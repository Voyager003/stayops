# StayOps

호텔 및 숙소 운영을 위한 PMS·CMS 서비스

---

## Tech Stack

- Language: Kotlin 2.2
- JDK 21
- Spring Boot 4.0.3
- Persistence: Spring Data MongoDB
- DB: MongoDB (replica set)
- Cache: Redis
- Auth: JWT
- Test: Kotest (BehaviorSpec) + MockK + Testcontainers
- API Docs: springdoc-openapi (Swagger)

---

## Functional Requirements

### 프로젝트 달성 목표

- 호텔 예약 PMS, CMS 시스템을 분석하여 숙소 운영의 핵심 도메인(객실·재고·예약·채널·정산)을 실제 서비스 수준으로 구현한다
- 멀티 채널(FineStay + OTA) 환경에서 재고 정합성과 데이터 일관성을 보장하는 서버를 구축한다

### BE 역량 목표

- **동시성 제어** — 마지막 1객실 동시 예약 시 재고 정합성 보장
- **데이터 일관성** — Outbox 패턴으로 메시지 브로커 없이 채널 간 Eventually Consistent 동기화
- **도메인 모델링** — 9개 BC 분리, 순수 도메인 객체, 도메인 이벤트 기반 크로스 컨텍스트 연동

### 기능적 요구사항

- **멀티 숙소 관리**
  - 복수의 숙소(호텔, 펜션, 리조트 등)를 하나의 계정으로 관리
  - 숙소별 독립적인 객실, 재고, 요금, 예약 데이터 운영
- **객실 및 재고 관리**
  - 객실타입/객실 CRUD, 날짜별 재고 수량 관리
  - 동시 예약 요청 시 Race Condition 방어
- **멀티 채널 판매**
  - 자체 예약(FineStay) + OTA(Agoda, Airbnb 등) 채널 통합 관리
  - OTA Webhook 수신을 통한 외부 예약 자동 생성
  - Outbox 패턴 기반 채널 간 재고 동기화
- **동적 요금 관리**
  - 시즌/요일/채널별 요금제 설정 + 우선순위 기반 요금 결정(RateResolver)
- **예약 라이프사이클**
  - 예약 생성 → 확정 → 체크인 → 체크아웃 상태 전이
  - 취소/노쇼 처리 + 재고 자동 복원
- **정산**
  - 채널별 수수료/실 정산액 집계 (MongoDB aggregation)
- **인증/인가**
  - JWT 기반 인증, 멀티 숙소 접근 권한 제어

### 기술적 도전 과제

- **필수**
  - 낙관적 락 동시성 제어: 마지막 1객실 동시 예약 → 정확히 1건만 성공
  - Outbox 패턴: 메시지 브로커 없이 MongoDB + 스케줄러로 신뢰성 있는 비동기 동기화
  - 도메인 이벤트: Bounded Context 간 결합도를 낮추면서 크로스 컨텍스트 연동
- **권장**
  - 가상 채널 어댑터로 실제 OTA API 없이 동기화 플로우 검증
  - TDD 전 계층 적용 (Red-Green-Refactor)
  - Testcontainers로 실제 MongoDB/Redis 기반 e2e 테스트

---

## Architecture

- **DDD**: 비즈니스 로직은 도메인 객체 내부에 위치
- **Layered Architecture**: Controller → Service → Domain ← Repository

### Bounded Context Map

| # | Context | 설명 |
|---|---------|------|
| 1 | **Property** | 숙소 관리, 멀티 숙소 지원 (테넌트 경계) |
| 2 | **Room** | 객실/객실타입 관리 |
| 3 | **Inventory** | 날짜별 객실 재고, 낙관적 락 동시성 제어 |
| 4 | **Guest** | 고객 등급, 방문/지출 이력 |
| 5 | **Channel** | 판매 채널 관리 (FineStay/OTA), Outbox 기반 재고 동기화, 가상 채널 어댑터 |
| 6 | **Rate** | 시즌/요일/채널별 동적 요금제, RateResolver |
| 7 | **Reservation** | 예약 CRUD, 상태 전이, 채널별 정산예정 금액 |
| 8 | **Settlement** | 채널별 수수료/실 정산액 집계 (MongoDB aggregation) |
| 9 | **Auth** | JWT 인증, 멀티 숙소 접근 권한 |

