# Phase 2: Property 도메인

숙소(Property) 관리. 모든 데이터의 최상위 스코프(테넌트 경계).

---

## 기능적 요구사항

- **멀티 숙소 관리**: 하나의 계정으로 펜션·호텔·리조트 등 여러 숙소를 등록하고 개별 관리할 수 있어야 한다
- **숙소 상태 관리**: 운영 중(ACTIVE) / 비활성(INACTIVE) / 정지(SUSPENDED) 상태를 전환할 수 있어야 한다
- **예약 가능 판단**: ACTIVE 상태인 숙소만 예약을 받을 수 있어야 한다
- **테넌트 격리**: 하위 모든 도메인(Room, Inventory, Rate, Reservation 등)이 `propertyId`로 스코핑되어야 한다

## 기술적 도전 과제

| 과제 | 설명 | 해결 전략 |
|------|------|----------|
| 테넌트 격리 | 모든 쿼리에 propertyId 조건이 누락되면 데이터 유출 발생 | 모든 Repository 메서드에 propertyId 파라미터 필수화 |
| 상태 전이 규칙 | 허용되지 않는 상태 전이(e.g., INACTIVE → SUSPENDED)를 방지해야 함 | 도메인 모델 내부에서 상태 전이 검증, 위반 시 예외 |
| FINESTAY 자동 생성 | 숙소 등록 시 자사 채널(FINESTAY)이 자동으로 함께 생성되어야 함 | Phase 7에서 PropertyCreated 이벤트 또는 Application Service 연동 |

---

## Sub-steps

### Phase 2-1: Property 도메인 모델 + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/property/domain/model/
├── Property.kt
├── PropertyType.kt       # enum: PENSION, HOTEL, MOTEL, GUESTHOUSE, RESORT
├── PropertyStatus.kt     # enum: ACTIVE, INACTIVE, SUSPENDED
├── Address.kt            # VO: street, city, state, zipCode, country
└── ContactInfo.kt        # VO: phone, email, website?

src/test/kotlin/com/stayops/property/domain/model/
└── PropertyTest.kt
```

**도메인 모델:**
```kotlin
data class Property(
    val id: String,
    val ownerId: String,
    val name: String,
    val type: PropertyType,
    val address: Address,
    val contactInfo: ContactInfo,
    val description: String,
    val status: PropertyStatus,
    val timezone: String = "Asia/Seoul",
    val currency: String = "KRW",
    val version: Long = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
```

**비즈니스 규칙:**
- `activate()`: INACTIVE → ACTIVE
- `deactivate()`: ACTIVE → INACTIVE (기존 예약은 유지)
- `suspend()`: ACTIVE → SUSPENDED (관리자 조치)
- `isBookable()`: status == ACTIVE

**TDD 순서:**
1. RED: Property 생성 + 상태 전이 테스트
2. GREEN: Property 구현
3. RED: Address, ContactInfo VO 불변식 테스트
4. GREEN: VO 구현
5. REFACTOR

---

### Phase 2-2: Repository + MongoDB 구현 + 통합 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/property/domain/repository/
└── PropertyRepository.kt   # interface

src/main/kotlin/com/stayops/property/infrastructure/persistence/
├── PropertyDocument.kt
└── MongoPropertyRepository.kt

src/test/kotlin/com/stayops/property/infrastructure/persistence/
└── MongoPropertyRepositoryTest.kt
```

**Repository 인터페이스:**
```kotlin
interface PropertyRepository {
    fun save(property: Property): Property
    fun findById(id: String): Property?
    fun findByOwnerId(ownerId: String): List<Property>
    fun findAll(): List<Property>
}
```

**MongoDB 인덱스:**
- `{ ownerId: 1 }`

---

### Phase 2-3: PropertyService + API + E2E 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/property/application/service/
└── PropertyService.kt

src/main/kotlin/com/stayops/property/api/
├── PropertyController.kt
├── dto/CreatePropertyRequest.kt
├── dto/UpdatePropertyRequest.kt
└── dto/PropertyResponse.kt

src/test/kotlin/com/stayops/property/application/service/
└── PropertyServiceTest.kt

src/test/kotlin/com/stayops/property/api/
└── PropertyControllerTest.kt
```

**API 엔드포인트:**
```
POST   /api/v1/properties           — 숙소 생성
GET    /api/v1/properties           — 전체 목록 조회
GET    /api/v1/properties/{pid}     — 상세 조회
PUT    /api/v1/properties/{pid}     — 수정
```

---

## 검증 기준

- [ ] Property 도메인 단위 테스트 통과
- [ ] MongoDB 통합 테스트 통과 (Testcontainers)
- [ ] Property CRUD API E2E 테스트 통과
- [ ] `./gradlew test` 전체 통과
