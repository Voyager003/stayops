# Phase 7: Channel — 채널 관리 (내장 채널 매니저)

PMS 내부에 채널 매니저를 구축하여 OTA(Agoda, Booking.com 등)와의 재고 동기화 및 예약 수신을 처리한다.
실제 OTA API가 없는 환경을 고려하여 Mock OTA 서버로 프로덕션 동일 흐름을 구현한다.

---

## 아키텍처

```
StayOps PMS (port 8080)                    Mock OTA Server (port 8081)
┌─────────────────────────┐                ┌──────────────────────┐
│  ChannelApi (CRUD)      │                │  ARI 수신 엔드포인트  │
│  ChannelWebhookApi      │◄── Webhook ────│  예약 시뮬레이션 API  │
│  SyncDashboardApi       │                │  취소 시뮬레이션 API  │
│                         │                │                      │
│  ChannelApplication     │                │  Webhook 발송 기능   │
│  ChannelSyncApplication │── ARI Push ──►│  (HMAC 서명 포함)    │
│  WebhookApplication     │                │                      │
│                         │                │  장애 시뮬레이션     │
│  Channel (도메인)        │                │  (타임아웃, 503 등)  │
│  ChannelMapping (도메인)  │                └──────────────────────┘
│  SyncTask (Outbox)      │
│                         │
│  ChannelAdapterRegistry │
│  HttpChannelSyncAdapter │
│  SyncTaskScheduler      │
└─────────────────────────┘
```

### 데이터 흐름

**아웃바운드 (PMS → OTA): ARI Push**
```
재고 변경 발생
→ 모든 활성 OTA 채널에 대해 SyncTask(PENDING) 생성 (Outbox)
→ 스케줄러가 PENDING 태스크 폴링
→ ChannelMapping으로 PMS roomTypeId → OTA 코드 변환
→ ChannelAdapterRegistry에서 채널별 Adapter 조회
→ Adapter가 Mock OTA의 ARI 수신 엔드포인트에 HTTP POST
→ 성공 → COMPLETED / 실패 → 지수 백오프 후 재시도
```

**인바운드 (OTA → PMS): 예약/취소 Webhook**
```
Mock OTA가 PMS webhook 엔드포인트에 HTTP POST (HMAC 서명 포함)
→ HMAC 서명 검증
→ 이벤트 ID로 중복 확인
→ ChannelMapping으로 OTA 코드 → PMS roomTypeId 변환
→ 예약 생성 또는 취소 처리 (Phase 8 연동)
→ 변경된 재고를 다른 OTA 채널에 ARI Push (오버부킹 방지)
```

---

## 기능적 요구사항

- **채널 등록·관리**: OTA 채널을 등록하고 연결 설정(API 엔드포인트, 인증 키, webhook secret), 수수료율, 상태를 관리
- **자사 숙소 예매 사이트 자동 생성**: 숙소 등록 시 자사 숙소 예매 사이트(자사 직영) 채널이 자동 생성 (수수료 0%, DIRECT 타입)
- **채널 매핑**: PMS의 roomTypeId와 OTA의 객실 코드 간 양방향 매핑 관리
- **재고 동기화 (ARI Push)**: 재고 변경 시 모든 활성 OTA 채널에 Availability를 자동 전파
- **외부 예약 수신 (Webhook)**: OTA로부터 예약/취소 webhook을 수신하고 HMAC 검증 후 처리
- **동기화 대시보드**: 채널별 동기화 상태(성공/실패/대기)를 모니터링하고 실패 태스크를 수동 재시도

---

## 기술적 도전 과제

| 과제 | 해결 전략 |
|------|----------|
| 메시지 브로커 없는 비동기 동기화 | Outbox 패턴 — SyncTask를 동일 트랜잭션에 저장, 스케줄러가 폴링 |
| 동기화 실패 복구 | 지수 백오프 재시도(30s→60s→120s) + maxRetries(3) 초과 시 FAILED |
| 멱등성 보장 | SyncTask에 idempotencyKey 부여, OTA 측 중복 push 방지 |
| Webhook 위조 방지 | HMAC-SHA256 서명 검증 |
| Webhook 중복 수신 | ProcessedWebhookEvent로 eventId 기반 중복 제거 (TTL 7일) |
| PMS ↔ OTA ID 불일치 | ChannelMapping으로 양방향 코드 변환 |
| 실제 OTA 없이 E2E 검증 | Mock OTA 서버 (별도 Spring Boot 앱, 실제 HTTP 통신) |

