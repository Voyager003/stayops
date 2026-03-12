# Phase 4: Inventory 도메인

날짜별 객실 재고 관리. 동시성 제어의 핵심 도메인.

---

## 기능적 요구사항

- **날짜별 재고 관리**: 객실타입별로 날짜 단위의 판매 가능 수량(총 수량 - 예약 수량 - 차단 수량)을 관리해야 한다
- **실시간 가용성 조회**: 특정 날짜 범위의 잔여 객실 수를 빠르게 조회할 수 있어야 한다
- **재고 차단/해제**: 특정 날짜의 객실을 차단(block)하거나 해제(unblock)할 수 있어야 한다
- **동시 예약 보호**: 마지막 1실에 대한 동시 예약 요청 시 정확히 1건만 성공하고 나머지는 실패해야 한다

## 기술적 도전 과제

| 과제 | 설명 | 해결 전략 |
|------|------|----------|
| 동시 예약 Race Condition | 10명이 마지막 1실을 동시에 예약하면 1명만 성공해야 함 | `version` 필드 기반 낙관적 락(Optimistic Locking) — 충돌 시 `ConflictException` |
| 재고 캐시 일관성 | Redis 캐시와 MongoDB 원본 데이터의 불일치 가능 | 예약/해제 시 해당 날짜 캐시 무효화, TTL 5분으로 자연 만료 |
| 날짜 범위 조회 성능 | 30일 가용성 조회 시 30건의 Document를 검색해야 함 | 복합 유니크 인덱스 `{ propertyId: 1, roomTypeId: 1, date: 1 }` |

---

## Sub-steps

### Phase 4-1: RoomInventory 도메인 모델 + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/inventory/domain/model/
└── RoomInventory.kt

src/test/kotlin/com/stayops/inventory/domain/model/
└── RoomInventoryTest.kt
```

**도메인 모델:**
```kotlin
data class RoomInventory(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val date: LocalDate,
    val totalCount: Int,
    val reservedCount: Int,
    val blockedCount: Int = 0,
    val version: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    val availableCount: Int
        get() = totalCount - reservedCount - blockedCount

    fun reserve(): RoomInventory { /* availableCount >= 1 검증 */ }
    fun release(): RoomInventory { /* reservedCount > 0 검증 */ }
    fun block(count: Int): RoomInventory { /* availableCount >= count 검증 */ }
    fun unblock(count: Int): RoomInventory { /* blockedCount >= count 검증 */ }
}
```

**비즈니스 규칙:**
- `reserve()`: availableCount >= 1 이어야 함. 아니면 예외
- `release()`: reservedCount > 0 이어야 함
- `block(count)`: availableCount >= count 이어야 함
- `availableCount = totalCount - reservedCount - blockedCount`

**TDD 순서:**
1. RED: reserve() 성공/실패 테스트
2. GREEN: reserve() 구현
3. RED: release() 성공/실패 테스트
4. GREEN: release() 구현
5. RED: block/unblock 테스트
6. GREEN: block/unblock 구현
7. REFACTOR

---

### Phase 4-2: Repository + MongoDB 구현 + 통합 테스트 (낙관적 락)

**생성할 파일:**
```
src/main/kotlin/com/stayops/inventory/domain/repository/
└── RoomInventoryRepository.kt

src/main/kotlin/com/stayops/inventory/infrastructure/persistence/
├── RoomInventoryDocument.kt
└── MongoRoomInventoryRepository.kt

src/test/kotlin/com/stayops/inventory/infrastructure/persistence/
└── MongoRoomInventoryRepositoryTest.kt
```

**Repository 인터페이스:**
```kotlin
interface RoomInventoryRepository {
    fun save(inventory: RoomInventory): RoomInventory
    fun findByPropertyIdAndRoomTypeIdAndDate(
        propertyId: String, roomTypeId: String, date: LocalDate
    ): RoomInventory?
    fun findByPropertyIdAndRoomTypeIdAndDateBetween(
        propertyId: String, roomTypeId: String, startDate: LocalDate, endDate: LocalDate
    ): List<RoomInventory>
}
```

**MongoDB 인덱스:**
- `{ propertyId: 1, roomTypeId: 1, date: 1 }` (unique)

**통합 테스트 포인트:**
- 낙관적 락 충돌 시 `OptimisticLockingFailureException` 발생 확인
- unique 인덱스 중복 방지 확인

---

### Phase 4-3: Redis 캐시 + Application 서비스 + API + E2E 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/inventory/infrastructure/cache/
└── RedisRoomInventoryCache.kt

src/main/kotlin/com/stayops/inventory/application/service/
└── RoomInventoryService.kt

src/main/kotlin/com/stayops/inventory/api/
├── RoomInventoryController.kt
└── dto/
    ├── AvailabilityResponse.kt
    ├── UpdateInventoryRequest.kt
    └── InitializeInventoryRequest.kt

src/test/kotlin/com/stayops/inventory/application/service/
└── RoomInventoryServiceTest.kt

src/test/kotlin/com/stayops/inventory/api/
└── RoomInventoryControllerTest.kt
```

**Redis 캐시:**
- Key: `inventory:{propertyId}:{roomTypeId}:{date}`
- TTL: 5분
- 예약/취소 시 해당 날짜 캐시 즉시 삭제

**API 엔드포인트:**
```
GET    /api/v1/properties/{pid}/availability              (params: roomTypeId, startDate, endDate)
PUT    /api/v1/properties/{pid}/inventory/{roomTypeId}/{date}
POST   /api/v1/properties/{pid}/inventory/initialize
```

---

## 검증 기준

- [ ] RoomInventory 도메인 단위 테스트 통과
- [ ] 낙관적 락 통합 테스트 통과
- [ ] Redis 캐시 통합 테스트 통과
- [ ] Availability API E2E 테스트 통과
- [ ] `./gradlew test` 전체 통과
