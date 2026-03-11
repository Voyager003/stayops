# Phase 2: Property 도메인

숙소(Property) 관리. 모든 데이터의 최상위 스코프(테넌트 경계).

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
    override val id: String,
    val ownerId: String,
    val name: String,
    val type: PropertyType,
    val address: Address,
    val contactInfo: ContactInfo,
    val description: String,
    val status: PropertyStatus,
    val timezone: String = "Asia/Seoul",
    val currency: String = "KRW",
    override val version: Long = 0,
    override val createdAt: Instant = Instant.now(),
    override val updatedAt: Instant = Instant.now()
) : AggregateRoot()
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
