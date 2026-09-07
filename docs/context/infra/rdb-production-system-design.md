# StayOps 시스템 디자인 — RDB(PostgreSQL) Production 기준

> 작성일: 2026-08-30
> 대상 프로필: `prod,rdb` (PostgreSQL 17 + jOOQ + Flyway)
> 목적: 면접에서 "이 시스템은 어떻게 생겼고, 요청 하나가 들어오면 내부에서 무슨 일이 순서대로 일어나는가"를 세부까지 설명하기 위한 정리

---

## 0. 30초 요약

StayOps는 숙소 예약·결제·재고를 관리하는 PMS이며, 채널매니저(CMS) 역할까지 한 서버에서 수행한다.
핵심 설계 문제는 **"돈은 외부(PG)에서 승인되고, 재고와 예약은 내부 DB에 있다"**는 비대칭이다.
이 비대칭을 다루기 위해 예약을 `ReservationIntent → Payment → InventoryHold → Reservation` 4개 개념으로 쪼개고,
PG 호출을 API 요청 스레드에서 분리해 **Payment Outbox 워커**가 처리하도록 했다.

RDB 프로필에서는 이 정합성을 애플리케이션 코드가 아니라 **PostgreSQL의 제약(unique index, CHECK, FK)과 잠금 기능(낙관적 락 CAS, `FOR UPDATE SKIP LOCKED`)** 이 최종 보증한다.
같은 도메인 로직을 MongoDB 프로필로도 돌릴 수 있는데, 이 두 구현의 차이가 이 프로젝트에서 가장 설명거리가 많은 지점이다.

---

## 1. 배포 토폴로지 (AWS, Terraform 관리)

`infra/terraform/prod`의 `deployment_topology = "production"`이 만드는 것.

```text
                       Internet
                          │
                    Route 53 (public)
                          │
              ┌───────────▼───────────┐
              │   ALB (public subnet) │  TLS 종료 / TLS1.3
              │   :80 → :443 redirect │  /actuator/prometheus → 404 고정응답
              └───────────┬───────────┘
                          │  HTTP :8080  (health check: /actuator/health)
        ┌─────────────────┴─────────────────┐
        │                                   │
┌───────▼────────┐                 ┌────────▼───────┐      private subnet
│  App EC2 #1    │                 │  App EC2 #2    │
│  docker: app   │                 │  docker: app   │
└───┬────────┬───┘                 └───┬────────┬───┘
    │        │                         │        │
    │        └───────────┬─────────────┘        │
    │                    │                      │
┌───▼──────────┐  ┌──────▼───────┐      ┌───────▼────────┐
│ RDS Postgres │  │  Redis EC2   │      │  Mock OTA EC2  │
│ Multi-AZ     │  │  (단일 노드)  │      │  (+ nginx)     │
└──────────────┘  └──────────────┘      └────────────────┘
                          │
                 ┌────────▼─────────┐
                 │ Observability EC2│  Prometheus / Loki / Grafana
                 └──────────────────┘
                          ▲
                          │  NAT Gateway (아웃바운드 전용: Toss API, 패치)
```

**구성 근거를 묻는다면**

| 요소 | 선택 | 이유 |
|---|---|---|
| App | EC2 2대 + ALB | 무중단 배포와 롤링 교체의 최소 단위. 인스턴스 1대는 배포 중 전체 다운 |
| PostgreSQL | **RDS Multi-AZ** | 동기 스탠바이 + 자동 failover. MongoDB는 EC2 3대 replica set으로 직접 운영하지만, RDB는 관리형이 운영 비용상 유리 |
| Redis | EC2 1대 | 세션·rate limit·재고 캐시용. **현재 SPOF이고, 이건 의도된 타협** (§9) |
| 접속 | Session Manager | Bastion/22번 포트 없음. 키 관리 부담 제거 |
| Secret | RDS managed password (Secrets Manager) + SSM Parameter Store | tfvars/state/user_data에 비밀번호를 남기지 않음 |

**네트워크 경계**: App/DB/Redis는 전부 private subnet. 인바운드는 ALB → App(8080)만 열려 있고, App → RDS(5432), App → Redis(6379)는 Security Group 참조로만 허용된다. Toss API 호출 등 아웃바운드는 NAT Gateway를 통해서만 나간다.

**배포 파이프라인**

```text
main push
  → GitHub Actions: 테스트 → 이미지 빌드 → GHCR push
  → 역할별 compose/config bundle 생성 → S3 artifact bucket 업로드
  → SSM Run Command (SSH 없음)
  → EC2: bundle 다운로드 → Parameter Store에서 /stayops/prod/<role>/ 조회 → .env 생성
        → docker compose pull && up -d
```

인프라(Terraform)와 애플리케이션 배포(compose)를 의도적으로 분리했다. 앱 코드 한 줄 바꿀 때마다 `terraform apply`가 도는 구조는 인프라 변경과 배포 실패의 blast radius가 섞인다.

**관측**: 앱은 Micrometer로 `/actuator/prometheus`를 노출하고, Prometheus가 private 네트워크에서 스크랩한다. 이 엔드포인트는 Spring Security에서 `permitAll`이지만 **ALB 리스너 룰이 외부 요청을 404로 잘라낸다** — 내부 스크랩은 되고 외부 노출은 안 되는 상태. 로그는 Promtail이 Docker 로그를 수집해 Loki로 보내고 Grafana에서 조회한다.

---

## 2. 애플리케이션 런타임 구조

- Kotlin 2.2.21 / JDK 24 / Spring Boot 4.0.3 / Embedded Tomcat 11 (동기 MVC, 가상 스레드 미사용)
- 패키징은 **도메인별 수직 슬라이스 + 내부 레이어**

```text
com.stayops.<도메인>/
├── api/              HTTP 진입, DTO 변환만
├── application/      유스케이스 조합, @Transactional 경계
│   ├── provided/     이 모듈이 다른 모듈에 제공하는 포트 (예: InventoryHoldService)
│   └── required/     이 모듈이 외부에 요구하는 포트 (예: PaymentGateway)
├── domain/           프레임워크 의존 없는 모델 + repository 인터페이스
└── infrastructure/
    ├── persistence/rdb/    jOOQ 구현  (@RdbPersistence = @Profile("rdb"))
    ├── persistence/mongo/  Mongo 구현 (@MongoPersistence = @Profile("mongo"))
    ├── external/           Toss / OTA HTTP 어댑터
    └── scheduler/          @Scheduled 진입점
```

