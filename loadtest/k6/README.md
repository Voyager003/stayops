# StayOps Load Tests

StayOps 부하 테스트는 애플리케이션 병목과 MongoDB 병목을 분리해서 본다.

```text
k6 -> Nginx -> StayOps Spring Boot -> MongoDB replica set
```

## 공통 실행 규칙

부하 시작 시점은 `k6 run`을 실행하는 순간이다. 종료는 선택한 `TEST_MODE`가 끝나거나 `Ctrl+C`로 중단할 때 결정된다.

모든 요청에는 다음 헤더가 붙는다.

```text
X-Experiment-Id: 실험 실행 단위
X-Loadtest-Phase: smoke, app-baseline, db-ramp, failover-steady 등
X-Loadtest-Scenario: lightweight-http, business-read-control, read-heavy, write-mixed
```

Spring Boot prod 로그는 이 값을 JSON MDC 필드로 출력한다. k6 summary 파일도 `app-summary-<EXPERIMENT_ID>.json`, `db-summary-<EXPERIMENT_ID>.json` 형식으로 생성된다.

지원하는 `TEST_MODE`:

| TEST_MODE | 용도 | 부하 형태 |
|---|---|---|
| `smoke` | 배포와 설정 확인 | 30s 실행 후 종료 |
| `baseline`, `app-baseline`, `db-baseline` | 안정 기준선 측정 | 2m warm-up, 10m steady, 1m cooldown |
| `ramp`, `db-ramp` | 병목 지점 탐색 | 1x, 2x, 3x 단계 증가 후 cooldown |
| `spike` | 짧은 급증 트래픽 | 1x, 5x, 1x 후 cooldown |
| `failover`, `failover-steady` | primary 중단 실험 | 3m warm-up, 10m failover window, 2m cooldown |

`dropped_iterations`가 증가하면 k6가 목표 도착률을 만들지 못했다는 뜻이다. 이 경우 서버가 아니라 load generator 리소스가 부족했을 가능성도 같이 확인한다.

## Application Thread-Pool Test

`stayops-app-load.js`는 Boot App이 DB보다 먼저 병목이 되는지 확인한다.

```text
lightweight_http        -> /actuator/info
business_read_control  -> /api/v1/customer/properties
```

예시:

```bash
BASE_URL=https://api.example.com \
EXPERIMENT_ID=20260419-app-baseline-001 \
LOADTEST_PHASE=app-baseline \
TEST_MODE=app-baseline \
LIGHTWEIGHT_RATE=50 \
BUSINESS_RATE=10 \
k6 run loadtest/k6/stayops-app-load.js
```

주요 지표:

- k6: throughput, p95/p99 latency, failed request rate, dropped iterations
- Spring Boot: HTTP latency, JVM heap, GC pause, live threads, Tomcat busy threads
- VM: CPU, memory, network, load average
- MongoDB: read control 요청에서만 CPU/IO가 상승하는지

## MongoDB DB Load Test

`stayops-db-load.js`는 MongoDB에 영향을 주는 읽기와 예약 생성 경로를 함께 실행한다.

```text
read_heavy  -> 숙소 목록, 상세, 객실 타입, 재고, 요금 조회
write_mixed -> 고객 예약 생성
```

예약 생성은 같은 회원, 같은 객실 타입, 같은 기간의 중복 예약 검증에 걸릴 수 있다. 따라서 write 시나리오는 `WRITE_DATE_SPREAD_DAYS` 범위 안에서 투숙일을 분산한다. 이 값보다 많은 write를 장시간 실행하면 다시 중복 충돌이 발생할 수 있으므로 테스트 데이터의 요금/재고 기간을 함께 늘려야 한다.

필수 데이터:

- `PROPERTY_ID`
- `ROOM_TYPE_ID`
- customer account email/password
- `CHECK_IN`부터 `CHECK_IN + WRITE_DATE_SPREAD_DAYS`까지 적용 가능한 요금
- failover 실험에서는 충분한 MongoDB 디스크 여유 공간

Smoke:

```bash
BASE_URL=https://api.example.com \
EXPERIMENT_ID=20260419-db-smoke-001 \
LOADTEST_PHASE=smoke \
TEST_MODE=smoke \
PROPERTY_ID=property-dummy-001 \
ROOM_TYPE_ID=roomtype-dummy-001 \
CUSTOMER_EMAIL=guest@example.com \
CUSTOMER_PASSWORD=replace-at-runtime \
READ_RATE=2 \
WRITE_RATE=1 \
WRITE_DATE_SPREAD_DAYS=30 \
k6 run loadtest/k6/stayops-db-load.js
```

Ramp:

```bash
BASE_URL=https://api.example.com \
EXPERIMENT_ID=20260419-db-ramp-001 \
LOADTEST_PHASE=db-ramp \
TEST_MODE=db-ramp \
PROPERTY_ID=property-dummy-001 \
ROOM_TYPE_ID=roomtype-dummy-001 \
CUSTOMER_EMAIL=guest@example.com \
CUSTOMER_PASSWORD=replace-at-runtime \
READ_RATE=20 \
WRITE_RATE=2 \
WRITE_DATE_SPREAD_DAYS=90 \
k6 run loadtest/k6/stayops-db-load.js
```

Failover:

```bash
BASE_URL=https://api.example.com \
EXPERIMENT_ID=20260419-failover-001 \
LOADTEST_PHASE=failover-steady \
TEST_MODE=failover-steady \
PROPERTY_ID=property-dummy-001 \
ROOM_TYPE_ID=roomtype-dummy-001 \
CUSTOMER_EMAIL=guest@example.com \
CUSTOMER_PASSWORD=replace-at-runtime \
READ_RATE=20 \
WRITE_RATE=2 \
WRITE_DATE_SPREAD_DAYS=90 \
k6 run loadtest/k6/stayops-db-load.js
```

Failover 실험은 steady window가 시작된 뒤 현재 MongoDB primary를 의도적으로 중지하고 관찰한다. DB를 트래픽으로 무너뜨리는 실험이 아니라, 통제된 장애 주입으로 election, write 실패율, 복구 시간을 측정한다.

## Prometheus Remote Write

Prometheus compose는 k6 remote write receiver를 켠다.

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
k6 run -o experimental-prometheus-rw loadtest/k6/stayops-db-load.js
```

원격에서 실행할 때는 Prometheus port를 public으로 열지 않고 SSH tunnel 또는 제한된 source IP만 사용한다.
