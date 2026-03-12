# Phase 8: Reservation 도메인 (핵심)

예약 생성, 수정, 취소, 체크인/체크아웃. 모든 Context를 연결하는 핵심 도메인.

---

## 기능적 요구사항

- **예약 생성**: 객실타입·날짜·채널을 지정하여 예약을 생성하고, 재고 차감·요금 산정·고객 등록이 원자적으로 처리되어야 한다
- **예약 라이프사이클**: PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT 상태 흐름과 취소(CANCELLED), 노쇼(NO_SHOW) 처리를 지원해야 한다
- **체크인 시 객실 배정**: 체크인 시 실제 객실(Room)을 배정하고, 해당 Room 상태를 OCCUPIED로 전환해야 한다
- **체크아웃 후 연동**: 체크아웃 시 객실 상태를 CLEANING으로 전환하고, 고객 방문 이력을 갱신하며, 채널 동기화를 트리거해야 한다
- **채널별 수수료 추적**: FineStay(수수료 0%) vs OTA(수수료율 적용) 예약을 구분하여 정산 데이터를 추적해야 한다

## 기술적 도전 과제

| 과제 | 설명 | 해결 전략 |
|------|------|----------|
| 다중 모듈 오케스트레이션 | 예약 1건 생성에 Room·Rate·Inventory·Guest·Channel 5개 모듈이 관여 | Application Service에서 순서대로 조율, 실패 시 보상 트랜잭션 |
| 동시 예약 Race Condition | 마지막 1실 동시 예약 시 1건만 성공해야 함 | Inventory의 낙관적 락 — version 충돌 시 409 Conflict 반환 |
| 도메인 이벤트 전파 | 체크아웃 → Guest 등급 갱신, 예약 생성 → Channel 동기화 | Spring `ApplicationEventPublisher` + `@EventListener` |
| 예약 가격 불변성 | 예약 후 요금제가 변경되어도 예약 시점의 가격이 보존되어야 함 | `ReservationPricing`에 스냅샷 저장 — 생성 후 변경 불가 |
| 10-thread 동시성 테스트 | 동시 예약 시나리오를 자동화 테스트로 검증해야 함 | `CountDownLatch` + `ExecutorService`로 10개 스레드 동시 요청 |

---

## Sub-steps

### Phase 8-1: Reservation 도메인 모델 + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/reservation/domain/model/
├── Reservation.kt
├── ReservationStatus.kt      # enum: PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW
├── BookingChannel.kt         # VO (channelCode 기반)
├── ReservationPricing.kt     # VO
└── GuestInfo.kt              # VO (예약 시점 스냅샷)

src/main/kotlin/com/stayops/reservation/domain/event/
├── ReservationCreated.kt
├── ReservationCheckedOut.kt
└── ReservationCancelled.kt

