# AWS MongoDB Standalone Load Test Runbook

## Purpose

이 Runbook은 **true standalone MongoDB** 기준의 read-heavy 부하 테스트 준비 절차를 정리한다.

이번 단계에서는 기존 replica set 기반 `docker-compose.yml`을 직접 바꾸지 않는다. 대신 standalone override 파일을 추가해서, read-heavy 검증을 위한 최소 토폴로지만 분리한다.

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

## Step 4. Smoke check

공개 엔드포인트 확인:

```bash
curl http://<APP_HOST>:8080/actuator/health
curl http://<APP_HOST>:8080/actuator/info
curl http://<APP_HOST>:8080/api/v1/customer/properties
```

결과:

- `200 OK`
- properties 응답이 배열

## Step 5. k6 smoke

```bash
cd loadtest/k6
BASE_URL=http://<APP_HOST>:8080 MODE=smoke k6 run stayops-app-load.js
BASE_URL=http://<APP_HOST>:8080 MODE=smoke k6 run stayops-db-load.js
```

## Step 6. App baseline

```bash
cd loadtest/k6
BASE_URL=http://<APP_HOST>:8080 MODE=app-baseline APP_RATE=5 k6 run stayops-app-load.js
```

목적:

- App front door latency 확인
- DB가 아닌 앱/네트워크 기본 상태 확인

## Step 7. DB baseline

```bash
cd loadtest/k6
BASE_URL=http://<APP_HOST>:8080 \
MODE=db-baseline \
HOT_PROPERTY_COUNT=5 \
SEARCH_RATE=0.50 \
DETAIL_RATE=0.20 \
OFFERS_RATE=0.30 \
DB_RATE=12 \
k6 run stayops-db-load.js
```

`HOT_PROPERTY_IDS`를 직접 주고 싶다면:

```bash
HOT_PROPERTY_IDS=prop-1,prop-7,prop-9
```

## Step 8. DB ramp

```bash
cd loadtest/k6
BASE_URL=http://<APP_HOST>:8080 \
MODE=db-ramp \
HOT_PROPERTY_COUNT=5 \
DB_RAMP_START=8 \
DB_RAMP_STEP_1=16 \
DB_RAMP_STEP_2=24 \
DB_RAMP_STEP_3=32 \
k6 run stayops-db-load.js
```

중점 관찰:

- p95 급등 시점
- `dropped_iterations`
- app CPU / memory
- MongoDB CPU / memory / connection / operation trend

## Step 9. Explicit non-goals

이번 Runbook에서 하지 않는 것:

- primary 중지
- election 관찰
- recovery 관찰
- write failure 실험
- spike / stress test

## Step 10. Result interpretation

standalone 결과는 아래 의미로만 해석한다.

- 현재 read-heavy public scenario에서의 처리량 추정
- hot-property 집중 시 `offers` fan-out 비용 확인
- App vs MongoDB 중 어디가 먼저 포화되는지 구분

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
