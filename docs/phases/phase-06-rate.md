# Phase 6: Rate 도메인

요금 관리: 시즌/요일/채널별 동적 요금제, 요금 결정 로직.

---

## Sub-steps

### Phase 6-1: RatePlan 도메인 모델 + RateResolver 도메인 서비스 + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/rate/domain/model/
├── RatePlan.kt
├── RatePlanType.kt      # enum: SEASONAL, WEEKDAY, CHANNEL_SPECIFIC, SPECIAL
├── RatePlanStatus.kt    # enum: ACTIVE, INACTIVE
└── DayOfWeekRate.kt     # VO

src/main/kotlin/com/stayops/rate/domain/service/
└── RateResolver.kt      # 도메인 서비스

src/test/kotlin/com/stayops/rate/domain/model/
└── RatePlanTest.kt

src/test/kotlin/com/stayops/rate/domain/service/
└── RateResolverTest.kt
```

**RatePlan 도메인 모델:**
```kotlin
data class RatePlan(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val name: String,
    val type: RatePlanType,
    val dateRange: DateRange? = null,       // null이면 상시 적용
    val dayOfWeekRules: List<DayOfWeekRate>? = null,
    val channelCode: String? = null,           // 채널 특화 요금 (Channel BC 코드 참조)
    val price: Money,
    val priority: Int,
    val status: RatePlanStatus,
    val version: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

**DayOfWeekRate (VO):**
```kotlin
data class DayOfWeekRate(
    val daysOfWeek: Set<DayOfWeek>,
    val price: Money
)
```

**RateResolver (도메인 서비스):**
```kotlin
class RateResolver {
    fun resolve(
        ratePlans: List<RatePlan>,
        roomTypeBasePrice: Money,
        date: LocalDate,
        channelCode: String?
    ): Money

    fun resolveForDateRange(
        ratePlans: List<RatePlan>,
        roomTypeBasePrice: Money,
        dateRange: DateRange,
        channelCode: String?
    ): Money  // 각 날짜별 요금 합산
}
```

**요금 결정 우선순위:**
1. SPECIAL (priority=100) — 특정 날짜 특가
2. CHANNEL_SPECIFIC (priority=50) — 채널별 요금
3. SEASONAL (priority=30) — 시즌 요금
4. WEEKDAY (priority=20) — 요일별 요금
5. RoomType.basePrice (fallback)

**TDD 순서:**
1. RED: RatePlan 생성/상태 전이 테스트
2. GREEN: RatePlan 구현
3. RED: RateResolver — basePrice fallback 테스트
4. GREEN: fallback 구현
5. RED: RateResolver — SEASONAL 우선 적용 테스트
6. GREEN: priority 기반 해결 구현
7. RED: RateResolver — 요일별 요금 테스트
8. GREEN: DayOfWeekRate 적용 구현
9. RED: RateResolver — 복수 날짜 합산 테스트
10. GREEN: resolveForDateRange 구현
11. REFACTOR

---

### Phase 6-2: Repository + MongoDB 구현 + 통합 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/rate/domain/repository/
└── RatePlanRepository.kt

src/main/kotlin/com/stayops/rate/infrastructure/persistence/
├── RatePlanDocument.kt
└── MongoRatePlanRepository.kt

src/test/kotlin/com/stayops/rate/infrastructure/persistence/
└── MongoRatePlanRepositoryTest.kt
```

**Repository 인터페이스:**
```kotlin
interface RatePlanRepository {
    fun save(ratePlan: RatePlan): RatePlan
    fun findById(id: String): RatePlan?
    fun findByPropertyIdAndRoomTypeIdAndStatus(
        propertyId: String, roomTypeId: String, status: RatePlanStatus
    ): List<RatePlan>
    fun findByPropertyId(propertyId: String): List<RatePlan>
    fun deleteById(id: String)
}
```

**MongoDB 인덱스:**
- `{ propertyId: 1, roomTypeId: 1, status: 1, priority: -1 }`

---

### Phase 6-3: RatePlanService + API + E2E 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/rate/application/service/
└── RatePlanService.kt

src/main/kotlin/com/stayops/rate/api/
├── RatePlanController.kt
└── dto/
    ├── CreateRatePlanRequest.kt
    ├── UpdateRatePlanRequest.kt
    ├── RatePlanResponse.kt
    └── RatePreviewResponse.kt

src/test/kotlin/com/stayops/rate/application/service/
└── RatePlanServiceTest.kt

src/test/kotlin/com/stayops/rate/api/
└── RatePlanControllerTest.kt
```

**API 엔드포인트:**
```
POST   /api/v1/properties/{pid}/rate-plans
GET    /api/v1/properties/{pid}/rate-plans
PUT    /api/v1/properties/{pid}/rate-plans/{id}
DELETE /api/v1/properties/{pid}/rate-plans/{id}
GET    /api/v1/properties/{pid}/rates/preview   (params: roomTypeId, startDate, endDate, channel)
```

**rates/preview API:**
- 특정 조건에서 각 날짜별 적용 요금 미리보기
- 어떤 RatePlan이 적용되었는지 표시

---

## 검증 기준

- [ ] RatePlan 도메인 단위 테스트 통과
- [ ] RateResolver 도메인 서비스 단위 테스트 통과 (모든 우선순위 시나리오)
- [ ] MongoDB 통합 테스트 통과
- [ ] Rate Plans API + rates/preview E2E 테스트 통과
- [ ] `./gradlew test` 전체 통과
