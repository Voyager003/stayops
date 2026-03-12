# Phase 3 리팩터링 메모

코드 리뷰에서 발견된 이슈. 즉시 수정하지 않고 추후 리팩터링 시 반영.

---

## Critical

### [C-1] RoomStatusAction 레이어 의존성 역전

**문제:**
`RoomStatusAction` enum이 `api.dto` 패키지에 선언되어 있어,
`application` 레이어가 `api` 레이어를 import하는 역방향 의존이 발생.

```kotlin
// RoomApplication.kt (application 레이어)
import com.stayops.room.api.dto.RoomStatusAction  // ← api를 참조
```

**영향:**
- gRPC 등 두 번째 API 채널 추가 시 REST api.dto를 참조해야 하는 구조
- 도메인 개념 추가 시 변경의 출발점이 presentation layer가 됨

**수정 방향:**
`RoomStatusAction`을 `domain/model`로 이동.
`UpdateRoomStatusRequest`는 domain의 enum을 참조하거나 별도 매핑.

---

## Major

### [M-1] RoomType.updateInfo() 검증 로직 중복

`updateInfo()` 내부의 `require` 3개가 `init` 블록과 동일.
`copy()` 호출 시 `init`이 자동 재실행되므로 중복 제거 가능.

```kotlin
// 제거 대상 (RoomType.kt:48-50)
require(name.isNotBlank()) { ... }
require(maxOccupancy >= 1) { ... }
require(basePrice.amount > BigDecimal.ZERO) { ... }
```

### [M-2] 인덱스 정의 이중화

`@CompoundIndex` 어노테이션(Document 클래스)과
`RoomMongoIndexConfiguration`(@PostConstruct) 양쪽에 동일 인덱스 정의.
하나의 방식으로 통일 필요.

### [M-3] pid(propertyId) 테넌트 격리 미검증

`GET/PUT/PATCH /api/v1/properties/{pid}/room-types/{id}` 등에서
URL의 `pid`와 실제 리소스의 `propertyId` 일치 여부를 검증하지 않음.
다른 숙소의 리소스에 접근 가능한 보안 이슈.

**수정 방향:** Repository에 `findByIdAndPropertyId()` 추가 또는 Application 서비스에서 검증.

### [M-4] roomTypeId 존재 여부 미검증

`RoomApplication.createRoom()` 시 `roomTypeId`가 실제 존재하는지 확인하지 않음.
`RoomApplication`에 `RoomTypeRepository`를 주입하여 존재 여부 검증 필요.

---

## Minor

- `PUT /rooms/{id}` → `PATCH`가 더 적절 (부분 수정)
- `deactivateRoomType()` 반환값 없음 — 다른 메서드와 일관성 불일치
- `activate()` 도메인 메서드가 API에 노출되지 않아 비활성화 후 재활성화 불가
- `RoomDocument`, `RoomTypeDocument`에 `@Version` 낙관적 락 미적용
- `GlobalExceptionHandler`에 `MethodArgumentNotValidException` 및 범용 `Exception` 핸들러 부재
- `CreateRoomTypeRequest.kt`에 미사용 `@NotNull` import
- `GET /rooms` 필터링(type, status, floor) 미구현
