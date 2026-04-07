# MongoDB 트랜잭션 학습 노트

> 작성일: 2026-04-07
> 컨텍스트: ReservationApplication 트랜잭션 보강 작업 중 학습한 내용

---

## 1. MongoDB 트랜잭션의 전제 조건

### Replica Set 또는 Sharded Cluster 필수

MongoDB는 **Standalone 모드에서 트랜잭션을 지원하지 않는다.**

```
Standalone (기본 설치)        → 트랜잭션 불가 (oplog 없음)
Replica Set                  → 트랜잭션 가능 (oplog 기반 롤백)
Sharded Cluster              → 크로스 샤드 트랜잭션 가능 (4.2+)
```

### oplog가 트랜잭션의 핵심

- oplog는 Replica Set의 변경 이력 로그 (Capped Collection)
- 트랜잭션 안의 쓰기는 oplog에 "보류" 상태로 기록
- 커밋 → oplog 항목 확정
- 롤백 → oplog 항목 취소

### 현재 StayOps 환경

```yaml
# docker-compose.yml
mongodb:
  command: ["--replSet", "rs0"]    # 단일 노드 Replica Set
```

단일 노드 Replica Set이라도 oplog가 생성되므로 트랜잭션이 동작한다.

---

## 2. RDB와의 차이

### RDB는 모든 SQL이 자동 트랜잭션

```
MySQL/PostgreSQL의 기본 동작 (auto-commit = ON):
  각 SQL 실행 시 → BEGIN/SQL/COMMIT 자동 처리
  → 단일 SQL도 트랜잭션 안에서 실행됨
```

### MongoDB는 명시적 session 기반

```
MongoDB의 트랜잭션:
  startSession() → startTransaction() → 작업들 → commitTransaction()
  → 명시적으로 session을 시작하지 않으면 트랜잭션 없음
  → 단일 문서 쓰기는 트랜잭션 없이 자체적으로 원자적
```

### 단일 쓰기에서의 차이

| 환경 | 단일 쓰기 + @Transactional |
|---|---|
| RDB (JPA) | 오버헤드 거의 없음 (이미 트랜잭션 안) |
| MongoDB | session 생성/관리 오버헤드 발생 |

---

## 3. @Transactional이 필요한 경우

### 판단 기준

```
쓰기 작업 1건  → @Transactional 불필요 (단일 문서 쓰기는 자체 원자적)
쓰기 작업 2건+ → @Transactional 필요 (전부 성공/실패 보장)
```

### 단일 문서 쓰기의 원자성

MongoDB는 **단일 문서에 대한 쓰기를 자체적으로 원자적으로 보장**한다:

```
db.reservations.updateOne({_id: "rsv-1"}, {$set: {...}})
  → WiredTiger 엔진이 문서에 잠금
  → 메모리 변경 + WAL 기록
  → 잠금 해제
  → 중간 상태 없음 (전부 성공 or 전부 실패)
```

따라서 단일 쓰기에는 트랜잭션이 추가 가치를 제공하지 않는다.

### 여러 쓰기에서의 필요성

```
@Transactional 없이:
  reserve() ✅  → 즉시 커밋
  reserve() ✅  → 즉시 커밋
  save()    ❌  → 실패
  결과: 재고는 빠졌는데 예약은 없음 (불일치)

@Transactional 있으면:
  reserve() ✅  → 보류
  reserve() ✅  → 보류
  save()    ❌  → 예외 발생
  자동 롤백 → 모두 취소 (정합성 유지)
```

---

## 4. @Transactional vs @TransactionalEventListener

### 역할이 완전히 다름

```
@Transactional:
  메서드 안의 작업들을 하나로 묶음 (트랜잭션 경계 정의)

@TransactionalEventListener:
  트랜잭션이 커밋된 후에 이벤트 처리
```

### 차이가 중요한 이유

```kotlin
// 일반 @EventListener
@EventListener
fun onReservationCreated(event: ReservationCreated) {
    // 트랜잭션 커밋 전에 실행
    // 여기서 실패 시 → 원래 트랜잭션도 롤백됨
}

// @TransactionalEventListener
@TransactionalEventListener
fun onReservationCreated(event: ReservationCreated) {
    // 트랜잭션 커밋 후에 실행
    // 여기서 실패해도 → 원래 트랜잭션 영향 없음
}
```

---

## 5. 트랜잭션 전파 유형 (Propagation)

### 7가지 전파 유형

| 유형 | 동작 |
|---|---|
| **REQUIRED** (기본값) | 기존 트랜잭션 있으면 참여, 없으면 새로 생성 |
| REQUIRES_NEW | 항상 새 트랜잭션 생성 (기존은 일시 중단) |
| SUPPORTS | 기존 트랜잭션 있으면 참여, 없으면 트랜잭션 없이 실행 |
| MANDATORY | 기존 트랜잭션 필수 (없으면 예외) |
| NESTED | 세이브포인트 생성 (부분 롤백 가능) |
| NOT_SUPPORTED | 트랜잭션 없이 실행 (기존 일시 중단) |
| NEVER | 트랜잭션 있으면 예외 |

### 테스트 롤백과의 관계

```
@SpringBootTest @Transactional 환경에서:

REQUIRED      → 테스트 트랜잭션에 참여 → 함께 롤백 ✅
REQUIRES_NEW  → 별도 트랜잭션 → 즉시 커밋 → 롤백 안 됨 ❌
NOT_SUPPORTED → 트랜잭션 밖에서 실행 → 롤백 안 됨 ❌
```

