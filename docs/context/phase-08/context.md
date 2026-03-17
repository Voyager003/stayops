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

## RateResolver 빈 등록

- **결정**: `rate/infrastructure/config/RateConfig.kt`에서 `@Bean`으로 등록
- **이유**: `RateResolver`는 도메인 서비스이므로 `@Component` 부착 불가 (도메인 순수성 규칙). infrastructure 레이어의 `@Configuration`으로 등록