---

## 프로젝트 구조

### Gradle 멀티 모듈

```
stayops/
├── stayops-pms/              # 기존 PMS 애플리케이션
│   └── src/main/kotlin/com/stayops/channel/
│       ├── domain/model/
│       │   ├── Channel.kt
│       │   ├── ChannelType.kt
│       │   ├── ChannelStatus.kt
│       │   ├── ChannelConnectionConfig.kt
│       │   ├── ChannelMapping.kt
│       │   ├── MappingEntry.kt
│       │   ├── MappingType.kt
│       │   ├── SyncTask.kt
│       │   ├── SyncTaskStatus.kt
│       │   ├── SyncTaskType.kt
│       │   └── ProcessedWebhookEvent.kt
│       ├── domain/repository/
│       │   ├── ChannelRepository.kt
│       │   ├── ChannelMappingRepository.kt
│       │   ├── SyncTaskRepository.kt
│       │   └── ProcessedWebhookEventRepository.kt
│       ├── domain/service/
│       │   └── ChannelSyncAdapter.kt
│       ├── application/
│       │   ├── dto/
│       │   │   └── AvailabilityPayload.kt
│       │   └── service/
│       │       ├── ChannelApplication.kt
│       │       ├── ChannelSyncApplication.kt
│       │       ├── WebhookApplication.kt
│       │       └── SyncDashboardApplication.kt
│       ├── infrastructure/
│       │   ├── persistence/
│       │   │   ├── ChannelDocument.kt
│       │   │   ├── MongoChannelRepository.kt
│       │   │   ├── ChannelMappingDocument.kt
│       │   │   ├── MongoChannelMappingRepository.kt
│       │   │   ├── SyncTaskDocument.kt
│       │   │   ├── MongoSyncTaskRepository.kt
│       │   │   ├── ProcessedWebhookEventDocument.kt
│       │   │   └── MongoProcessedWebhookEventRepository.kt
│       │   ├── external/
│       │   │   ├── HttpChannelSyncAdapter.kt
│       │   │   └── ChannelAdapterRegistry.kt
│       │   ├── webhook/
│       │   │   └── HmacSignatureVerifier.kt
│       │   └── scheduler/
│       │       └── SyncTaskScheduler.kt
│       └── api/
│           ├── ChannelApi.kt
│           ├── ChannelWebhookApi.kt
│           ├── SyncDashboardApi.kt
│           └── dto/
│               ├── CreateChannelRequest.kt
│               ├── UpdateChannelRequest.kt
│               ├── ChannelResponse.kt
│               ├── CreateMappingRequest.kt
│               ├── ChannelMappingResponse.kt
│               ├── WebhookEvent.kt
│               ├── SyncDashboardResponse.kt
│               └── SyncTaskResponse.kt
│
└── stayops-mock-ota/         # Mock OTA 서버 (별도 모듈)
    └── src/main/kotlin/com/stayops/mockota/
        ├── MockOtaApplication.kt
        ├── api/
        │   ├── AriReceiverApi.kt
        │   └── SimulationApi.kt
        ├── service/
        │   ├── WebhookSenderService.kt
        │   └── FailureSimulatorService.kt
        └── model/
            ├── MockBooking.kt
            └── ReceivedAri.kt
```

---

## Sub-steps

### Phase 7-1: Channel 도메인 모델 + 단위 테스트

**생성할 파일:**
```
channel/domain/model/Channel.kt
channel/domain/model/ChannelType.kt
channel/domain/model/ChannelStatus.kt
channel/domain/model/ChannelConnectionConfig.kt
```