---

## 6. 테스트 코드의 트랜잭션 롤백

### RDB에서는 자동 롤백 가능

```kotlin
@SpringBootTest
@Transactional   // 테스트 종료 후 자동 롤백
class SomeTest {
    @Test
    fun test() {
        repository.save(...)  // 저장
        // 테스트 종료 → 자동 롤백 → DB에 데이터 안 남음
    }
}
```

### MongoDB에서는 안정적이지 않음

GitHub Issue #5019: MongoTemplate이 session을 드라이버에 전파하지 않는 경우가 있어
롤백이 사실상 no-op이 될 수 있음.

### 추가 문제: listCollections 충돌

테스트 클래스에 `@Transactional`을 명시하면, `@BeforeEach`의 데이터 정리 작업이
트랜잭션 안에서 실행되어 다음 에러 발생:

```
Cannot run 'listCollections' in a multi-document transaction.
```

MongoDB 트랜잭션 안에서는 DDL 작업(listCollections, createCollection 등)이 불가능하다.

### MongoDB E2E 테스트의 권장 패턴

테스트 클래스에 `@Transactional`을 사용하지 않고, `@BeforeEach`에서 수동 삭제:

```kotlin
@BeforeEach
fun setUp() {
    mongoTemplate.collectionNames.forEach { name ->
        mongoTemplate.getCollection(name).deleteMany(org.bson.Document())
    }
    // 테스트 데이터 새로 생성
}
```

현재 StayOps의 모든 E2E 테스트가 이 패턴을 따르고 있다.

---

## 7. 트랜잭션 롤백이 작동하지 않는 경우 정리

| 경우 | 롤백 | 이유 |
|---|---|---|
| RDB + @Transactional | ✅ | JDBC session이 트랜잭션에 바인딩 |
| MongoDB + @Transactional (프로덕션 코드) | ✅ | Replica Set + MongoTransactionManager |
| **MongoDB + @Transactional (테스트 클래스)** | **❌ 불안정** | listCollections 충돌, session 전파 문제 |
| 별도 스레드 (@Async) | ❌ | 다른 트랜잭션에서 실행 |
| REQUIRES_NEW 전파 | ❌ | 독립 트랜잭션으로 즉시 커밋 |
| Java Checked Exception | ❌ | 기본 설정에서 롤백 안 함 (Kotlin은 해당 없음) |
| TransactionManager 미등록 | ❌ | 트랜잭션 자체가 시작 안 됨 |

---

## 8. 단일 쓰기에 @Transactional을 명시하면 발생하는 일

### MongoDB Replica Set 환경 (StayOps)
- 동작은 정상 (에러 없음)
- session 생성/관리 오버헤드 발생 (네트워크 왕복 1회 → 3~4회)
- WriteConflict 가능성 증가
- 약 30% 성능 저하 추정

### Standalone MongoDB 환경
- 런타임 에러 발생 (Standalone에서 트랜잭션 불가)

### 권장
- 단일 쓰기 → @Transactional 사용 안 함
- 여러 쓰기 → @Transactional 명시

---

## 9. @Transactional(readOnly = true)의 효과

### RDB (JPA)에서는 큰 효과
- Dirty Checking 비활성화 → 메모리/CPU 절약
- 불필요한 UPDATE 방지
- Flush 비용 절감
- Replica 라우팅 가능

### MongoDB에서는 실질적 효과 거의 없음
- Spring Data MongoDB는 dirty checking이 없음 (명시적 save() 필요)
- 명시 안 하는 것이 권장

### 결론
```
RDB:     모든 조회 메서드에 readOnly 명시 (성능 이점)
MongoDB: 조회 메서드에 어노테이션 없음 (오버헤드 회피)
```

---

## 10. 현재 StayOps의 적용 결과

### @Transactional 적용 메서드 (여러 쓰기)

| 클래스 | 메서드 | 쓰기 작업 |
|---|---|---|
| BookingApplication | createBooking | 재고 + 예약 + 결제 |
| BookingApplication | confirmPayment | 결제 + 예약 |
| BookingApplication | cancelBooking | 결제 + 예약 + 재고 |
| ReservationApplication | createReservation | 재고 N일 + 예약 |
| ReservationApplication | cancelReservation | 예약 + 재고 N일 |
| ReservationApplication | checkInReservation | 객실 + 예약 |
| ReservationApplication | checkOutReservation | 예약 + 객실 |

### @Transactional 미적용 (단일 쓰기)

`PropertyApplication.createProperty`, `RoomTypeApplication.deleteRoomType`,
`GuestApplication.updateGuest` 등 단일 쓰기 메서드는 어노테이션 없이 유지.

---

## 참고 자료

- [Transaction Management — Spring Framework](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html)
- [@Rollback — Spring Framework](https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-rollback.html)
- [MongoTransactionManager API](https://docs.spring.io/spring-data/mongodb/docs/current/api/org/springframework/data/mongodb/MongoTransactionManager.html)
- [Spring Data MongoDB Issue #5019: Session 전파](https://github.com/spring-projects/spring-data-mongodb/issues/5019)
- [Spring Boot Issue #20182: TransactionAutoConfiguration](https://github.com/spring-projects/spring-boot/issues/20182)
