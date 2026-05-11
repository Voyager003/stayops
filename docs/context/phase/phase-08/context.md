# Phase 8 의사결정 컨텍스트

## 리팩토링 예정: ReservationApplication 모듈 간 의존성 정리

- **현상**: ReservationApplication이 다른 모듈의 Repository를 직접 접근 (roomTypeRepository, channelRepository, ratePlanRepository, guestRepository)
- **문제**: 모듈 경계를 침범하여 내부 구현에 결합됨. 해당 모듈의 Repository 구조가 바뀌면 ReservationApplication에 영향
- **해결 방안**: 다른 모듈은 Application 서비스를 통해 접근
  - `roomTypeRepository` → `RoomTypeApplication.getRoomType()` (신규 생성 필요)
  - `channelRepository` → `ChannelApplication.findChannel()` (이미 존재)
  - `guestRepository` → `GuestApplication.getGuest()` (이미 존재)
  - `ratePlanRepository + rateResolver` → `RatePlanApplication.resolveRoomRate()` (신규 생성 필요)
- **현재 상태**: 추후 리팩토링 예정

## 코드 리뷰 미해결 이슈 (추후 수정)

### CRITICAL

- **C1: @Transactional 누락** — 재고 차감 + 예약 저장 + Room 상태 변경이 원자적이지 않음. 중간 실패 시 데이터 불일치 발생. `createReservation`, `cancelReservation`, `checkInReservation`, `checkOutReservation`에 `@Transactional` 적용 필요
- **C2: 다날짜 재고 차감 보상 로직 없음** — 3박 예약에서 1~2일차 성공 후 3일차 실패 시 1~2일차 재고 영구 소실. `@Transactional`로 해결하거나 수동 보상 로직 필요
- **C3: 테넌트 격리 누락** — RoomType, Room, Guest 조회 시 propertyId 검증 없음. 다른 숙소의 객실타입/객실/고객으로 예약 가능 (보안 위반)
- **C4: Channel 상태 검증 누락** — SUSPENDED/INACTIVE 채널로 예약 생성 가능. `channel.status == ACTIVE` 검증 필요

### HIGH

- **H1: 노쇼 시 재고 미복원** — 의도적 결정이면 문서화 필요, 아니면 release() 추가
- **H2: API Request DTO에 Bean Validation 미적용** — `@Valid`, `@NotBlank`, `@Positive` 등 누락
- **H3: getReservation에서 require 사용** — 테넌트 검증 실패 시 400 반환. NotFoundException(404)으로 변경하여 존재 정보 노출 방지
- **H4: 페이징 없는 무제한 목록 조회** — `findByPropertyId` 등이 전체 목록 반환

### MEDIUM

- **M2: checkOut 순서 문제** — 예약 저장 후 Room 업데이트. Room 실패 시 불일치. Room 먼저 업데이트하도록 순서 변경 필요
- **M3: 테스트 보일러플레이트 중복** — 예약 생성 코드가 6회 반복. 헬퍼 함수 추출 필요
- **M4: ChannelEventHandlerTest available 파라미터 미사용** — 인벤토리 생성 시 available 값 무시
- **M5: Guest 조회 후 미사용** — 존재 확인만 하고 데이터는 request 파라미터 사용. 불필요한 변수 제거 또는 Guest 데이터 활용

## RateResolver 빈 등록

- **결정**: `rate/infrastructure/config/RateConfig.kt`에서 `@Bean`으로 등록
- **이유**: `RateResolver`는 도메인 서비스이므로 `@Component` 부착 불가 (도메인 순수성 규칙). infrastructure 레이어의 `@Configuration`으로 등록
