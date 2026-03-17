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