도메인: `member, property, room, inventory, rate, channel, reservation, payment, guest, settlement, statistics, dashboard, shared`

### 2-1. 왜 JPA가 아니라 jOOQ인가

1. **도메인 순수성**: 도메인 모델에 `@Entity`를 붙이지 않겠다는 원칙을 지키려면 ORM 매핑 계층이 따로 필요하다. jOOQ는 record ↔ 도메인 변환을 리포지토리 구현이 명시적으로 하므로 도메인이 영속성을 모른다.
2. **정합성 장치를 직접 쓰기 위해**: 이 시스템의 핵심은 `FOR UPDATE SKIP LOCKED`, 부분 인덱스 조건, `ON CONFLICT DO NOTHING/DO UPDATE ... WHERE version = ?` 같은 PostgreSQL 기능이다. JPA에서 이걸 다루려면 결국 native query로 내려간다.
3. **낙관적 락을 내 손으로**: `@Version` 대신 UPDATE의 WHERE 절에 version을 직접 넣고 `affectedRows == 0`을 충돌로 해석한다. 언제 어떤 SQL이 나가는지 코드에 그대로 보인다.

### 2-2. 스키마와 코드 생성 순서

```text
db/migration/V*.sql  (Flyway, 단일 진실 원본)
      │
      ├── 빌드 타임: ./gradlew generateJooq
      │     jOOQ DDLDatabase가 마이그레이션 SQL을 파싱 → com.stayops.jooq.generated.Tables 생성
      │     (실행 중인 DB에 붙지 않는다 → CI에서 DB 없이 컴파일 가능)
      │
      └── 런타임: 앱 기동 시 Flyway가 RDS에 마이그레이션 적용
```

`compileKotlin`이 `generateJooq`에 의존하므로 마이그레이션에 없는 컬럼을 코드에서 참조하면 **컴파일이 깨진다.** 스키마-코드 드리프트를 빌드가 잡아준다.

현재 마이그레이션은 V1~V7이고, 스키마 진화 자체가 설계 히스토리다.

| 버전 | 내용 | 배경 |
|---|---|---|
| V1 | 기본 스키마 (members ~ scheduler_locks) | 예약이 곧 최종 예약이던 시절 |
| V2 | `reservation_intents`, `inventory_holds`, `room_inventories.held_count` | "결제 전 예약"과 "확정 예약"을 분리 |
| V3/V4 | payments·outbox가 intent를 참조 가능하게 | 결제가 최종 예약이 아니라 intent에 붙음 |
| V5 | unique/부분 인덱스 + `CHECK` 제약 | 정합성 보증을 앱에서 DB로 내림 |
| V6 | `reservation_intent_requests` | 클라이언트 Idempotency-Key |
| V7 | `active_reservation_attempts` | 같은 사람이 같은 조건으로 동시에 여러 intent를 만드는 것 차단 |

---

## 3. 요청 하나가 처리되는 순서 (HTTP 진입 → 응답)

`POST /api/v1/customer/reservation-intents` 기준.

```text
1. Route 53 → ALB:443
     TLS 종료. X-Forwarded-For / X-Forwarded-Proto 부착
     (앱은 server.forward-headers-strategy=framework 로 원 클라이언트 IP·스킴 복원)

2. ALB → 정상(healthy) 타깃만 선택
     health check: GET /actuator/health, 10초 간격, 2회 연속 성공/실패로 상태 전환
     신규 타깃은 slow_start 30초 동안 트래픽을 점진 수용

3. App EC2 → Docker bridge → Tomcat :8080

4. 서블릿 필터 체인
   a. SecurityHeadersFilter        CSP, Referrer-Policy, X-Content-Type-Options 부착
   b. Spring Security FilterChain
        - Redis 세션에서 SecurityContext 복원 (spring-session-data-redis, TTL 30분)
          prod 쿠키: SameSite=None; Secure  (클라이언트가 다른 오리진이므로)
        - CSRF 검증 (webhook 경로는 ignoringRequestMatchers로 제외)
        - authorizeHttpRequests: /api/v1/customer/reservation-intents/** = authenticated
   c. RateLimitFilter (@Order(LOWEST_PRECEDENCE - 20) → Security 이후에 실행)
        - 인증이 끝난 뒤라야 MEMBER 식별자를 쓸 수 있어서 순서가 이렇다
        - 규칙 매칭 → Redis fixed window
        - 초과 시 429 + Retry-After, 통과/차단/장애를 Micrometer 카운터로 기록

5. DispatcherServlet → CustomerReservationIntentApi
6. Application 계층에서 @Transactional 시작 → 도메인 호출 → jOOQ DSLContext
7. HikariCP 커넥션 → RDS PostgreSQL
8. 응답 201 + 세션 쿠키
```

### 3-1. Rate limit 내부 동작

```kotlin
// RedisFixedWindowRateLimiter — Lua 스크립트 1회 왕복
local current = redis.call('INCR', KEYS[1])
if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
return current
```

- 키: `rate:<ruleId>:<sha256(identity)>:<windowStart>` — 식별자(IP/memberId)를 해시해 저장하므로 Redis에 원문 IP가 남지 않는다.
- 윈도우는 `epochSecond / windowSeconds * windowSeconds`로 계산하는 고정 윈도우. 경계에서 최대 2배 버스트를 허용하는 알려진 단점이 있으나, 로그인·회원가입 남용 차단이 목적이라 슬라이딩 윈도우 비용을 지불하지 않았다.
- **fail-open**: Redis가 죽으면 예외를 잡아 `fallback` 카운터를 올리고 요청을 통과시킨다. rate limiter는 보조 장치이고, 이것 때문에 서비스 전체가 죽으면 안 된다는 판단.
- 현재 기본값은 `enabled: false`. 부하 실험에서 회원가입 규칙(IP당 600초 5회)이 테스트 셋업 단계를 막아버려 기본 비활성으로 두고 환경변수로 켠다.

