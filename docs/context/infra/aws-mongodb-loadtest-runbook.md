# AWS MongoDB Standalone Load Test Runbook

## Purpose

이 Runbook은 **true standalone MongoDB** 기준의 read-heavy 부하 테스트 준비 절차를 정리한다.

이번 단계에서는 기존 replica set 기반 `docker-compose.yml`을 직접 바꾸지 않는다. 대신 standalone override 파일과 standalone Prometheus 설정을 추가해서, 안정 구간 탐색과 고정 부하 검증에 필요한 최소 토폴로지만 분리한다.

## Topology

- Local PC
  - k6 실행
- AWS EC2 #1
  - `app`
  - `redis`
  - `mock-ota`
  - 관측용 loki / promtail / grafana 선택
- AWS EC2 #2
  - standalone `mongod`

## Files

- base compose: `docker-compose.yml`
- standalone override: `docker-compose.loadtest-standalone.yml`
- standalone Prometheus config: `infra/app/prometheus.loadtest-standalone.yml`
- k6 scripts:
  - `loadtest/k6/stayops-app-load.js`
  - `loadtest/k6/stayops-db-load.js`

## Preconditions

- 대상 브랜치: `feat/standalone-mongodb-loadtest-main`
- MongoDB는 replica set이 아니라 standalone으로 띄운다.
- reservation write 시나리오는 이번 단계에서 제외한다.
- App/Mongo private network 연결이 가능해야 한다.

## Step 1. Standalone compose shape 확인

standalone override는 아래 두 가지만 바꾼다.

1. MongoDB 실행 옵션에서 replica set 제거
2. App의 Mongo URI에서 `replicaSet=rs0` 제거

`mongo-init`은 compose 병합 충돌을 피하기 위해 **no-op 서비스**로 남긴다. 즉 standalone 실험에서는 `rs.initiate()`를 실행하지 않는다.

## Step 2. Compose config 검증

```bash
docker compose -f docker-compose.yml -f docker-compose.loadtest-standalone.yml config
```

확인 포인트:

- `mongodb.command`에 `--replSet`가 없어야 한다.
- `app.environment.SPRING_MONGODB_URI`에 `replicaSet=rs0`가 없어야 한다.
- `mongo-init.entrypoint`가 `standalone mode: skip rs.initiate` 메시지를 출력하는 no-op이어야 한다.

## Step 3. App stack 기동

### 3-1. MongoDB EC2

```bash
docker compose -f docker-compose.yml -f docker-compose.loadtest-standalone.yml up -d mongodb
```

### 3-2. App EC2

MongoDB가 별도 EC2에 있으므로 App 컨테이너는 private IP를 직접 바라보게 한다.

```bash
SPRING_MONGODB_URI=mongodb://<MONGO_PRIVATE_IP>:27017/stayops \
docker compose -f docker-compose.yml -f docker-compose.loadtest-standalone.yml up -d app redis mock-ota
```

로그 확인:

```bash
docker compose -f docker-compose.yml -f docker-compose.loadtest-standalone.yml logs -f app
```

## Step 4. Observability stack 준비

Prometheus / Grafana를 같이 쓸 경우, app host의 관측 스택은 `infra/app/docker-compose.yml` 기준으로 올린다.

```bash
cd infra/app
PROMETHEUS_CONFIG_PATH=./prometheus.loadtest-standalone.yml \
MONGO_HOST=<MONGO_PRIVATE_IP> \
MONGO_EXPORTER_HOST=<MONGO_PRIVATE_IP> \
docker compose up -d prometheus grafana node-exporter
```

확인 포인트:

- App: `/actuator/prometheus`
- App host: `node-exporter`
- Mongo host: `:9100`, `:9216`

## Step 5. Smoke check

공개 엔드포인트 확인:

```bash
curl http://<APP_HOST>:8080/actuator/health
curl http://<APP_HOST>:8080/actuator/info
curl http://<APP_HOST>:8080/api/v1/customer/properties
```