**Channel 도메인 모델:**
```kotlin
@ConsistentCopyVisibility
data class Channel private constructor(
    val id: String,
    val propertyId: String,
    val code: String,
    val name: String,
    val type: ChannelType,
    val commissionRate: BigDecimal,
    val connectionConfig: ChannelConnectionConfig,
    val status: ChannelStatus,
    val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

**ChannelConnectionConfig (VO):**
```kotlin
data class ChannelConnectionConfig(
    val apiEndpoint: String?,
    val apiKey: String?,
    val apiSecret: String?,
    val webhookSecret: String?
)
```

**비즈니스 규칙:**
- `code`는 propertyId 내에서 유니크
- DIRECT 채널: commissionRate = 0, connectionConfig는 빈 값
- OTA 채널: commissionRate > 0, apiEndpoint·webhookSecret 필수
- `activate()`, `deactivate()`, `suspend()` 상태 전이

**테스트 (Kotest BehaviorSpec):**
- DIRECT/OTA 채널 생성 검증
- OTA 채널 필수 필드 누락 시 예외
- 상태 전이 성공/실패 케이스

---

### Phase 7-2: Channel Repository + MongoDB 통합 테스트

**생성할 파일:**
```
channel/domain/repository/ChannelRepository.kt
channel/infrastructure/persistence/ChannelDocument.kt
channel/infrastructure/persistence/MongoChannelRepository.kt
```

**MongoDB 인덱스:**
- `{ propertyId: 1, code: 1 }` (unique)
- `{ propertyId: 1, status: 1 }`

**테스트 (JUnit5 + Testcontainers):**
- Save/Find/Delete CRUD 동작 검증
- 유니크 제약 조건 검증

---

### Phase 7-3: ChannelMapping 도메인 + Repository

**생성할 파일:**
```
channel/domain/model/ChannelMapping.kt
channel/domain/model/MappingEntry.kt
channel/domain/model/MappingType.kt
channel/domain/repository/ChannelMappingRepository.kt
channel/infrastructure/persistence/ChannelMappingDocument.kt
channel/infrastructure/persistence/MongoChannelMappingRepository.kt
```

**ChannelMapping 도메인:**
```kotlin
@ConsistentCopyVisibility
data class ChannelMapping private constructor(
    val id: String,
    val propertyId: String,
    val channelCode: String,
    val mappings: List<MappingEntry>,
    val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun addMapping(entry: MappingEntry): ChannelMapping
    fun removeMapping(internalId: String, type: MappingType): ChannelMapping
    fun findExternalCode(internalId: String, type: MappingType): String?
    fun findInternalId(externalCode: String, type: MappingType): String?
}
```

**비즈니스 규칙:**
- `{propertyId, channelCode}` 단위로 1개의 ChannelMapping
- 동일 `{internalId, type}` 중복 매핑 불가
- 역방향 조회도 유니크 보장

**MongoDB 인덱스:** `{ propertyId: 1, channelCode: 1 }` (unique)

**테스트:**
- 단위: 매핑 추가/제거, 양방향 조회, 중복 방지
- 통합: MongoDB 저장/조회

---

### Phase 7-4: SyncTask (Outbox) + AvailabilityPayload

**생성할 파일:**
```
channel/domain/model/SyncTask.kt
channel/domain/model/SyncTaskStatus.kt
channel/domain/model/SyncTaskType.kt
channel/domain/repository/SyncTaskRepository.kt
channel/application/dto/AvailabilityPayload.kt
channel/infrastructure/persistence/SyncTaskDocument.kt
channel/infrastructure/persistence/MongoSyncTaskRepository.kt
```

**SyncTask 도메인:**
```kotlin
@ConsistentCopyVisibility
data class SyncTask private constructor(
    val id: String,
    val propertyId: String,
    val channelCode: String,
    val type: SyncTaskType,
    val payload: Map<String, Any>,
    val idempotencyKey: String,
    val status: SyncTaskStatus,
    val retryCount: Int,
    val maxRetries: Int,
    val nextRetryAt: Instant?,
    val lastError: String?,
    val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun startProcessing(): SyncTask
    fun complete(): SyncTask
    fun fail(errorMessage: String): SyncTask
    fun retry(): SyncTask
}
```

**MongoDB 인덱스:**
- `{ status: 1, nextRetryAt: 1 }` — 스케줄러 폴링 쿼리
- `{ propertyId: 1, channelCode: 1, status: 1 }`

**테스트:**
- 단위: SyncTask 상태 머신, 지수 백오프 계산, 멱등성 키 생성
- 단위: AvailabilityPayload 직렬화/역직렬화
- 통합: MongoDB 저장/조회, nextRetryAt 기반 쿼리

---

### Phase 7-5: Mock OTA 모듈 + ChannelAdapter + Registry + 스케줄러

**생성할 파일:**
```
# Mock OTA 모듈
stayops-mock-ota/build.gradle.kts
stayops-mock-ota/src/main/kotlin/.../MockOtaApplication.kt
stayops-mock-ota/src/main/kotlin/.../api/AriReceiverApi.kt
stayops-mock-ota/src/main/kotlin/.../api/SimulationApi.kt
stayops-mock-ota/src/main/kotlin/.../service/WebhookSenderService.kt
stayops-mock-ota/src/main/kotlin/.../service/FailureSimulatorService.kt

