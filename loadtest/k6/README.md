# StayOps k6 Load Test

## Purpose

이 부하 테스트는 고객 핵심 여정 기준으로 App 서버와 MongoDB replica set의 처리 한계를 찾고,
한계 초과 부하에서 mongo1 장애와 recovery를 관측하기 위한 것이다.

이번 범위에 포함한다.

- 고객 숙소 탐색
- 숙소 상세 / 객실 타입 조회
- 날짜 / 인원 기준 offers 조회
- setup 단계 고객 / OWNER 로그인 세션
- 고객 예약 생성
- 결제 확인 API 호출
- 내 예약 조회
- PMS 예약 목록 조회
- PMS PENDING 예약 수동 확정
- breakpoint 탐색
- MongoDB overload 실험

이번 범위에서 제외한다.

- OTA random booking / webhook 유입
- 실제 Toss API 대량 호출

외부 결제 API는 대량 부하 테스트 결과를 왜곡하므로 App 서버는 `STAYOPS_PAYMENT_GATEWAY=loadtest`일 때
loadtest 전용 mock payment gateway를 사용한다. 기본값은 `toss`이며 운영 결제 동작은 유지된다.

## Synthetic data

부하 테스트 전 운영 MongoDB에 synthetic data를 넣는다. 모든 데이터는 `loadtest-<runId>` prefix를 사용한다.
테스트 계정도 run prefix를 포함한다.

- 고객: `loadtest-run-001-customer-0001@example.com`
- OWNER: `loadtest-run-001-owner-0001@example.com`
- 비밀번호: `password123`

2GiB MongoDB 인스턴스 기준 기본 규모:

- 숙소 10개
- 고객 계정 30개
- 재고 60일
- 기존 예약 / 결제 5,000건

안정 확인 후 `10,000 -> 20,000 -> 50,000` 순서로 올린다.

mongo1에서 실행:

```bash
cd ~/stayops/infra/mongodb
set -a
source .env
set +a

docker compose cp /path/to/stayops/loadtest/mongodb/seed-synthetic-data.js mongo:/tmp/seed-synthetic-data.js
docker compose cp /path/to/stayops/loadtest/mongodb/cleanup-synthetic-data.js mongo:/tmp/cleanup-synthetic-data.js

docker compose exec \
  -e LOADTEST_RUN_ID=run-001 \
  -e LOADTEST_PROPERTY_COUNT=10 \
  -e LOADTEST_CUSTOMER_COUNT=30 \
  -e LOADTEST_INVENTORY_DAYS=60 \
  -e LOADTEST_RESERVATION_COUNT=5000 \
  -e LOADTEST_BATCH_SIZE=500 \
  -T mongo \
  mongosh -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin \
  --file /tmp/seed-synthetic-data.js
```

정리:

```bash
docker compose exec \
  -e LOADTEST_PREFIX=loadtest-run-001 \
  -T mongo \
  mongosh -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin \
  --file /tmp/cleanup-synthetic-data.js
```

로컬 파일을 컨테이너에서 바로 읽을 수 없으면 `docker cp`로 mongo 컨테이너에 복사한 뒤 실행한다.

## Scenarios

### `stayops-app-load.js`

가벼운 앱 baseline 용도다.

- `GET /health`
- `GET /api/v1/customer/properties`

운영 `/health`가 200을 반환하지 않으면 `HEALTH_WEIGHT=0 INFO_WEIGHT=1`로 실행한다.

### `stayops-db-load.js`

read-heavy 숙소 조회 부하다.

- `GET /api/v1/customer/properties`
- `GET /api/v1/customer/properties/{propertyId}`
- `GET /api/v1/customer/properties/{propertyId}/room-types`
- `GET /api/v1/customer/properties/{propertyId}/offers`

### `stayops-cuj-load.js`

고객 핵심 여정 baseline / step-load 용도다.

기본 mix:

- `25%` 숙소 목록 조회
- `20%` 숙소 상세 + 객실 타입 조회
- `25%` offers 조회
- `20%` 예약 생성
- `10%` 내 예약 조회

인증이 필요한 요청은 고객 계정 풀로 로그인한 뒤 세션 쿠키를 사용한다.
결제 확인 API까지 호출한다. App 서버는 loadtest payment gateway를 켜야 외부 Toss를 호출하지 않는다.

권장 mix:

- `20%` 숙소 목록 조회
- `15%` 숙소 상세 + 객실 타입 조회
- `20%` offers 조회
- `20%` 고객 예약 생성 + 결제 확인
- `10%` 내 예약 조회
- `12%` PMS 예약 목록 조회
- `3%` PMS PENDING 예약 수동 확정

### `stayops-breakpoint-load.js`

처리 한계 탐색용이다. 기본 RPS 단계:

```text
20 -> 40 -> 80 -> 120 -> 160 -> 220
```

각 단계는 기본 5분이다.

### `stayops-mongo-overload.js`

destructive 실험용이다. breakpoint 이후 한계 이상의 부하를 가해 mongo1 primary 장애를 유도한다.

기본 RPS 단계:

```text
160 -> 240 -> 320 -> 480
```

실행 전 mongo1, mongo2, mongo3의 `rs.status()`와 Grafana 대시보드를 열어둔다.

## Run examples

```bash
cd loadtest/k6
```

App 서버 부하 테스트 전 loadtest payment gateway를 켠다.

```bash
STAYOPS_PAYMENT_GATEWAY=loadtest
STAYOPS_PAYMENT_LOADTEST_LATENCY_MS=200
docker compose -f docker-compose.prod.yml up -d app
```

### Smoke

```bash
MODE=smoke \
LOADTEST_RUN_ID=run-001 \
CUSTOMER_COUNT=30 \
OWNER_COUNT=10 \
k6 run stayops-cuj-load.js
```

### CUJ baseline

```bash
LOADTEST_RUN_ID=run-001 \
CUJ_RATE=10 \
CUSTOMER_COUNT=30 \
OWNER_COUNT=10 \
k6 run stayops-cuj-load.js
```

### CUJ step-load

```bash
MODE=step-load \
LOADTEST_RUN_ID=run-001 \
CUJ_STEP_RATES=5,10,20,40,80 \
CUSTOMER_COUNT=30 \
OWNER_COUNT=10 \
k6 run stayops-cuj-load.js
```

### Breakpoint

```bash
LOADTEST_RUN_ID=run-001 \
BREAKPOINT_RATES=20,40,80,120,160,220 \
BREAKPOINT_STAGE_MINUTES=5 \
CUSTOMER_COUNT=30 \
OWNER_COUNT=10 \
k6 run stayops-breakpoint-load.js
```

### Mongo overload

```bash
LOADTEST_RUN_ID=run-001 \
OVERLOAD_RATES=160,240,320,480 \
OVERLOAD_STAGE_MINUTES=3 \
CUSTOMER_COUNT=30 \
OWNER_COUNT=10 \
k6 run stayops-mongo-overload.js
```

## Metrics

k6:

- `http_req_duration p95/p99`
- `http_req_failed`
- `dropped_iterations`
- endpoint별 RPS

App / JVM:

- `http_server_requests_seconds_count`
- `http_server_requests_seconds_bucket`
- 5xx rate
- `jvm_memory_used_bytes`
- `jvm_gc_pause_seconds_bucket`
- Tomcat thread metrics

Host:

- `node_cpu_seconds_total`
- `node_memory_MemAvailable_bytes`
- network rx / tx
- disk I/O

MongoDB:

- mongo1/2/3 up
- primary / secondary state
- connections
- opcounters
- replication lag
- CPU / memory / disk I/O

CloudWatch:

- EC2 CPU
- CPU credit balance
- instance status check
- network
- EBS metrics

## Pass / fail 기준

안정적으로 감당 가능한 부하는 아래 기준을 만족하는 최대 RPS로 본다.

- `http_req_failed < 1%`
- `dropped_iterations = 0`
- 주요 API p95 `< 1.5s ~ 2.5s`
- App 5xx가 지속적으로 증가하지 않음
- MongoDB primary가 정상 유지

Mongo overload는 pass/fail보다 관측 실험이다.

- mongo1이 죽는 시점의 RPS
- secondary 승격 시간
- app 오류 지속 시간
- 정상 read/write 복구 시간
- mongo1 복귀 후 secondary 합류 여부

한계 이상 부하로 자연 마비가 재현되지 않으면, primary 중지/재시작 또는 `rs.stepDown()`은 별도 승인 후 수행한다.

## Verification

```bash
node --check common.js
node --check cuj-flow.js
node --check stayops-app-load.js
node --check stayops-db-load.js
node --check stayops-cuj-load.js
node --check stayops-breakpoint-load.js
node --check stayops-mongo-overload.js
node --check ../mongodb/seed-synthetic-data.js
node --check ../mongodb/cleanup-synthetic-data.js
```