---

## 4. 핵심 흐름 ①: 고객 직접 예약 + 결제 (가장 중요한 부분)

### 왜 예약을 4개로 쪼갰나

"결제창을 띄운 순간의 예약"과 "돈이 승인된 예약"은 같은 것이 아니다. 하나의 `Reservation` 행에 상태값으로만 표현하면 이런 질문에 답할 수 없다.

- 결제 대기 중인 예약이 재고를 점유하는가? 점유한다면 실패했을 때 누가 되돌리는가?
- PG는 승인했는데 우리 DB 쓰기가 실패하면 그 예약은 무슨 상태인가?
- OTA에서 넘어온 "이미 결제 끝난 예약"도 같은 상태 기계를 타야 하는가?

그래서 다음처럼 분리했다.

| 개념 | 테이블 | 책임 |
|---|---|---|
| `ReservationIntent` | `reservation_intents` | 결제 성사 전의 예약 시도. TTL 15분 |
| `Payment` | `payments` | PG 승인 상태 전이 |
| `InventoryHold` | `inventory_holds` + `inventory_hold_dates` | 날짜별 임시 재고 점유 |
| `Reservation` | `reservations` | **결제가 승인된 뒤에만 생성되는** 확정 예약 |

`Reservation` 행의 존재 자체가 "돈을 받았다"의 증거가 된다.

---

### Step 1. 예약 intent 생성 — `POST /reservation-intents` (헤더 `Idempotency-Key` 필수)

`CustomerReservationIntentIdempotencyApplication` → `CustomerReservationIntentCreationApplication`

**1-a. 멱등 처리 (트랜잭션 밖)**

```text
reservation_intent_requests 조회 (PK: member_id + idempotency_key)
  ├─ 있음 → request_hash 비교
  │           일치   → 기존 intent + payment 그대로 반환 (resolution=replayed)
  │           불일치 → 409 IDEMPOTENCY_KEY_REUSED  (같은 키로 다른 예약 금지)
  └─ 없음 → 아래 생성 트랜잭션 진입
```

`request_hash`는 요청 필드를 `길이:값` 형태로 이어붙여 SHA-256한 값이다. 단순 연결이면 `("ab","c")`와 `("a","bc")`가 같은 해시가 되므로 길이 프리픽스를 붙였다.

**1-b. 생성 트랜잭션** (`@Transactional` 1개)

```text
① 중복 예약 검사
   reservations에 (member_id, room_type_id, check_in, check_out)이 CONFIRMED이거나
   만료되지 않은 PENDING으로 존재하면 409 DUPLICATE_RESERVATION

② 숙소/객실타입 조회, DIRECT 채널 확보(없으면 생성)

③ 요금 계산  RateResolverService
   활성 RatePlan들을 우선순위·요일 규칙·기간으로 해석 → 숙박일 합계
   ReservationPricing.calculate()가 수수료/순수익까지 계산

④ 재고 가용성 사전 검증
   room_inventories를 체크인 ~ 체크아웃-1일 범위로 조회
   available_count(= total - reserved - blocked - held)가 0 이하인 날이 있으면 400 ROOM_NOT_AVAILABLE

⑤ reservation_intents INSERT        (status=PAYMENT_WAITING, expires_at=now+15분)

⑥ reservation_intent_requests INSERT
   PK 충돌 → ReservationIntentRequestAlreadyExistsException
   → 상위에서 "같은 키의 동시 요청"으로 해석하고 짧게 재조회 (최대 5회, 10ms씩 증가)

⑦ active_reservation_attempts INSERT ... ON CONFLICT (booking_key) DO NOTHING
   booking_key = sha256(memberId + propertyId + roomTypeId + checkIn + checkOut)
   affectedRows == 0 → 409 DUPLICATE_ACTIVE_RESERVATION_ATTEMPT
   → 같은 사람이 같은 조건으로 여러 결제창을 동시에 여는 것을 DB 유니크 제약으로 막는다

⑧ InventoryHold 생성 + 날짜별 재고 점유
   숙박 날짜마다:
     room_inventories 조회(Redis 캐시 → miss 시 DB)
     도메인 hold() 호출 → heldCount + 1
     UPDATE room_inventories SET held_count=?, version=version+1
       WHERE id=? AND version=?        ← 낙관적 락 CAS
     affectedRows == 0 → 409 INVENTORY_CONFLICT
     성공 시 해당 캐시 키 evict
   inventory_holds + inventory_hold_dates INSERT (status=HELD, TTL은 intent와 동일)

⑨ payments INSERT (status=PENDING, orderId 발급, reservation_id는 NULL)

⑩ 구조화 로그: event=reservation_intent_created ...
```

응답 201에 `orderId`, `amount`, `expiresAt`이 실려 나가고, 클라이언트는 이 값으로 Toss 결제창을 띄운다.

> **핵심 포인트**: 이 트랜잭션이 끝나는 순간 재고는 `held_count`로 이미 잠겨 있다. 즉 **결제창을 여는 순간부터 남이 그 방을 못 산다.** 대신 15분 안에 결제가 끝나지 않으면 만료 스케줄러가 회수한다.

---

### Step 2. 결제 승인 요청 — `POST /reservation-intents/{id}/confirm-payment` → **202 Accepted**

사용자가 Toss 결제창에서 결제를 마치면 `paymentKey`를 들고 우리 서버로 돌아온다. 여기서 **PG를 직접 호출하지 않는다.**

```text
소유자 검증 (intent.memberId == 로그인 회원)
상태 가드
  ├─ 이미 RESERVED / APPROVED   → 202 그대로 반환 (event=payment_confirm_ignored reason=already_finalized)
  ├─ FAILED / EXPIRED / CANCELLED → 202 그대로 반환 (reason=already_terminated)
  └─ now > expires_at            → 400 RESERVATION_INTENT_EXPIRED
intent  → CONFIRM_REQUESTED
payment → CONFIRM_REQUESTED (paymentKey 기록)
payment_outbox_messages INSERT
    type=CONFIRM_PAYMENT, status=PENDING, idempotency_key(unique), max_retries
응답 202
```

