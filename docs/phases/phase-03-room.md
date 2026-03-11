# Phase 3: Room 도메인

객실타입(RoomType)과 객실(Room) 관리. propertyId로 스코핑.

---

## Sub-steps

### Phase 3-1: RoomType + Room 도메인 모델 + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/room/domain/model/
├── RoomType.kt
├── RoomTypeStatus.kt    # enum: ACTIVE, INACTIVE
├── Room.kt
└── RoomStatus.kt        # enum: AVAILABLE, OCCUPIED, MAINTENANCE, CLEANING

src/test/kotlin/com/stayops/room/domain/model/
├── RoomTypeTest.kt
└── RoomTest.kt
```

**RoomType 도메인 모델:**
```kotlin
data class RoomType(
    override val id: String,
    val propertyId: String,
    val name: String,
    val description: String,
    val maxOccupancy: Int,
    val basePrice: Money,
    val amenities: List<String>,
    val status: RoomTypeStatus,
    override val version: Long = 0,
    override val createdAt: Instant,
    override val updatedAt: Instant
) : AggregateRoot()
```

**Room 도메인 모델:**
```kotlin
data class Room(
    override val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val roomNumber: String,
    val floor: Int,
    val status: RoomStatus,
    val memo: String? = null,
    override val version: Long = 0,
    override val createdAt: Instant,
    override val updatedAt: Instant
) : AggregateRoot()
```

**비즈니스 규칙:**
- RoomType: maxOccupancy >= 1, basePrice > 0
- Room: roomNumber 비어있지 않음, floor >= 1
- Room 상태 전이: AVAILABLE ↔ OCCUPIED, AVAILABLE ↔ MAINTENANCE, AVAILABLE ↔ CLEANING

**TDD 순서:**
1. RED: RoomType 불변식 테스트
2. GREEN: RoomType 구현
3. RED: Room 상태 전이 테스트
4. GREEN: Room 구현
5. REFACTOR

---

### Phase 3-2: Repository + MongoDB 구현 + 통합 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/room/domain/repository/
├── RoomTypeRepository.kt
└── RoomRepository.kt

src/main/kotlin/com/stayops/room/infrastructure/persistence/
├── RoomTypeDocument.kt
├── MongoRoomTypeRepository.kt
├── RoomDocument.kt
└── MongoRoomRepository.kt

src/test/kotlin/com/stayops/room/infrastructure/persistence/
├── MongoRoomTypeRepositoryTest.kt
└── MongoRoomRepositoryTest.kt
```

**MongoDB 인덱스:**
- `rooms: { propertyId: 1, roomNumber: 1 }` (unique)
- `room_types: { propertyId: 1, name: 1 }` (unique)

---

### Phase 3-3: Application 서비스 + API + E2E 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/room/application/service/
├── RoomTypeService.kt
└── RoomService.kt

src/main/kotlin/com/stayops/room/api/
├── RoomTypeController.kt
├── RoomController.kt
└── dto/
    ├── CreateRoomTypeRequest.kt
    ├── UpdateRoomTypeRequest.kt
    ├── RoomTypeResponse.kt
    ├── CreateRoomRequest.kt
    ├── UpdateRoomRequest.kt
    ├── RoomResponse.kt
    └── UpdateRoomStatusRequest.kt

src/test/kotlin/com/stayops/room/application/service/
├── RoomTypeServiceTest.kt
└── RoomServiceTest.kt

src/test/kotlin/com/stayops/room/api/
├── RoomTypeControllerTest.kt
└── RoomControllerTest.kt
```

**API 엔드포인트:**

Room Types:
```
POST   /api/v1/properties/{pid}/room-types
GET    /api/v1/properties/{pid}/room-types
GET    /api/v1/properties/{pid}/room-types/{id}
PUT    /api/v1/properties/{pid}/room-types/{id}
DELETE /api/v1/properties/{pid}/room-types/{id}
```

Rooms:
```
POST   /api/v1/properties/{pid}/rooms
GET    /api/v1/properties/{pid}/rooms          (필터: type, status, floor)
GET    /api/v1/properties/{pid}/rooms/{id}
PUT    /api/v1/properties/{pid}/rooms/{id}
PATCH  /api/v1/properties/{pid}/rooms/{id}/status
```

---

## 검증 기준

- [ ] RoomType, Room 도메인 단위 테스트 통과
- [ ] MongoDB 통합 테스트 통과 (unique 인덱스 검증 포함)
- [ ] Room Types, Rooms API E2E 테스트 통과
- [ ] `./gradlew test` 전체 통과