src/test/kotlin/com/stayops/reservation/domain/model/
└── ReservationTest.kt
```

**Reservation 도메인 모델:**
```kotlin
data class Reservation(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val roomId: String? = null,
    val guestId: String,
    val guestInfo: GuestInfo,
    val dateRange: DateRange,
    val nightCount: Int,
    val numberOfGuests: Int,
    val status: ReservationStatus,
    val channel: BookingChannel,
    val pricing: ReservationPricing,
    val memo: String? = null,
    val version: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

**BookingChannel (VO):**
```kotlin
data class BookingChannel(
    val channelCode: String,                // Channel 모듈의 채널 코드 (e.g. "FINESTAY", "AGODA", "AIRBNB")
    val externalReservationId: String? = null,
    val commissionRate: BigDecimal           // 0.15 = 15%
)
```

> `ChannelSource` enum 대신 `channelCode: String`을 사용한다. 예약 시점의 채널 코드를 문자열로 보존하여 채널 정보 변경과 무관하게 예약 데이터의 일관성을 유지한다.

**ReservationPricing (VO):**
```kotlin
data class ReservationPricing(
    val roomRate: Money,
    val additionalCharges: Money = Money.ZERO,
    val totalAmount: Money,
    val commissionAmount: Money,
    val netAmount: Money
) {
    companion object {
        fun calculate(roomRate: Money, additionalCharges: Money, commissionRate: BigDecimal): ReservationPricing
    }
}
```

**상태 전이 규칙:**
```
PENDING → CONFIRMED     (confirm)
CONFIRMED → CHECKED_IN  (checkIn, roomId 필수)
CHECKED_IN → CHECKED_OUT (checkOut)
CONFIRMED → CANCELLED   (cancel)
CONFIRMED → NO_SHOW     (noShow)
```

**비즈니스 규칙:**
- FINESTAY(DIRECT) 채널: commissionRate = 0
- 체크인 시 roomId 배정 필수
- 취소는 CONFIRMED 상태에서만 가능
- 가격 변경 불가 (스냅샷)

**TDD 순서:**
1. RED: Reservation 생성 테스트
2. GREEN: Reservation 팩토리 구현
3. RED: 상태 전이 성공/실패 테스트 (모든 경로)
4. GREEN: 상태 전이 메서드 구현
5. RED: ReservationPricing.calculate() 테스트
6. GREEN: Pricing 계산 구현
7. RED: BookingChannel 불변식 테스트
8. GREEN: BookingChannel 구현
9. REFACTOR

---

### Phase 8-2: Repository + MongoDB 구현 + 통합 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/reservation/domain/repository/
└── ReservationRepository.kt

src/main/kotlin/com/stayops/reservation/infrastructure/persistence/
├── ReservationDocument.kt
└── MongoReservationRepository.kt

src/test/kotlin/com/stayops/reservation/infrastructure/persistence/
└── MongoReservationRepositoryTest.kt
```

**Repository 인터페이스:**
```kotlin
interface ReservationRepository {
    fun save(reservation: Reservation): Reservation
    fun findById(id: String): Reservation?
    fun findByPropertyId(propertyId: String): List<Reservation>
    fun findByPropertyIdAndStatus(propertyId: String, status: ReservationStatus): List<Reservation>
    fun findByPropertyIdAndDateRange(propertyId: String, startDate: LocalDate, endDate: LocalDate): List<Reservation>
    fun findByPropertyIdAndGuestId(propertyId: String, guestId: String): List<Reservation>
    fun findByPropertyIdAndChannelCode(propertyId: String, channelCode: String): List<Reservation>
}
```

**MongoDB 인덱스:**
- `{ propertyId: 1, status: 1, "dateRange.checkIn": 1 }`
- `{ propertyId: 1, "channel.channelCode": 1 }`

---

### Phase 8-3: 예약 생성 플로우 + 단위 테스트

**수정/생성할 파일:**
```
src/main/kotlin/com/stayops/reservation/application/service/
└── ReservationService.kt

src/test/kotlin/com/stayops/reservation/application/service/
└── ReservationServiceTest.kt
```

**예약 생성 플로우:**
1. RoomType 존재 확인 (room 모듈)
2. Channel 유효성 확인 (channel 모듈 — channelCode로 조회)
3. RateResolver로 날짜별 요금 산출 (rate 모듈)
4. 날짜별 재고 확인 + 차감 (inventory 모듈, 트랜잭션)
5. Guest 조회/생성 (guest 모듈)
6. Reservation 생성 + 저장
7. `ReservationCreated` 이벤트 발행

**TDD 순서:**
1. RED: 정상 예약 생성 테스트 (모든 의존성 Mock)
2. GREEN: ReservationService.createReservation() 구현
3. RED: 재고 부족 시 예외 테스트
4. GREEN: 재고 검증 로직 추가
5. RED: RatePlan 적용 테스트
6. GREEN: RateResolver 연동
7. REFACTOR

---

### Phase 8-4: 취소/수정 플로우 + 단위 테스트

**수정할 파일:**
```
src/main/kotlin/com/stayops/reservation/application/service/ReservationService.kt
src/test/kotlin/com/stayops/reservation/application/service/ReservationServiceTest.kt
```

**취소 플로우:**
1. Reservation 상태 → CANCELLED
2. 재고 복원 (Inventory.release())
3. `ReservationCancelled` 이벤트 발행

**수정 플로우:**
- 날짜/인원 변경 시 재고 재계산
- 가격 재산출

---

### Phase 8-5: 체크인/체크아웃 플로우 + 단위 테스트

**수정할 파일:**
```
src/main/kotlin/com/stayops/reservation/application/service/ReservationService.kt
src/test/kotlin/com/stayops/reservation/application/service/ReservationServiceTest.kt
```

**체크인 플로우:**
1. CONFIRMED → CHECKED_IN
2. roomId 배정 (Room 상태 → OCCUPIED)

**체크아웃 플로우:**
1. CHECKED_IN → CHECKED_OUT
2. Room 상태 → CLEANING
3. `ReservationCheckedOut` 이벤트 발행

---

### Phase 8-6: 도메인 이벤트 연동

**생성/수정할 파일:**
```
src/main/kotlin/com/stayops/guest/application/service/
└── GuestEventHandler.kt    # ReservationCheckedOut 수신 → VisitSummary 갱신

src/test/kotlin/com/stayops/guest/application/service/
└── GuestEventHandlerTest.kt
```

**이벤트 흐름:**
- `ReservationCheckedOut` → GuestEventHandler → Guest.recordVisit() → 저장
- Spring `ApplicationEventPublisher` + `@EventListener` 사용

---

### Phase 8-7: API 컨트롤러 + E2E 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/reservation/api/
├── ReservationController.kt
└── dto/
    ├── CreateReservationRequest.kt
    ├── UpdateReservationRequest.kt
    ├── ReservationResponse.kt
    ├── CheckInRequest.kt
    └── NoShowRequest.kt

src/test/kotlin/com/stayops/reservation/api/
└── ReservationControllerTest.kt
```

**API 엔드포인트:**
```
POST   /api/v1/properties/{pid}/reservations
GET    /api/v1/properties/{pid}/reservations
GET    /api/v1/properties/{pid}/reservations/{id}
PUT    /api/v1/properties/{pid}/reservations/{id}
POST   /api/v1/properties/{pid}/reservations/{id}/cancel
POST   /api/v1/properties/{pid}/reservations/{id}/check-in
POST   /api/v1/properties/{pid}/reservations/{id}/check-out
POST   /api/v1/properties/{pid}/reservations/{id}/no-show
```

---

### Phase 8-8: 동시성 테스트

**생성할 파일:**
```
src/test/kotlin/com/stayops/reservation/
└── ConcurrentReservationTest.kt
```

**테스트 시나리오:**
- 마지막 1객실에 대해 10개 동시 예약 요청
- 정확히 1건만 성공, 나머지 9건은 실패 (409 Conflict 또는 재고 부족)
- `CountDownLatch` + `ExecutorService` 사용

---

### Phase 8-9: Channel 동기화 연동

**수정할 파일:**
```
src/main/kotlin/com/stayops/reservation/application/service/ReservationService.kt
src/main/kotlin/com/stayops/channel/application/service/ChannelSyncService.kt (Phase 7에서 생성)
src/test/kotlin/com/stayops/reservation/application/service/ReservationServiceTest.kt
```

**연동 내용:**
- `ReservationCreated` 이벤트 → ChannelSyncService: 다른 활성 OTA 채널로 재고 변경 동기화 (Outbox → VirtualChannelSyncAdapter)
- `ReservationCancelled` 이벤트 → ChannelSyncService: 재고 복원 동기화
- Webhook으로 수신된 OTA 예약 → Reservation 자동 생성

**TDD 순서:**
1. RED: 예약 생성 시 SyncTask 생성 확인 테스트 (모든 활성 OTA 채널 대상)
2. GREEN: 이벤트 핸들러에서 SyncTask 생성 구현
3. RED: OTA webhook 수신 → 예약 자동 생성 테스트
4. GREEN: webhook 핸들러 → ReservationService 연동 구현
5. REFACTOR

---

## 검증 기준

- [ ] Reservation 도메인 모든 상태 전이 단위 테스트 통과
- [ ] ReservationPricing 계산 테스트 통과
- [ ] 예약 생성/취소/체크인/체크아웃 서비스 단위 테스트 통과
- [ ] 도메인 이벤트 연동 테스트 통과
- [ ] 동시성 테스트: 1객실 동시 예약 → 1건만 성공
- [ ] Channel 동기화 연동 테스트 통과
- [ ] 전체 API E2E 테스트 통과
- [ ] `./gradlew test` 전체 통과