**왜 API 스레드에서 PG를 호출하지 않는가** — 면접에서 가장 자주 파고드는 지점.

1. PG 호출은 수백 ms~수 초가 걸리는 외부 I/O다. 톰캣 스레드를 그동안 잡고 있으면 PG 지연이 곧 우리 서비스의 스레드 고갈이 된다.
2. **"승인은 성공했는데 응답을 못 받은" 경우**를 API 스레드에서 복구할 방법이 없다. 사용자는 이미 떠났고 재시도 주체가 사라진다.
3. Outbox 행으로 남겨두면 재시도 주체가 DB에 남는다. 앱이 죽어도 다음 워커가 인계한다.

세 가지 상태 가드가 전부 202를 반환하는 이유는 클라이언트가 폴링·재시도하는 흐름이기 때문이다. 다만 응답만 봐서는 "새로 접수됨"과 "무시됨"을 구분할 수 없어서 **로그 이벤트로 구분**한다.

---

### Step 3. Payment Outbox 워커 — 5초 fixed delay, 모든 App 인스턴스가 동시에 실행

`PaymentOutboxScheduler` → `ReservationPaymentOutboxApplication.processPendingMessages(workerId)`

**3-a. 메시지 선점 (claim)**

```sql
BEGIN;
SELECT id FROM payment_outbox_messages
 WHERE (status = 'PENDING'     AND (next_retry_at IS NULL OR next_retry_at <= now))
    OR (status = 'IN_PROGRESS' AND locked_until <= now)      -- 리스 만료분 회수
 ORDER BY created_at, id
 LIMIT 1
 FOR UPDATE SKIP LOCKED;                                     -- ★

UPDATE payment_outbox_messages
   SET status='IN_PROGRESS', locked_by=:workerId,
       locked_until = now + 60s, version = version + 1
 WHERE id = :id;
COMMIT;
```

- `SKIP LOCKED` 덕분에 **App 인스턴스 2대가 같은 스케줄에 깨어나도 서로 다른 메시지를 집는다.** 이 스케줄러만 `scheduler_locks`(리더 선출)를 쓰지 않는 이유가 이것이다 — 리더 1대로 제한하면 결제 처리량이 인스턴스 수만큼 늘지 않는다.
- 리스(`locked_until`) 60초. 워커가 죽으면 60초 뒤 다른 워커가 같은 조건으로 회수한다. 이래서 PG에 `Idempotency-Key`를 반드시 넘겨야 한다(중복 승인 방지).
- V5의 부분 인덱스 두 개가 정확히 이 두 조건절을 받친다.
  ```sql
  create index idx_payment_outbox_pending_ready  on ... (next_retry_at, created_at, id) where status='PENDING';
  create index idx_payment_outbox_expired_lease  on ... (locked_until, created_at, id)  where status='IN_PROGRESS';
  ```
  완료된 메시지가 쌓여도 인덱스는 처리 대기분만 담는다.
- 한 번의 run에서 최대 100건(`MAX_MESSAGES_PER_RUN`). 100건을 다 채웠다는 것은 **유입 속도가 처리 속도를 넘었다는 신호**이므로 배치 완료 로그에 `claimed`/`batchCapacity`/`durationMs`를 같이 남긴다.

**3-b. PG 승인 호출** (트랜잭션 **밖**)

```kotlin
paymentGateway.confirm(paymentKey, orderId, amount, idempotencyKey)
// Toss: POST /v1/payments/confirm, Basic 인증, Idempotency-Key 헤더
// connect 5s / read 30s, JdkClientHttpRequestFactory
// 에러 응답은 TossErrorMapper가 도메인 예외로 변환
```

외부 호출을 DB 트랜잭션 안에 넣지 않는다. 넣으면 PG 지연만큼 커넥션과 행 잠금을 붙잡게 되고, 커넥션 풀이 먼저 마른다.

**3-c. 성공 시 확정 트랜잭션** (`approveAndCreateReservationFromIntent`, `@Transactional` 1개)

```text
① PG 응답 검증: orderId 일치? totalAmount 일치?
   불일치 → 결제 실패 처리 + hold release + outbox SKIPPED   (금액 변조/오배송 방어)
② guests 조회 또는 생성 (property_id + phone 기준)
③ reservations INSERT  (status = PENDING, 즉 "결제 완료, 숙소 확인 대기")
④ payments UPDATE  status=APPROVED, payment_key/method/approved_at, reservation_id 연결
⑤ inventoryHoldService.consume(intentId)
     hold: HELD → PAYMENT_PROCESSING → CONSUMED
     날짜마다  held_count - 1, reserved_count + 1   (각각 낙관적 락 CAS)
⑥ reservation_intents  status=RESERVED, reservation_id 연결
⑦ active_reservation_attempts 삭제 (같은 조건 재예약 허용)
⑧ ReservationCreated 이벤트 발행 → 채널 동기화 태스크 적재
⑨ payment_outbox_messages status=COMPLETED
```

이 9단계가 하나의 PostgreSQL 트랜잭션이다. 중간에 무엇이 실패하든 **전부 롤백**되고, 그 경우는 §Step 5의 보상 경로로 간다.

---

### Step 4. PG 실패 경로 — 예외 종류별로 다르게 처리한다

| PG 예외 | 의미 | 처리 |
|---|---|---|
| `PaymentDeclined` | 카드 거절 등 **확정적 실패** | payment FAILED + hold release + intent PAYMENT_FAILED, outbox COMPLETED (재시도 무의미) |
| `InvalidRequest` | 요청 자체가 잘못됨 | 위와 같되 outbox는 SKIPPED (재시도해도 같은 결과) |
| `ProviderError` / `UnknownError` | **결과를 모름** | `inquire(paymentKey)`로 실제 상태 조회 → DONE이면 승인으로 간주하고 확정, 아니면 재시도 예약 |
| `AlreadyProcessed` | 이미 처리된 결제 | `inquire` → DONE이면 확정, 아니면 **실패 확정**(`failPaymentWhenNotDone=true`) |

