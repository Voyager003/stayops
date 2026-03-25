# Phase 7 의사결정 컨텍스트

## 아키텍처 결정: PMS 내장 채널 매니저

- **결정**: StayOps PMS 내부에 채널 매니저 기능을 직접 구현
- **배경**: 프로덕션에서 채널 매니저는 전문 SaaS(SiteMinder, Channex 등)가 제공하거나, 대형 PMS(Cloudbeds, Mews)가 자체 구축한다
- **선택 근거**:
  - 포트폴리오 목적 — 분산 시스템 설계 역량(Outbox, Adapter Registry, 매핑, 재시도)을 직접 어필
  - 채널 매니저의 핵심 역할(ARI 변환/배포, 예약 수집/통합, OTA별 연결 관리)을 PMS 내부에 구현
  - 외부 채널 매니저 사용 시 PMS는 API 클라이언트 구현 위주가 되어 학습 범위가 좁아짐

## Mock OTA 서버

- **결정**: 별도 Spring Boot 앱(Gradle 멀티 모듈)으로 Mock OTA 구축
- **배경**: 실제 OTA API(Agoda, Booking.com 등) 사용 불가 (파트너 인증 필요)
- **선택 근거**:
  - in-process VirtualAdapter(80% 성공 랜덤)는 "실제 연동"이라 보기 어려움
  - 별도 프로세스 간 실제 HTTP 통신으로 네트워크 장애, HMAC 서명, 재시도를 프로덕션과 동일하게 검증
  - 시연 시 두 앱을 띄워 E2E 흐름을 눈으로 보여줄 수 있음
  - Mock OTA를 실제 OTA로 교체하면 PMS 코드 변경 없이 프로덕션 배포 가능한 구조

## ARI 범위

- **결정**: Availability(재고)만 MVP로 구현
- **배경**: 프로덕션에서 ARI는 Availability, Rate, Restriction 세 가지. Channex는 Availability와 Rate/Restriction을 분리된 API로 처리
- **선택 근거**: 핵심 오버부킹 방지 로직에 집중. Rate/Restriction은 동일 구조로 확장 가능하므로 Phase 7 이후에 추가

## Outbox 패턴 + HTTP 직접 호출

- **결정**: Transactional Outbox(SyncTask in MongoDB) + 스케줄러가 OTA에 직접 HTTP 호출
- **배경**: 전통적 Outbox는 DB에 저장 후 메시징 시스템(Kafka 등)에 발행하지만, 우리는 메시징 시스템 없이 HTTP로 직접 호출한다
- **Outbox가 필요한 이유**:
  - 재고 차감과 OTA 알림은 서로 다른 시스템(MongoDB vs 외부 HTTP)에서 일어남
  - 재고 차감 후 앱이 crash하면 OTA에 알림이 소실 → 오버부킹 위험
  - 같은 트랜잭션에 SyncTask를 저장하여 "재고 변경됐는데 알림 소실" 방지
  - MongoDB replica set의 멀티 도큐먼트 트랜잭션으로 원자성 보장
- **@Async 대신 Outbox를 선택한 이유**:
  - @Async는 메시지가 메모리에만 존재하여 앱 재시작 시 소실
  - Sync Dashboard(채널별 성공/실패/대기 조회)에는 영속적 기록이 필수
  - 실패 건 수동 재시도 기능에도 영속 상태가 필요
- **메시징 시스템(Kafka 등) 없이 HTTP 직접 호출의 트레이드오프**:
  - 스케줄러 병목 — 하나의 OTA가 느리면 뒤의 모든 태스크가 밀림
  - Scale-out 시 중복 처리 — 멀티 인스턴스에서 같은 SyncTask를 동시에 가져감 (분산 락 필요)
  - 순서 보장 불가 — 같은 객실의 연속 변경이 역순으로 도착할 수 있음
  - **현재 프로젝트에서는 단일 인스턴스 + Mock OTA 2~3개이므로 실질적 영향 없음. 메시징 시스템 도입 대비 직접 호출이 합리적**

## ARI Push 동시성 이슈 (미해결)

30초 폴링 기반 ARI Push에서 두 가지 정합성 리스크가 존재한다.

### 1. 폴링 지연 중 오버부킹

- 예약 발생 시점부터 OTA 반영까지 최대 30초 지연
- 그 사이 OTA에는 이전 재고가 표시되어 오버부킹 가능
- 호텔 업계에서는 폴링 지연에 의한 오버부킹을 정책(보상/대체 배정)으로 대응하는 것이 일반적
- 완화: 폴링 주기 단축 또는 이벤트 기반(Kafka 등) 전환