```
                         ┌──────────┐
                         │ Property │
                         └────┬─────┘
                              │ propertyId
      ┌───────────┬───────────┼───────────┬──────────┬──────────┐
      ▼           ▼           ▼           ▼          ▼          ▼
  ┌──────┐  ┌─────────┐  ┌─────┐     ┌──────┐  ┌──────┐  ┌──────────┐
  │ Room │  │Inventory│  │Guest│     │ Rate │  │ Auth │  │Settlement│
  └──┬───┘  └────┬────┘  └──┬──┘     └──┬───┘  └──────┘  └────┬─────┘
     │      ┌────┴────┐     │           │                      │
     │      │ Channel │◄────┼───────────┼──────────────────────┘
     │      └────┬────┘     │           │
     ▼           ▼          ▼           ▼
  ┌──────────────────────────────────────────────────────────────┐
  │                     Reservation                              │
  └──────────────────────────────────────────────────────────────┘
```

### Reservation Flow

> TODO: 예약 생성 → 확정 → 체크인 → 체크아웃 플로우 다이어그램 추가

### Channel Sync Flow

```
FineStay 예약 생성
→ Inventory 차감
→ SyncTask(PENDING) 생성 (AGODA, AIRBNB 등 활성 OTA 대상)
→ SyncTaskScheduler 폴링
→ VirtualChannelSyncAdapter 호출
→ 성공 → COMPLETED / 실패 → 재시도 (max 3회)
→ 관리자: Sync Dashboard에서 채널별 동기화 현황 확인
```

---

## Infrastructure

> TODO: 프로젝트 완성 후 인프라 구성도 추가

---

## DB

> TODO: 프로젝트 완성 후 ERD 다이어그램 추가

---

## 패키지 구조

```
src/main/kotlin/com/stayops/
├── shared/          # Money, DateRange, 예외, 설정
├── property/        # 숙소 관리
├── room/            # 객실/객실타입
├── inventory/       # 날짜별 재고 + Redis 캐시
├── guest/           # 고객 등급/이력
├── channel/         # 채널 CRUD, webhook 수신, Outbox 동기화
├── rate/            # 요금제 + RateResolver
├── reservation/     # 예약 + 도메인 이벤트
├── settlement/      # MongoDB aggregation 정산
└── auth/            # JWT + Redis Refresh Token
```

---

## 구현 단계 (10 Phases)

각 Phase의 도메인 모델, TDD 순서, 생성 파일 목록 등 상세 계획은 [`docs/phases/`](docs/phases/) 참조.

| Phase | 내용 |
|-------|------|
| [Phase 1](docs/phases/phase-01-foundation.md) | Foundation — 공통 도메인, MongoDB/Redis 설정, 예외 핸들러 |
| [Phase 2](docs/phases/phase-02-property.md) | Property 도메인 |
| [Phase 3](docs/phases/phase-03-room.md) | Room 도메인 |
| [Phase 4](docs/phases/phase-04-inventory.md) | Inventory 도메인 |
| [Phase 5](docs/phases/phase-05-guest.md) | Guest 도메인 |
| [Phase 6](docs/phases/phase-06-rate.md) | Rate 도메인 |
| [Phase 7](docs/phases/phase-07-channel.md) | Channel — 채널 관리, OTA webhook, Outbox 동기화, 가상 채널 어댑터 |
| [Phase 8](docs/phases/phase-08-reservation.md) | Reservation 도메인 |
| [Phase 9](docs/phases/phase-09-settlement.md) | Settlement |
| [Phase 10](docs/phases/phase-10-auth.md) | Auth |

---

## How to Run

```bash
# 인프라 기동
docker compose up -d

# Backend
./gradlew bootRun        # http://localhost:8080
./gradlew test           # 전체 테스트
```

- Swagger UI: http://localhost:8080/swagger-ui.html

---

## Preview

> TODO: 프로젝트 완성 후 스크린샷 추가