핵심은 **"모르는 상태"를 실패로 단정하지 않는 것**이다. 타임아웃은 실패가 아니다. 돈이 빠져나갔을 수도 있으므로 반드시 조회로 사실을 확인한다. 조회조차 실패하면 `failOutbox`로 재시도 횟수를 올리고 백오프를 잡아 다음 사이클로 넘긴다(`max_retries` 소진 시 FAILED).

---

### Step 5. 가장 어려운 경우 — **PG는 승인했는데 내부 확정 트랜잭션이 실패**

```text
confirmAtGateway() 성공  ← 돈은 이미 나갔다
   ↓
approveAndCreateReservationFromIntent()  실패
   (재고 CAS 충돌, DB 커넥션 끊김, 제약 위반 등)
   ↓
ConfirmedPaymentFinalizationException 으로 감싸 상위로 전파
   ↓
compensateConfirmedIntentFinalizationFailure()   ← 보상 트랜잭션
   ① outbox를 failOutbox 처리 — 아직 재시도 여력이 있으면 재시도로 두고 보상하지 않는다
   ② 재시도 소진(FAILED)일 때만 실제 보상 수행:
        payment: APPROVED → CANCEL_REQUESTED
        inventory hold release (재고 원복)
        intent: PAYMENT_FAILED
        active_reservation_attempts 삭제
        payment_outbox_messages INSERT (type=CANCEL_PAYMENT)   ← 환불을 또 Outbox로
   ③ uq_payment_outbox_payment_type (payment_id, type) UNIQUE 로 취소 메시지 중복 생성 차단
```

취소 역시 Outbox 메시지다. 즉 **환불도 재시도 가능한 비동기 작업**으로 다룬다. 보상 자체가 실패하는 경우를 앱 코드의 예외 처리에 맡기지 않겠다는 의미다.

로그에서 이 경로는 `payment_reservation_finalization_failed` → `payment_compensation_started` → `payment_cancel_outbox_created` → `payment_compensation_completed` 순으로 나타난다.

---

### Step 6. 미결제 만료 회수 — 30초 주기, **리더 1대만**

```kotlin
schedulerLock.tryAcquire("reservation-intent-expiration", owner, now+2분, now)
```

```sql
INSERT INTO scheduler_locks (name, locked_by, locked_until, updated_at)
VALUES (...)
ON CONFLICT (name) DO UPDATE SET locked_by=..., locked_until=..., updated_at=...
WHERE scheduler_locks.locked_until <= now;     -- 만료된 락만 뺏을 수 있다
-- affectedRows == 1 이면 획득 성공
```

획득한 인스턴스만 `PAYMENT_WAITING` 이면서 `expires_at <= now`인 intent를 최대 100건 가져와 처리한다.

```text
hold release  → 날짜별 held_count - 1  (재고가 다시 팔 수 있는 상태로 복귀)
intent        → EXPIRED
active_reservation_attempts 삭제
```

**Outbox 워커는 SKIP LOCKED로 분산, 만료·재고 롤링 스케줄러는 리더 락으로 단독 실행** — 같은 "여러 인스턴스" 문제에 두 가지 다른 해법을 쓴 이유는, 전자는 처리량을 나누고 싶고 후자는 중복 실행이 무의미(또는 낭비)하기 때문이다.

같은 락 메커니즘을 재고 rolling 동기화(매일 02:00, 락 2시간)도 사용한다.

---

### Step 7. 확정 이후 생명주기

```text
Reservation.PENDING  ──숙소 운영자 확인──▶ CONFIRMED ──▶ CHECKED_IN ──▶ CHECKED_OUT
        │                                    │
        └── 취소 ──▶ CANCELLED               ├── 취소 ──▶ CANCELLED (재고 복원 + 채널 동기화)
                                             └── NO_SHOW
```

취소 시에도 `ReservationCancelled` 이벤트가 발행되어 재고 복원과 OTA 재고 동기화 태스크가 만들어진다.

---

## 5. 핵심 흐름 ②: 채널(OTA) 양방향 동기화

### 5-1. PMS → OTA (재고 밀어내기)

```text
예약 생성/취소 트랜잭션 커밋
   → ApplicationEventPublisher: ReservationCreated / ReservationCancelled
   → ChannelEventHandler (@EventListener)
   → 날짜별 현재 available_count 조회
   → 활성 OTA 채널마다 sync_tasks INSERT (type=AVAILABILITY_UPDATE, payload jsonb, idempotency_key)
   → SyncTaskScheduler → claimReadyForProcessing (여기도 FOR UPDATE SKIP LOCKED)
   → HttpChannelAvailabilityPublisher → Mock OTA로 HTTP push (connect 5s / read 10s, 공유 RestClient)
   → 성공: COMPLETED / 실패: retry_count++ 후 백오프 재시도
```

OTA 호출도 예약 트랜잭션 안에서 하지 않는다. 외부 채널이 느리다고 우리 예약이 실패하면 안 되기 때문이다. `sync_tasks`가 결제 Outbox와 같은 패턴(claim + lease + 재시도 + 멱등키)을 쓴다.

### 5-2. OTA → PMS (웹훅 수신)

```text
POST /api/v1/properties/{propertyId}/channels/webhook/{channelCode}   (permitAll + CSRF 제외)
   ① 채널 존재 확인
   ② HMAC 서명 검증 — 실패 시 즉시 거절 (검증 전에는 payload를 신뢰하지 않는다)
   ③ processed_webhook_events.saveIfAbsent(eventId)
        INSERT ... ON CONFLICT DO NOTHING → false면 중복 수신이므로 그대로 종료
        (OTA는 재전송을 하므로 at-least-once를 전제로 설계)
   ④ eventType 분기
      BOOKING      → 날짜별 reserved_count 증가 → guest 생성 → Reservation 생성 후 CONFIRMED
                     외부에서 이미 결제가 끝났으므로 승인된 외부 결제로 기록
      CANCELLATION → 예약 취소 + 재고 복원
```

**OTA 예약은 `ReservationIntent`와 `InventoryHold`를 거치지 않는다.** 결제 성사 여부를 우리가 판단하는 흐름이 아니라, 이미 확정된 사실을 통보받는 흐름이기 때문이다. 두 흐름이 같은 `Reservation` 테이블에 도달하지만 진입 경로가 다르다는 점이 도메인 설계의 결론이다.

