# Phase 9: Settlement (정산)

채널별 수수료/실 정산액 집계. MongoDB aggregation 활용.

---

## 기능적 요구사항

- **기간별 정산 조회**: 시작일~종료일 범위의 총 매출·총 수수료·순 정산액을 집계하여 조회할 수 있어야 한다
- **채널별 정산 분리**: 채널(자사 숙소 예매 사이트, Agoda, Booking.com 등)별로 예약 건수·매출·수수료·순정산을 분리하여 확인할 수 있어야 한다
- **정산 대상 필터링**: CHECKED_OUT과 NO_SHOW 상태의 예약만 정산에 포함해야 한다 (취소·미확정 제외)

## 기술적 도전 과제

| 과제 | 설명 | 해결 전략 |
|------|------|----------|
| 대량 데이터 집계 성능 | 수천 건의 예약을 기간·채널별로 실시간 집계해야 함 | MongoDB Aggregation Pipeline ($match → $group → $sum) — 애플리케이션이 아닌 DB 레벨에서 집계 |
| 별도 도메인 모델 불필요 | 정산은 Reservation 데이터의 읽기 전용 뷰이므로 별도 Entity 불요 | DTO 기반 쿼리 서비스(`SettlementQueryService`) — 도메인 모델 없이 Aggregation 결과를 DTO로 직접 매핑 |
| 금액 정밀도 | BigDecimal 기반 Money가 MongoDB에서 정확히 집계되어야 함 | Aggregation에서 `pricing.totalAmount`, `pricing.commissionAmount` 필드를 직접 $sum |

---

## Sub-steps

### Phase 9-1: SettlementQueryService + MongoDB aggregation 구현

**생성할 파일:**
```
src/main/kotlin/com/stayops/settlement/application/service/
└── SettlementQueryService.kt

src/main/kotlin/com/stayops/settlement/infrastructure/persistence/
└── MongoSettlementQueryRepository.kt

src/main/kotlin/com/stayops/settlement/application/dto/
├── SettlementSummary.kt
└── ChannelSettlement.kt

src/test/kotlin/com/stayops/settlement/application/service/
└── SettlementQueryServiceTest.kt

src/test/kotlin/com/stayops/settlement/infrastructure/persistence/
└── MongoSettlementQueryRepositoryTest.kt
```

**SettlementSummary:**
```kotlin
data class SettlementSummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalReservations: Int,
    val totalRevenue: Money,
    val totalCommission: Money,
    val netSettlement: Money,
    val byChannel: List<ChannelSettlement>
)

data class ChannelSettlement(
    val channelCode: String,
    val reservationCount: Int,
    val totalRevenue: Money,
    val totalCommission: Money,
    val netSettlement: Money
)
```

**MongoDB Aggregation:**
```javascript
db.reservations.aggregate([
  { $match: {
      propertyId: "X",
      status: { $in: ["CHECKED_OUT", "NO_SHOW"] },
      "dateRange.checkOut": { $gte: startDate, $lte: endDate }
  }},
  { $group: {
      _id: "$channel.channelCode",
      reservationCount: { $sum: 1 },
      totalRevenue: { $sum: "$pricing.totalAmount.amount" },
      totalCommission: { $sum: "$pricing.commissionAmount.amount" },
      netSettlement: { $sum: "$pricing.netAmount.amount" }
  }}
])
```

**TDD 순서:**
1. RED: 기간별 정산 요약 테스트 (Testcontainers + 테스트 데이터)
2. GREEN: aggregation 구현
3. RED: 채널별 분류 테스트
4. GREEN: 채널별 group by 구현
5. REFACTOR

---

### Phase 9-2: Settlement API + E2E 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/settlement/api/
├── SettlementController.kt
└── dto/
    ├── SettlementSummaryResponse.kt
    └── ChannelSettlementResponse.kt

src/test/kotlin/com/stayops/settlement/api/
└── SettlementControllerTest.kt
```

**API 엔드포인트:**
```
GET    /api/v1/properties/{pid}/settlements               (params: startDate, endDate)
GET    /api/v1/properties/{pid}/settlements/by-channel     (params: startDate, endDate)
```

---

## 검증 기준

- [ ] MongoDB aggregation 통합 테스트 통과
- [ ] 기간별/채널별 정산 계산 정확성 확인
- [ ] Settlement API E2E 테스트 통과
- [ ] `./gradlew test` 전체 통과