### 2. 재시도 시 순서 역전

- 현재 payload에 절대값(availableCount)을 사용하여 정상 순서에서는 마지막 값이 최신 상태를 반영
- 그러나 이전 태스크가 실패 후 재시도되면 오래된 값이 최신 값을 덮어쓸 수 있음
- 예: Task1(count=4) 실패 → Task2(count=3) 성공 → Task1 재시도 성공 → OTA에 4로 잘못 반영
- 해결 방안 (현재 미구현):
  - **태스크 병합**: 같은 (propertyId + roomTypeId + date + channelCode) PENDING 태스크가 있으면 payload를 최신 값으로 갱신
  - **버전/타임스탬프**: OTA 측에서 오래된 업데이트 무시
  - **메시징 큐**: Kafka 등으로 순서 보장
- 현재 프로젝트는 단일 인스턴스 + Mock OTA 환경이므로 실질적 영향은 제한적이나, 프로덕션 전환 시 태스크 병합이 1차 해법

## 코드 리뷰 미해결 이슈 (추후 수정)

### CRITICAL

- **C2: application 레이어가 infrastructure 구현체 직접 의존** — `ChannelSyncApplication`이 `ChannelAdapterRegistry`를, `WebhookApplication`이 `HmacSignatureVerifier`를 직접 import. 도메인 인터페이스로 추상화 필요
- **C3: findChannel에서 propertyId 검증 누락** — channelId로만 조회하여 다른 숙소의 채널에 접근 가능 (테넌트 격리 실패). updateChannel, deleteChannel 등 모든 메서드에 영향
- **C4: retryTask에서 propertyId 검증 누락** — taskId로만 조회하여 다른 숙소의 태스크 재시도 가능 (테넌트 격리 실패)
- **C5: 채널/매핑 없는 SyncTask를 COMPLETED 처리** — 설정 오류를 성공으로 숨겨 대시보드 오염. FAILED 처리로 변경 필요

### HIGH

- **H1: API Request DTO에 Bean Validation 미적용** — `@Valid`, `@NotBlank`, `@DecimalMin` 등 누락
- **H2: Webhook eventId/eventType 빈 문자열 허용** — 누락 시 빈 문자열로 처리되어 이후 중복 판단 오류
- **H3: Webhook 중복 체크 TOCTOU 레이스** — 동시 요청 시 DuplicateKeyException 미처리, 500 에러 반환
- **H4: Mock OTA Thread.sleep이 서블릿 스레드 블로킹** — 타임아웃 시뮬레이션에서 스레드풀 고갈 가능
- **H5: ChannelApi에서 channelId와 channelCode 경로 혼용** — 같은 경로에서 의미가 달라 혼동 가능

### MEDIUM

- **M2: updateChannel이 reconstitute()로 검증 우회** — 도메인 불변식을 건너뜀
- **M3~4: 테스트 커버리지 부족** — updateChannel, deleteChannel, getSyncTasks 등 미테스트
- **M5: ProcessedWebhookEvent에 private constructor 패턴 미적용**
- **M6: PMS-MockOTA 간 HMAC 구현 중복**
- **M8: HttpChannelSyncAdapter에 타임아웃 미설정** — OTA 장애 시 무한 대기 가능
- **M10: WebhookEvent sealed interface 미사용** — dead code

## 기존 코드 처리

- **결정**: Phase 7-1(Channel 도메인), 7-2(Repository), 7-3(SyncTask) 코드를 폐기하고 새로 작성
- **근거**: 기존 설계가 프로덕션 패턴과 괴리 — ChannelPolicy 구조, 비타입 payload(Map<String, Any>), 연결 설정 부재 등

## 프로덕션 리서치 기반 설계 원칙

Channex, Booking.com Connectivity API, RateGain 문서 조사 결과 반영:
- **매핑이 근간**: PMS roomTypeId ↔ OTA 코드 양방향 매핑 없이는 ARI push도 webhook 수신도 불가
- **ARI Push**: 변경 즉시 전송 + 매일 밤 전체 풀 싱크 (Channex 권장 패턴)
- **Webhook은 알림 역할**: 실제 데이터는 API로 pull하는 것이 안전 (순서 보장 불가)
- **이벤트 중복 제거 필수**: OTA는 at-least-once로 webhook을 재전송할 수 있음
- **Acknowledge 패턴**: 처리 완료된 예약을 확인하여 재전송 방지