### 5-3. PG 웹훅

Toss의 결제 상태 변경 웹훅은 `transmission_id`를 `processed_payment_webhook_events`에 기록해 중복을 차단하고, 내부 결제를 찾아 상태를 대조한 뒤 필요하면 `CONFIRM_PAYMENT` Outbox를 다시 적재한다. 즉 **클라이언트 리다이렉트가 유실돼도 웹훅이 결제를 구제하는 이중 경로**다.

---

## 6. 동시성·정합성 설계 정리 (RDB 관점)

같은 방 1개를 여러 명이 동시에 예약할 때, 방어선이 4겹이다.

```text
1층: 애플리케이션 가드
     - 중복 예약 검사 (reservations 조회)
     - active_reservation_attempts (같은 회원·같은 조건 동시 시도 차단)
     - 재고 사전 검증 (available_count > 0)
     → 대부분의 요청을 여기서 400/409로 걸러낸다. 하지만 TOCTOU가 있으므로 이것만으론 부족

2층: UNIQUE 제약 (경합의 승자를 DB가 정한다)
     - reservation_intent_requests PK (member_id, idempotency_key)
     - active_reservation_attempts PK (booking_key)
     - uq_payments_reservation_intent        : intent당 결제 1건
     - uq_inventory_holds_reservation_intent : intent당 hold 1건
     - uq_payment_outbox_payment_type        : 결제당 타입별 메시지 1건
     - uq_payment_outbox_idempotency_key

3층: 낙관적 락 (재고 CAS)
     UPDATE room_inventories SET ..., version = version + 1
      WHERE id = ? AND version = ?
     affectedRows == 0 → 409 INVENTORY_CONFLICT

4층: CHECK 제약 (최후의 방어선, V5)
     ck_room_inventories_non_negative_counts : 모든 카운트 >= 0
     ck_room_inventories_capacity            : reserved + blocked + held <= total
     → 애플리케이션에 버그가 있어도 오버부킹 상태가 디스크에 기록되지 않는다
```

**왜 비관적 락(`SELECT ... FOR UPDATE`)이 아닌가**
- 재고 행은 `(propertyId, roomTypeId, date)` 단위로 잘게 쪼개져 있어 실제 경합은 "인기 날짜의 특정 객실타입"에 한정된다. 그 좁은 구간을 위해 모든 예약 경로에 행 잠금 대기를 넣으면, 경합이 없는 다수 요청까지 느려진다.
- 결제 흐름은 여러 날짜의 재고를 순차로 만진다. 비관적 락이면 날짜 순서가 어긋난 두 트랜잭션이 데드락을 만든다. CAS는 데드락 대신 즉시 실패(409)로 끝난다.
- 사용자 입장에서 "잠깐 기다린 뒤 성공"보다 "지금 안 됩니다"가 더 정직한 도메인이다. 방은 하나뿐이니까.

**멱등성이 놓인 4개 층위**

| 층위 | 키 | 막는 것 |
|---|---|---|
| 클라이언트 → API | `Idempotency-Key` 헤더 → `reservation_intent_requests` | 네트워크 재시도로 intent가 2개 생기는 것 |
| 회원 행위 | `booking_key = sha256(member+property+roomType+dates)` | 같은 사람이 결제창을 여러 개 여는 것 |
| Outbox | `idempotency_key` UNIQUE + `(payment_id, type)` UNIQUE | 같은 결제를 두 번 승인/취소 요청하는 것 |
| PG 호출 | Toss `Idempotency-Key` 헤더 | 리스 만료 재처리로 **실제 결제가 두 번 되는 것** |

### 6-1. MongoDB 프로필과의 차이 (이 프로젝트에서만 할 수 있는 비교)

| 항목 | PostgreSQL 프로필 | MongoDB 프로필 |
|---|---|---|
| 다중 문서/행 원자성 | 기본 제공 (단일 트랜잭션) | replica set 트랜잭션 필요, 제약이 많음 |
| 오버부킹 최종 방어 | `CHECK` 제약이 DB에서 강제 | 애플리케이션 코드가 유일한 방어선 |
| 워커 분산 | `FOR UPDATE SKIP LOCKED` | `findAndModify` 기반 claim + lease |
| 조인 집계(정산/통계) | `GROUP BY` 한 방 | aggregation pipeline, `$lookup` 비용 |
| 스키마 진화 | Flyway 마이그레이션 (명시적, 되돌리기 어려움) | 문서 구조 변경이 유연 (그만큼 드리프트 위험) |
| 장애 시 | RDS Multi-AZ 자동 failover (관리형) | election 과정을 직접 관측·튜닝 (timeout 설정 중요) |

같은 도메인·같은 애플리케이션 서비스를 두 저장소로 구현해 보고 나서 얻은 결론은, **"결제-재고처럼 불변식이 강한 도메인은 RDB의 제약이 코드보다 싸다"**는 것이다. `ck_room_inventories_capacity` 한 줄이 애플리케이션 방어 코드 수십 줄과 같은 일을 하고, 그것도 버그와 무관하게 항상 동작한다.

---

## 7. 가용성과 장애 대응

### 7-1. 무중단 배포 — graceful shutdown 순서

```text
1. 배포 스크립트가 새 이미지를 pull, 컨테이너 교체
2. ALB deregistration_delay 30초
      → 대상에서 빠졌지만 진행 중인 요청은 계속 처리
3. 앱: server.shutdown=graceful
      → 새 요청 수락 중단, 처리 중 요청 완료 대기
4. spring.lifecycle.timeout-per-shutdown-phase = 30s
      → 스케줄러/Outbox 워커도 이 단계에서 정리된다
5. docker stop_grace_period = 40s
      → SIGTERM 후 40초까지 기다린 뒤 SIGKILL (3·4단계보다 길게 잡아야 강제 종료되지 않는다)
6. 새 컨테이너 기동 → health check 2회 연속 통과 → 타깃 등록
7. slow_start 30초 동안 트래픽 점진 유입 (JIT 워밍업 전 급증 방지)
```

