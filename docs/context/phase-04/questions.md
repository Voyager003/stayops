# Phase 4 학습 질문 모음

## Q1. 엘비스 연산자(`?:`)란?

Kotlin의 null 안전 연산자. 좌변이 null이 아니면 좌변을 반환하고, null이면 우변을 반환한다.

```kotlin
val result = expression ?: defaultValue
// expression이 null이 아니면 expression, null이면 defaultValue
```

이 프로젝트 예시:
```kotlin
mongo.findByPropertyIdAndRoomTypeIdAndDate(...)?.toDomain()
// null이면 null 반환, null이 아니면 toDomain() 호출
```

---

## Q2. Phase 4-2 테스트 실패 원인 및 진단 과정

### 발생한 3가지 실패

| 테스트 | 오류 |
|--------|------|
| `reserve 후 저장하면 변경된 상태가 유지된다` | DuplicateKeyException |
| `다른 날짜이면 같은 propertyId, roomTypeId로 저장할 수 있다` | AssertionError (size: 0) |
| `날짜 범위 내의 재고 목록을 반환한다` | AssertionError (size: 0) |

### 문제 1: DuplicateKeyException

**원인:** 첫 번째 `save()` 반환값을 버리고 원본 객체(`version = null`)로 두 번째 save 시도 → `isNew = true` → INSERT 재시도 → `_id` 충돌

**진단:** XML 테스트 리포트에서 `_id_ dup key: { _id: "inv-1" }` 확인 (복합 인덱스 충돌이 아닌 `_id` 충돌)

**수정:** 첫 번째 `save()` 반환값을 캡처해서 사용

```kotlin
// Before
val inventory = newInventory()
inventoryRepository.save(inventory)        // 반환값 버림
val reserved = inventory.reserve()         // version = null 유지

// After
val inventory = inventoryRepository.save(newInventory())  // version = 0 받아옴
val reserved = inventory.reserve()         // version = 0 복사
```

### 문제 2 & 3: DateBetween 쿼리 0개 반환

**원인:** Spring Data MongoDB 5.x에서 `LocalDate`를 `{year, month, dayOfMonth}` BSON 서브도큐먼트로 저장하는데, 서브도큐먼트에 대한 `$gte`/`$lte` 비교가 정상 동작하지 않음

**진단:** 테스트 내부에 임시 debug 출력 추가

```kotlin
val allDocs = mongoDataRepository.findAll()
println("DEBUG stored docs: ${allDocs.map { it.id to it.date }}")
// → [(inv-1, 2026-03-12), (inv-2, 2026-03-13)]  ← 문서는 있음

val result = ...findByPropertyIdAndRoomTypeIdAndDateBetween(...)
println("DEBUG result size: ${result.size}")
// → 0  ← Between 쿼리만 실패
```

문서는 정상 저장됐지만, Spring Data 파생 `Between` 쿼리가 서브도큐먼트에 대해 동작하지 않음을 확인

**수정:**
1. `RoomInventoryDocument.date` 타입을 `LocalDate` → `String` ("YYYY-MM-DD")으로 변경
2. Spring Data 파생 쿼리 대신 `@Query` 명시적 지정

```kotlin
@Query("{ 'propertyId': ?0, 'roomTypeId': ?1, 'date': { '\$gte': ?2, '\$lte': ?3 } }")
fun findByPropertyIdAndRoomTypeIdAndDateBetween(...): List<RoomInventoryDocument>
```

ISO 날짜 문자열은 사전순 = 날짜순이므로 문자열 범위 비교가 올바르게 동작함

---

## Q3. MongoDB 인덱스의 역할

### 1. 검색 속도 향상
- 인덱스 없음: Collection Full Scan → O(n)
- 인덱스 있음: B-Tree 탐색 → O(log n)

### 2. 고유성 보장 (Unique Index)
```kotlin
@CompoundIndex(def = "{'propertyId': 1, 'roomTypeId': 1, 'date': 1}", unique = true)
```
같은 `(propertyId, roomTypeId, date)` 조합의 중복 저장을 DB 레벨에서 차단

### 3. 복합 인덱스 Prefix Rule
왼쪽 필드부터 순서대로 사용해야 인덱스를 탐
```
(propertyId, roomTypeId, date) 인덱스 기준:
✓ propertyId만으로 조회
✓ propertyId + roomTypeId 조회
✓ propertyId + roomTypeId + date 조회
✗ roomTypeId만으로 조회 (첫 번째 필드 없음)
```

### 4. 필드 순서 값 (1 / -1)
- `1`: 오름차순 인덱스
- `-1`: 내림차순 인덱스

---

## Q4. 현재 락(Lock) 구현 상태

### 구현됨: 낙관적 락 (Optimistic Lock)

`@Version val version: Long?` 필드를 통해 MongoDB 낙관적 락 구현

동작: 저장 시 version이 일치하지 않으면 `OptimisticLockingFailureException` 발생 (충돌 감지)

### 미구현: 충돌 해결 로직

낙관적 락은 충돌을 "감지"만 함. 실제 충돌 시 어떻게 처리할지는 Phase 4-3 Application Service에서 결정 예정:
- 예외를 그대로 던지기 (클라이언트에 재시도 요청)
- N회 자동 재시도 로직
- Redis 분산 락 (비관적 접근)

---

## Q5. RoomInventory를 Redis에 저장하는 이유

- 재고 조회는 예약 흐름에서 가장 빈번하게 발생하는 질의다
- 특정 날짜의 재고는 조회 횟수 대비 변경 횟수가 적어 캐시 히트율이 높다
- 변경 시 EVICT 패턴으로 stale 방지 + DB 낙관적 락으로 정합성 보장
- TTL 5분 — 재고처럼 자주 변하는 데이터에 적합한 짧은 만료 시간