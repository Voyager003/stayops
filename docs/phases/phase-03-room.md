# Phase 3: Room 도메인

객실타입(RoomType)과 객실(Room) 관리. propertyId로 스코핑.

---

## 기능적 요구사항

- **객실타입 관리**: 숙소별로 객실타입(디럭스, 스위트 등)을 등록하고 기본 요금·최대 수용 인원·편의시설을 설정할 수 있어야 한다
- **개별 객실 관리**: 객실타입에 속하는 개별 객실(호수, 층수)을 등록하고 상태를 관리할 수 있어야 한다
- **객실 상태 전이**: 체크인(AVAILABLE → OCCUPIED), 체크아웃(OCCUPIED → CLEANING), 정비(AVAILABLE ↔ MAINTENANCE) 등 상태 흐름을 제어해야 한다
- **체크인 시 객실 배정**: 예약 체크인 시 특정 Room을 배정할 수 있어야 한다 (Phase 8 연동)

## 기술적 도전 과제

| 과제 | 설명 | 해결 전략 |
|------|------|----------|
| 호수 유니크 제약 | 같은 숙소 내 동일 호수 중복 등록 방지 | MongoDB 복합 유니크 인덱스 `{ propertyId: 1, roomNumber: 1 }` |
| 객실타입명 유니크 | 같은 숙소 내 동일 타입명 중복 방지 | MongoDB 복합 유니크 인덱스 `{ propertyId: 1, name: 1 }` |
| 상태 전이 무결성 | 허용되지 않는 전이(e.g., CLEANING → OCCUPIED) 방지 | 도메인 모델에서 유효 전이만 허용, `when` 기반 상태 머신 |

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
    val id: String,
    val propertyId: String,
    val name: String,
    val description: String,
    val maxOccupancy: Int,
    val basePrice: Money,
    val amenities: List<String>,
    val status: RoomTypeStatus,
    val version: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

**Room 도메인 모델:**
```kotlin
data class Room(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val roomNumber: String,
    val floor: Int,
    val status: RoomStatus,
    val memo: String? = null,
    val version: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant
)
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