**Outbox 워커가 처리 중일 때 인스턴스가 죽으면?** — 해당 메시지는 `IN_PROGRESS`에 `locked_until`을 단 채 남는다. 60초 뒤 다른 인스턴스의 워커가 리스 만료 조건으로 회수하고, PG에는 같은 `Idempotency-Key`로 다시 요청한다. 이미 승인된 결제라면 Toss가 `AlreadyProcessed`로 응답하고, 조회 경로를 통해 확정으로 이어진다. **인스턴스 종료가 결제 유실이나 이중 결제로 이어지지 않는 이유가 여기 있다.**

### 7-2. 의존 컴포넌트 장애 시 동작

| 장애 | 결과 | 설계 |
|---|---|---|
| Redis 다운 | 로그인 세션 소실(재로그인 필요), rate limit **fail-open**, 재고 캐시 miss → DB 직행 | 캐시 접근은 전부 `runCatching`으로 감싸 경고 로그만 남기고 원본 경로로 폴백 |
| RDS failover | 진행 중 트랜잭션은 실패, Hikari가 커넥션 재수립 | 실패한 Outbox 메시지는 재시도 큐에 남아 자동 복구 |
| Toss 지연/오류 | API 응답에는 영향 없음(이미 202로 끝났으므로) | Outbox 백로그로 흡수, 백로그 크기는 배치 완료 로그로 관측 |
| Mock OTA 다운 | 재고 동기화 지연 | `sync_tasks`가 재시도, 예약 자체는 정상 처리 |
| App 1대 다운 | ALB가 health check 2회 실패 후 제외 | 남은 1대가 트래픽 인수, Outbox는 리스 만료로 인계 |

### 7-3. 추적 가능성

모든 결제 로그는 `[한글 라벨] event=snake_case key=value` 형식으로 통일했고, `reservationIntentId`가 **동기 API 요청과 비동기 워커를 잇는 상관 키**다.

```bash
grep "reservationIntentId=<id>" app.log
```

정상 경로:
```text
reservation_intent_created → payment_confirm_accepted → payment_outbox_claimed
→ payment_pg_confirm_started → payment_pg_confirm_succeeded → payment_reservation_finalized
```

실패/보상 경로:
```text
... → payment_pg_confirm_failed → payment_recovery_inquiry_failed
    → payment_reservation_finalization_failed → payment_compensation_started
    → payment_cancel_outbox_created → payment_compensation_completed
```

`paymentKey`는 뒤 8자리만 마스킹해 남긴다(`maskSuffix`). 로그에 결제 식별자 원문을 남기지 않으면서 추적은 가능하게 하는 절충이다.

---

## 8. 어떻게 검증했는가

### 8-1. 재고 경합 — `loadtest/optimistic-lock`

방 1개(`total_count=1`)에 30개 요청을 동시에 던진다.

```text
  20 × 400 ROOM_NOT_AVAILABLE   ← 1층 사전 검증에서 탈락
   9 × 409 INVENTORY_CONFLICT   ← 3층 낙관적 락 CAS에서 탈락
   1 × 201                      ← 성공

재고 최종: total=1, held=1, reserved=0, version=0→1, available=0
충돌 로그의 attemptedVersion이 전부 동일 = 9개 스레드가 같은 스냅샷을 읽고 경쟁했다는 증거
```

성공이 정확히 1건이라는 것과, 실패가 두 가지 다른 층위에서 갈린다는 것을 동시에 보여준다.

### 8-2. 결제-예약 정합성 — `loadtest/payment-flow`

성능이 아니라 **불변식**을 보는 실험이다. k6의 `shared-iterations`로 시도 수를 고정하고, 각 iteration이 intent 상태를 종착까지 폴링한 뒤 `verify.sql`이 10개 섹션을 판정한다.

| 불변식 | 합격 조건 |
|---|---|
| 승인된 결제 수 == 예약 수 | 일치 (돈만 받고 예약이 없는 상태 = 0) |
| 예약 없이 승인된 결제(고아 결제) | 0건 |
| 동일 조건 중복 예약 | 0건 |
| 미처리/실패 Outbox | 0건 |
| 재고 `held_count` 잔여 | 0 (모든 hold가 consume 또는 release로 종결) |
| `reserved_count` == 확정 예약 숙박일 수 | 일치 (재고 누수 없음) |
| 정리되지 않은 활성 예약 시도 | 0건 |

PG 왕복 지연은 `STAYOPS_PAYMENT_LOADTEST_LATENCY_MS`로 흉내낸다. 0으로 두면 워커가 비현실적으로 빨라 백로그가 생기지 않아 검증 가치가 떨어진다.

`HOT_DATE_ONLY=true`로 한 날짜에 몰면 20 VU/200건 중 195건이 `409 INVENTORY_CONFLICT`가 되어 결제 경로에 닿지도 못한다 — 이건 결제 검증이 아니라 재고 경합 검증이 되므로 실험을 둘로 나눴다.

### 8-3. 가용성 — `loadtest/k6/graceful-and-rate-limit.js`

ramping-arrival-rate로 CUJ 트래픽을 흘리는 동안 배포/종료를 걸고, 동시에 로그인 버스트로 429가 실제로 발생하는지(`rate_limit_login_429 count > 0`) 확인한다. 즉 **가용성 장치가 켜져 있는지를 테스트가 증명**하게 했다.

### 8-4. 코드 레벨

- Kotest BehaviorSpec 단위 테스트 (도메인 + 애플리케이션, MockK)
- Testcontainers PostgreSQL 통합 테스트 (`RdbRoomInventoryRepositoryTest`가 저장소 계층 CAS를 검증)
- ArchUnit 명명 규칙 테스트 (`architecture/PersistenceNamingConventionTest`, `ServiceNamingConventionTest`)
- CI에서 PR마다 전체 테스트 수행

---

## 9. 현재 한계 (면접에서 먼저 말하는 편이 낫다)

