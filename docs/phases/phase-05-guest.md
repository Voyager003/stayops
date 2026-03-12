# Phase 5: Guest 도메인

고객 관리: 등급(Tier), 방문/지출 이력, 최근 방문 추적.

---

## 기능적 요구사항

- **고객 자동 등록**: 예약 생성 시 전화번호 기반으로 기존 고객을 조회하고, 없으면 자동 생성해야 한다
- **등급 자동 승급**: 체크아웃 시 방문 기록이 갱신되고, 방문 횟수·누적 지출에 따라 등급이 자동 재산정되어야 한다
- **방문 이력 추적**: 총 방문 횟수, 누적 지출, 최근 방문일, 평균 숙박일수를 추적해야 한다
- **등급별 조회**: 특정 등급(VIP, GOLD 등)의 고객 목록을 조회할 수 있어야 한다

## 기술적 도전 과제

| 과제 | 설명 | 해결 전략 |
|------|------|----------|
| 등급 산정 복합 기준 | 방문 횟수 기준과 누적 지출 기준이 OR 조건으로 결합됨 (VIP: 20회+ OR 500만원+) | `calculateTier()`에서 두 기준 모두 평가, 높은 등급 반환 |
| 크로스 BC 이벤트 | 체크아웃(Reservation BC) → 방문 기록 갱신(Guest BC) 연동 필요 | Spring `@EventListener`로 `ReservationCheckedOut` 이벤트 수신 (Phase 8에서 구현) |
| 전화번호 유니크 | 같은 숙소 내 동일 전화번호 중복 등록 방지 | MongoDB 복합 유니크 인덱스 `{ propertyId: 1, phone: 1 }` |

---

## Sub-steps

### Phase 5-1: Guest 도메인 모델 + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/guest/domain/model/
├── Guest.kt
├── GuestTier.kt         # enum: NEW, BRONZE, SILVER, GOLD, VIP
└── VisitSummary.kt      # VO

src/test/kotlin/com/stayops/guest/domain/model/
└── GuestTest.kt
```

**도메인 모델:**
```kotlin
data class Guest(
    val id: String,
    val propertyId: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val tier: GuestTier = GuestTier.NEW,
    val memo: String? = null,
    val visitSummary: VisitSummary = VisitSummary.EMPTY,
    val version: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

**GuestTier 승급 기준:**
- NEW: 0회
- BRONZE: 2+ 방문
- SILVER: 5+ 방문
- GOLD: 10+ 방문
- VIP: 20+ 방문 또는 500만원+ 누적 지출

**VisitSummary (VO):**
```kotlin
data class VisitSummary(
    val totalVisits: Int = 0,
    val totalSpend: Money = Money.ZERO,
    val lastVisitDate: LocalDate? = null,
    val averageStayNights: Double = 0.0
) {
    fun recordVisit(spend: Money, stayNights: Int, visitDate: LocalDate): VisitSummary
}
```

**비즈니스 규칙:**
- `recordVisit()`: visitSummary 갱신 + tier 자동 재계산
- `calculateTier()`: visitSummary 기반 등급 결정

**TDD 순서:**
1. RED: Guest 생성 테스트
2. GREEN: Guest 구현
3. RED: VisitSummary.recordVisit() 테스트
4. GREEN: VisitSummary 구현
5. RED: GuestTier 승급 규칙 테스트
6. GREEN: tier 계산 로직 구현
7. REFACTOR

---

### Phase 5-2: Repository + MongoDB 구현 + 통합 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/guest/domain/repository/
└── GuestRepository.kt

src/main/kotlin/com/stayops/guest/infrastructure/persistence/
├── GuestDocument.kt
└── MongoGuestRepository.kt

src/test/kotlin/com/stayops/guest/infrastructure/persistence/
└── MongoGuestRepositoryTest.kt
```

**Repository 인터페이스:**
```kotlin
interface GuestRepository {
    fun save(guest: Guest): Guest
    fun findById(id: String): Guest?
    fun findByPropertyIdAndPhone(propertyId: String, phone: String): Guest?
    fun findByPropertyId(propertyId: String): List<Guest>
    fun findByPropertyIdAndTier(propertyId: String, tier: GuestTier): List<Guest>
    fun findByPropertyIdAndNameContaining(propertyId: String, name: String): List<Guest>
}
```

**MongoDB 인덱스:**
- `{ propertyId: 1, phone: 1 }` (unique)

---

### Phase 5-3: GuestService + API + E2E 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/guest/application/service/
└── GuestService.kt

src/main/kotlin/com/stayops/guest/api/
├── GuestController.kt
└── dto/
    ├── GuestResponse.kt
    ├── UpdateGuestRequest.kt
    └── GuestHistoryResponse.kt

src/test/kotlin/com/stayops/guest/application/service/
└── GuestServiceTest.kt

src/test/kotlin/com/stayops/guest/api/
└── GuestControllerTest.kt
```

**API 엔드포인트:**
```
GET    /api/v1/properties/{pid}/guests                   (필터: tier, name)
GET    /api/v1/properties/{pid}/guests/{id}
GET    /api/v1/properties/{pid}/guests/{id}/history       (방문 이력 = 예약 목록)
PUT    /api/v1/properties/{pid}/guests/{id}
```

**참고:** Guest는 직접 생성 API가 없음. 예약 생성 시 자동으로 생성/연결됨 (Phase 7).

---

## 검증 기준

- [ ] Guest 도메인 단위 테스트 통과
- [ ] GuestTier 승급 규칙 테스트 통과
- [ ] VisitSummary 갱신 테스트 통과
- [ ] MongoDB 통합 테스트 통과 (unique 인덱스 포함)
- [ ] Guest API E2E 테스트 통과
- [ ] `./gradlew test` 전체 통과
