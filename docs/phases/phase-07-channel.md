# Phase 7: Channel (판매 채널 관리) — CMS

판매 채널(DIRECT/OTA) 등록, Outbox 패턴 기반 재고 동기화, 가상 채널 어댑터.

---

## Sub-steps

### Phase 7-1: Channel 도메인 모델 + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/channel/domain/model/
├── Channel.kt
├── ChannelType.kt            # enum: DIRECT, OTA
├── ChannelStatus.kt          # enum: ACTIVE, INACTIVE, SUSPENDED
└── ChannelConfig.kt          # VO (수수료율, webhook URL 등)

src/test/kotlin/com/stayops/channel/domain/model/
└── ChannelTest.kt
```

**Channel 도메인 모델:**
```kotlin
data class Channel(
    override val id: String,
    val propertyId: String,
    val code: String,               // "FINESTAY", "AGODA", "AIRBNB" 등
    val name: String,               // 표시명
    val type: ChannelType,
    val config: ChannelConfig,
    val status: ChannelStatus,
    override val version: Long = 0,
    override val createdAt: Instant,
    override val updatedAt: Instant
) : AggregateRoot()
```

**ChannelConfig (VO):**
```kotlin
data class ChannelConfig(
    val commissionRate: BigDecimal,       // 0.15 = 15%, FINESTAY(DIRECT)는 0
    val webhookUrl: String? = null        // OTA → PMS webhook 수신 URL
)
```

**비즈니스 규칙:**
- `code`는 propertyId 내에서 유니크
- FINESTAY 채널: commissionRate = 0, type = DIRECT, 숙소 생성 시 자동 등록
- OTA 채널: commissionRate > 0, webhookUrl 필수
- `activate()`, `suspend()`, `deactivate()` 상태 전이 메서드

**TDD 순서:**
1. RED: Channel 생성 테스트 (DIRECT, OTA)
2. GREEN: Channel 팩토리 구현
3. RED: 상태 전이 성공/실패 테스트
4. GREEN: 상태 전이 메서드 구현
5. RED: FINESTAY 채널 commissionRate=0 불변식 테스트
6. GREEN: 불변식 검증 구현
7. REFACTOR

---

### Phase 7-2: Repository + MongoDB 구현 + 통합 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/channel/domain/repository/
└── ChannelRepository.kt

src/main/kotlin/com/stayops/channel/infrastructure/persistence/
├── ChannelDocument.kt
└── MongoChannelRepository.kt

src/test/kotlin/com/stayops/channel/infrastructure/persistence/
└── MongoChannelRepositoryTest.kt
```

**Repository 인터페이스:**
```kotlin
interface ChannelRepository {
    fun save(channel: Channel): Channel
    fun findById(id: String): Channel?
    fun findByPropertyId(propertyId: String): List<Channel>
    fun findByPropertyIdAndCode(propertyId: String, code: String): Channel?
    fun findByPropertyIdAndStatus(propertyId: String, status: ChannelStatus): List<Channel>
    fun deleteById(id: String)
}
```

**MongoDB 인덱스:**
- `{ propertyId: 1, code: 1 }` (unique)
- `{ propertyId: 1, status: 1 }`

---

### Phase 7-3: Outbox 패턴 — SyncTask 도메인 + ChannelSyncAdapter + 스케줄러

**생성할 파일:**
```
src/main/kotlin/com/stayops/channel/domain/model/
├── SyncTask.kt
├── SyncTaskStatus.kt         # enum: PENDING, IN_PROGRESS, COMPLETED, FAILED
└── SyncTaskType.kt           # enum: INVENTORY_UPDATE, RESERVATION_SYNC

src/main/kotlin/com/stayops/channel/domain/repository/
└── SyncTaskRepository.kt

src/main/kotlin/com/stayops/channel/domain/service/
└── ChannelSyncAdapter.kt     # 인터페이스 (Dependency Inversion)

src/main/kotlin/com/stayops/channel/infrastructure/persistence/
├── SyncTaskDocument.kt
└── MongoSyncTaskRepository.kt

src/main/kotlin/com/stayops/channel/infrastructure/external/
└── VirtualChannelSyncAdapter.kt    # 가상 어댑터 구현

src/main/kotlin/com/stayops/channel/application/service/
└── ChannelSyncService.kt

src/main/kotlin/com/stayops/channel/infrastructure/scheduler/
└── SyncTaskScheduler.kt

src/test/kotlin/com/stayops/channel/domain/model/
└── SyncTaskTest.kt

src/test/kotlin/com/stayops/channel/infrastructure/external/
└── VirtualChannelSyncAdapterTest.kt

src/test/kotlin/com/stayops/channel/application/service/
└── ChannelSyncServiceTest.kt
```

**SyncTask 도메인 모델:**
```kotlin
data class SyncTask(
    override val id: String,
    val propertyId: String,
    val channelCode: String,
    val type: SyncTaskType,
    val payload: Map<String, Any>,      // 동기화할 데이터 (재고 변경, 예약 정보 등)
    val status: SyncTaskStatus,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val lastError: String? = null,
    override val version: Long = 0,
    override val createdAt: Instant,
    override val updatedAt: Instant
) : AggregateRoot()
```

**ChannelSyncAdapter (도메인 서비스 인터페이스):**
```kotlin
interface ChannelSyncAdapter {
    fun pushInventoryUpdate(channel: Channel, payload: Map<String, Any>): SyncResult
}

data class SyncResult(val success: Boolean, val errorMessage: String? = null)
```