| 한계 | 영향 | 개선 방향 |
|---|---|---|
| **조회 인덱스 부재** — V5에서 추가한 인덱스는 전부 Outbox용이고, `reservations`에는 PK 외 인덱스가 없다. PostgreSQL은 FK에 인덱스를 자동 생성하지 않는다 | 예약 검색(`property_id + status + created_at`)이 데이터 증가 시 seq scan | MongoDB 프로필에서 복합 인덱스로 p95 2.25s → 230ms를 만든 경험을 그대로 적용해야 하는 부분. 다음 마이그레이션 과제 |
| **offset 페이지네이션** (`limit/offset` + `fetchCount`) | 깊은 페이지에서 비용 증가, 매 페이지 count 쿼리 | 커서 기반(`created_at, id` 튜플 비교)으로 전환 |
| **Redis 단일 노드 SPOF** | 세션·rate limit·캐시가 동시에 영향 | 세션은 fail-open이 아니므로(재로그인 필요) ElastiCache 또는 Sentinel 검토 |
| **HikariCP 기본값(풀 10)** | App 2대 × 10 = 20 커넥션. 워커·스케줄러·API가 같은 풀을 공유 | RDS `max_connections`와 워커 동시성 기준으로 명시적 사이징 필요 |
| **Outbox 폴링 5초** | 결제 승인 → 예약 확정까지 최대 5초 + PG 왕복 | 처리량이 아니라 지연이 문제가 되면 NOTIFY/LISTEN 또는 즉시 트리거 |
| **재고 캐시가 evict 방식** | 쓰기 직후 캐시를 지우므로 다음 읽기는 항상 DB | 정합성 우선의 선택. stale read가 발생해도 CAS가 막기 때문에 오버부킹으로는 이어지지 않음 |
| **`application-prod.yml`이 Mongo URI를 참조** | `rdb` 프로필에서는 Mongo auto-config가 배제되어 실제로는 해석되지 않지만, 프로필별 설정 파일 분리가 깔끔하지 않음 | prod 설정을 저장소별로 분리 |
| **부하 실험이 단일 머신** | 응답시간 절대값은 의미 없음 | 정합성 검증 목적으로만 해석. 성능 수치는 AWS 환경 실험(MongoDB 프로필)에서 별도 측정 |

---

## 10. 예상 질문과 답변 요지

**Q. 결제 API가 왜 202를 주나요? 사용자는 결제가 끝난 줄 아는데.**
A. PG 승인은 외부 I/O이고 결과를 못 받는 경우가 존재합니다. 그래서 API는 "승인 요청을 확실히 접수했다"까지만 보증하고 Outbox에 적재합니다. 클라이언트는 intent를 폴링해 `RESERVED`를 확인합니다. 사용자 체감 지연은 Outbox 폴링 주기(5초)와 PG 왕복이고, 대신 어떤 실패에서도 재시도 주체가 DB에 남습니다.

**Q. 이미 승인된 결제를 두 번 승인하지 않는다고 어떻게 보장하죠?**
A. 세 겹입니다. Outbox 메시지의 `idempotency_key`가 UNIQUE라 메시지 자체가 하나뿐이고, claim이 `FOR UPDATE SKIP LOCKED`라 두 워커가 같은 메시지를 못 집고, 리스 만료로 재처리되더라도 PG에 **같은** `Idempotency-Key`를 보내기 때문에 PG가 중복을 흡수합니다.

**Q. PG는 승인됐는데 우리 DB가 실패하면요?**
A. 확정 트랜잭션이 통째로 롤백되고 `ConfirmedPaymentFinalizationException`으로 감싸 상위로 던집니다. 재시도 여력이 남아 있으면 재시도하고, 소진되면 보상 트랜잭션이 결제를 `CANCEL_REQUESTED`로 바꾸고 재고 hold를 풀고 `CANCEL_PAYMENT` Outbox를 만듭니다. 환불도 재시도 가능한 비동기 작업으로 다룹니다.

**Q. 낙관적 락은 경합이 심하면 실패율이 높지 않나요?**
A. 높습니다. 실험에서 한 날짜에 몰면 200건 중 195건이 409였습니다. 다만 이 도메인에서는 그게 정확한 결과입니다. 방이 1개면 1명만 성공해야 하고, 비관적 락으로 순서를 세워도 결국 1명만 성공합니다. 차이는 나머지가 기다렸다 실패하느냐 즉시 실패하느냐이고, 후자를 택했습니다. 다만 재고가 여러 개일 때 CAS 재시도를 넣으면 성공률을 올릴 수 있고, 그건 다음 개선 항목입니다.

**Q. 스케줄러가 인스턴스 2대에서 다 도는데 중복 실행 안 되나요?**
A. 두 가지로 나눴습니다. Outbox 워커는 중복 실행이 오히려 좋아서(처리량 분산) 락 없이 `SKIP LOCKED`로 서로 다른 메시지를 집게 했고, intent 만료·재고 롤링은 중복이 무의미해서 `scheduler_locks` 테이블로 리더를 뽑습니다. 리더 락도 `ON CONFLICT DO UPDATE ... WHERE locked_until <= now`로 구현해서, 리더가 죽어도 락 만료 후 다른 인스턴스가 이어받습니다.

**Q. MongoDB랑 PostgreSQL 둘 다 구현한 이유가 뭐죠?**
A. 실무에서 MongoDB를 쓰고 있어서 시작은 Mongo였는데, 결제·재고처럼 불변식이 강한 도메인에서 트랜잭션과 제약을 애플리케이션이 전부 책임지는 구조가 부담이었습니다. 같은 도메인을 RDB로 옮겨 보고 `CHECK` 제약 한 줄이 방어 코드 수십 줄을 대체하는 걸 확인했습니다. 반대로 스키마 진화 유연성과 문서 단위 읽기는 Mongo가 낫습니다. 포트-어댑터로 저장소를 프로필로 갈아끼울 수 있게 만든 덕에 이 비교가 가능했습니다.

**Q. 오버부킹이 절대 안 난다고 확신하나요?**
A. 애플리케이션 버그가 있어도 `reserved + blocked + held <= total` CHECK 제약 때문에 **잘못된 상태가 디스크에 기록되지 않습니다.** 대신 그 경우 트랜잭션이 실패하므로 예약이 거절되죠. 오버부킹 대신 예약 실패를 택한 설계입니다. 실제로 이 불변식을 부하 실험의 `verify.sql`이 매 실행마다 검증합니다.