결과:

- `200 OK`
- properties 응답이 배열

## Step 6. k6 smoke

```bash
cd loadtest/k6
BASE_URL=http://<APP_HOST>:8080 MODE=smoke k6 run stayops-app-load.js
BASE_URL=http://<APP_HOST>:8080 MODE=smoke k6 run stayops-db-load.js
```

목적:

- 스크립트 오류 확인
- 최소 부하에서 엔드포인트가 정상 동작하는지 확인

## Step 7. App baseline

```bash
cd loadtest/k6
BASE_URL=http://<APP_HOST>:8080 MODE=app-baseline APP_RATE=5 k6 run stayops-app-load.js
```

목적:

- App front door latency 확인
- DB가 아닌 앱/네트워크 기본 상태 확인

## Step 8. DB step-load

```bash
cd loadtest/k6
BASE_URL=http://<APP_HOST>:8080 \
MODE=db-step-load \
HOT_PROPERTY_COUNT=5 \
SEARCH_RATE=0.50 \
DETAIL_RATE=0.20 \
OFFERS_RATE=0.30 \
DB_STEP_RATES=8,16,24,32 \
k6 run stayops-db-load.js
```

`HOT_PROPERTY_IDS`를 직접 주고 싶다면:

```bash
HOT_PROPERTY_IDS=prop-1,prop-7,prop-9
```

중점 관찰:

- 어떤 단계까지 p95가 안정적인지
- `http_req_failed`
- `dropped_iterations`
- app CPU / memory
- MongoDB CPU / memory / connections / operations

불안정 판정:

- `http_req_failed >= 1%`
- `dropped_iterations > 0`
- `customer-property-offers` p95 `> 2s`
- App CPU 또는 MongoDB CPU가 `75%` 이상으로 지속

## Step 9. DB load

```bash
cd loadtest/k6
BASE_URL=http://<APP_HOST>:8080 \
MODE=db-load \
HOT_PROPERTY_COUNT=5 \
SEARCH_RATE=0.50 \
DETAIL_RATE=0.20 \
OFFERS_RATE=0.30 \
DB_LOAD_RATE=<STEP_LOAD에서_확인한_안정_RPS> \
k6 run stayops-db-load.js
```

중점 관찰:

- 안정 구간에서도 p95와 실패율이 유지되는지
- 고정 부하에서 App / MongoDB 중 어느 쪽이 병목인지
- `offers`가 주 병목인지

## Step 10. 확인할 핵심 메트릭

- k6
  - RPS
  - p95 / p99
  - error rate
  - `dropped_iterations`
- App / JVM
  - endpoint latency
  - `process.cpu.usage`
  - `system.cpu.usage`
  - `jvm.memory.used`
  - `jvm.gc.pause`
  - `tomcat.threads.*`
- Host
  - CPU
  - memory available
  - network rx/tx
  - disk I/O
- MongoDB
  - connections
  - opcounters / operations trend
  - network bytes in/out
  - memory / resident memory

## Step 11. Explicit non-goals

이번 Runbook에서 하지 않는 것:

- primary 중지
- election 관찰
- recovery 관찰
- write failure 실험
- spike / stress test

## Step 12. Result interpretation

standalone 결과는 아래 의미로만 해석한다.

- 현재 read-heavy public scenario에서의 안정 구간
- hot-property 집중 시 `offers` fan-out 비용 확인
- App vs MongoDB 중 어디가 먼저 포화되는지 구분
- 스펙업 전에 시도할 최적화 후보 도출

아래 의미로 해석하면 안 된다.

- MongoDB write-path capacity
- failover capability
- replica set recovery behavior
- production HA 보장

## Rollback

standalone 실험 종료 후 base compose만 다시 쓰면 replica set 방식으로 돌아간다.

```bash
docker compose down
docker compose up -d
```