**VirtualChannelSyncAdapter (가상 구현):**
```kotlin
@Component
class VirtualChannelSyncAdapter : ChannelSyncAdapter {
    // ~80% 성공, ~20% 랜덤 실패 (재시도 로직 시연용)
    // 실패 시 "Connection timeout", "Rate limit exceeded" 등 현실적 에러 메시지
    // 200-500ms 딜레이로 네트워크 지연 시뮬레이션
}
```

> 실제 프로덕션에서는 `AgodaSyncAdapter`, `AirbnbSyncAdapter` 등 채널별 구현체를 추가하면 된다. Dependency Inversion 원칙에 따라 도메인은 인터페이스만 의존한다.

**Outbox 패턴 흐름:**
```
FineStay 예약 생성 (같은 트랜잭션)
├── Reservation 저장
├── Inventory 차감
└── SyncTask(PENDING) 저장 — 해당 Property의 모든 활성 OTA 채널 대상

SyncTaskScheduler (별도 프로세스, 주기적 폴링)
├── PENDING 태스크 조회
├── ChannelSyncAdapter.pushInventoryUpdate() 호출
│   ├── 성공 → COMPLETED
│   └── 실패 → retryCount++ → 다음 폴링에서 재시도
└── maxRetries(3) 초과 → FAILED (관리자 확인 필요)
```

**TDD 순서:**
1. RED: SyncTask 생성/상태 전이 테스트
2. GREEN: SyncTask 구현
3. RED: ChannelSyncAdapter 인터페이스 계약 테스트
4. GREEN: VirtualChannelSyncAdapter 구현 (랜덤 성공/실패)
5. RED: ChannelSyncService — 재고 변경 시 SyncTask 생성 테스트
6. GREEN: SyncTask 생성 로직 구현
7. RED: SyncTaskScheduler — PENDING 태스크 처리 + 재시도 테스트
8. GREEN: 스케줄러 구현
9. REFACTOR

---

### Phase 7-4: Webhook 수신 핸들러

**생성할 파일:**
```
src/main/kotlin/com/stayops/channel/api/
├── ChannelWebhookController.kt
└── dto/
    ├── OtaBookingWebhookRequest.kt
    └── OtaCancellationWebhookRequest.kt

src/main/kotlin/com/stayops/channel/application/service/
└── WebhookHandlerService.kt

src/test/kotlin/com/stayops/channel/application/service/
└── WebhookHandlerServiceTest.kt

src/test/kotlin/com/stayops/channel/api/
└── ChannelWebhookControllerTest.kt
```

**Webhook 엔드포인트:**
```
POST   /api/v1/properties/{pid}/channels/webhook/{channelCode}
```

**Webhook 처리 플로우:**
1. channelCode로 Channel 조회
2. 서명 검증 (HMAC)
3. Webhook 타입 분기 (booking / cancellation)
4. Reservation 생성 또는 취소 (ReservationService 위임)

> 실제 OTA 연동 없이도 Swagger UI 또는 curl로 webhook 엔드포인트를 직접 호출하여 테스트할 수 있다.

---

### Phase 7-5: ChannelService + Channel CRUD API + Sync Dashboard

**생성할 파일:**
```
src/main/kotlin/com/stayops/channel/application/service/
└── ChannelService.kt

src/main/kotlin/com/stayops/channel/api/
├── ChannelController.kt
└── dto/
    ├── CreateChannelRequest.kt
    ├── UpdateChannelRequest.kt
    ├── ChannelResponse.kt
    ├── ChannelSyncDashboardResponse.kt
    └── ChannelSyncStatusResponse.kt

src/test/kotlin/com/stayops/channel/application/service/
└── ChannelServiceTest.kt

src/test/kotlin/com/stayops/channel/api/
└── ChannelControllerTest.kt
```

**API 엔드포인트:**
```
POST   /api/v1/properties/{pid}/channels
GET    /api/v1/properties/{pid}/channels
GET    /api/v1/properties/{pid}/channels/{id}
PUT    /api/v1/properties/{pid}/channels/{id}
DELETE /api/v1/properties/{pid}/channels/{id}
```

**SyncTask 관리 API:**
```
GET    /api/v1/properties/{pid}/sync-tasks              (params: status, channelCode)
POST   /api/v1/properties/{pid}/sync-tasks/{id}/retry
```

**Sync Dashboard API:**
```
GET    /api/v1/properties/{pid}/channels/sync-dashboard
```

**ChannelSyncDashboardResponse:**
```kotlin
data class ChannelSyncDashboardResponse(
    val channels: List<ChannelSyncStatusResponse>
)

data class ChannelSyncStatusResponse(
    val channelCode: String,
    val channelName: String,
    val pendingCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val lastSyncAt: Instant?,
    val lastError: String?
)
```

> 관리자는 이 대시보드로 "PMS가 각 채널에 반영했다고 믿는 채널별 재고 상태와 동기화 결과"를 확인한다.

---

## 검증 기준

- [ ] Channel 도메인 단위 테스트 통과 (DIRECT/OTA 생성, 상태 전이)
- [ ] SyncTask Outbox 패턴 단위 테스트 통과
- [ ] VirtualChannelSyncAdapter 재시도 로직 테스트 통과
- [ ] ChannelSyncService 통합 테스트 통과 (SyncTask 생성 → 처리)
- [ ] Webhook 수신 → 예약 생성 E2E 테스트 통과
- [ ] Channel CRUD API E2E 테스트 통과
- [ ] Sync Dashboard 조회 테스트 통과
- [ ] `./gradlew test` 전체 통과