# PMS 측
channel/domain/service/ChannelSyncAdapter.kt
channel/infrastructure/external/HttpChannelSyncAdapter.kt
channel/infrastructure/external/ChannelAdapterRegistry.kt
channel/infrastructure/scheduler/SyncTaskScheduler.kt
channel/application/service/ChannelSyncApplication.kt
```

**테스트:**
- 단위: ChannelSyncApplication 태스크 생성·처리 (MockK)
- 통합: PMS → Mock OTA 간 실제 HTTP ARI push E2E

---

### Phase 7-6: Webhook 수신 + HMAC 검증 + 이벤트 중복 제거

**생성할 파일:**
```
channel/api/ChannelWebhookApi.kt
channel/api/dto/WebhookEvent.kt
channel/application/service/WebhookApplication.kt
channel/domain/model/ProcessedWebhookEvent.kt
channel/domain/repository/ProcessedWebhookEventRepository.kt
channel/infrastructure/persistence/ProcessedWebhookEventDocument.kt
channel/infrastructure/persistence/MongoProcessedWebhookEventRepository.kt
channel/infrastructure/webhook/HmacSignatureVerifier.kt
```

**테스트:**
- 단위: HmacSignatureVerifier 유효/무효 서명 검증
- 단위: WebhookApplication 중복 제거, 매핑 변환 (MockK)
- E2E: Mock OTA → PMS webhook 호출 → 서명 검증 → 처리 확인

---

### Phase 7-7: Channel CRUD API + Mapping API

**생성할 파일:**
```
channel/application/service/ChannelApplication.kt
channel/api/ChannelApi.kt
channel/api/dto/CreateChannelRequest.kt
channel/api/dto/UpdateChannelRequest.kt
channel/api/dto/ChannelResponse.kt
channel/api/dto/CreateMappingRequest.kt
channel/api/dto/ChannelMappingResponse.kt
```

**테스트:**
- 단위: ChannelApplication (MockK)
- E2E: 채널 CRUD + 매핑 관리 전체 흐름 (Testcontainers)

---

### Phase 7-8: Sync Dashboard + SyncTask 관리 API

**생성할 파일:**
```
channel/application/service/SyncDashboardApplication.kt
channel/api/SyncDashboardApi.kt
channel/api/dto/SyncDashboardResponse.kt
channel/api/dto/ChannelSyncStatusResponse.kt
channel/api/dto/SyncTaskResponse.kt
```

**테스트:**
- 단위: 대시보드 집계 로직 (MockK)
- E2E: 대시보드 조회 + 수동 재시도 흐름 (Testcontainers)

---

## Phase 8 연동 포인트

1. `ChannelSyncApplication.createAvailabilitySyncTasks()` — 예약 생성/취소 시 호출
2. `WebhookApplication` — OTA 예약 수신 후 코드 변환, Phase 8 ReservationService에 위임
3. `ChannelMapping.findInternalId()` — OTA 코드 → PMS roomTypeId 변환

---

## 검증 기준

- [ ] Channel 도메인 단위 테스트 통과
- [ ] ChannelMapping 양방향 매핑 단위 테스트 통과
- [ ] SyncTask Outbox + 지수 백오프 단위 테스트 통과
- [ ] PMS → Mock OTA ARI Push E2E 테스트 통과
- [ ] Mock OTA → PMS Webhook 수신 E2E 테스트 통과
- [ ] HMAC 서명 검증 + 이벤트 중복 제거 테스트 통과
- [ ] Channel CRUD + Mapping API E2E 테스트 통과
- [ ] Sync Dashboard 조회 테스트 통과
- [ ] `./gradlew test` 전체 통과
- [ ] 시연 시나리오: Mock OTA에서 예약 → PMS 재고 차감 → 다른 OTA에 ARI 전파

---

## 기존 코드 처리

기존 커밋(7-1: `35c4198`, 7-2: `cc6c392`)과 미커밋 7-3 코드를 폐기하고 새로 작성한다.
`feature/phase-7-channel` 브랜치를 main에서 새로 분기하거나, 기존 파일을 삭제 후 재작성한다.
