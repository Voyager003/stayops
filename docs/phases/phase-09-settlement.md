# Phase 9: Settlement (정산)

채널별 수수료/실 정산액 집계. MongoDB aggregation 활용.

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
