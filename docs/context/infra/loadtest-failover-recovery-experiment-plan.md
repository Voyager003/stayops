# 부하 테스트와 MongoDB 장애 복구 실험 계획

작성일: 2026-04-19
상태: 구현 준비

## 목적

이번 작업의 목적은 프로덕션 전체 고가용성 구성을 완성하는 것이 아니라, 포트폴리오 비용 범위 안에서 다음 가설을 검증할 수 있는 실행 환경을 만든다.

```text
1. Application 서버와 MongoDB 중 어느 쪽이 먼저 병목이 되는가
2. MongoDB P-S-S replica set에서 primary 장애 시 자동 failover가 동작하는가
3. 장애 중 들어온 write가 어떤 오류율과 지연을 보이는가
4. 장애 노드가 복귀했을 때 oplog 기반 recovery로 SECONDARY에 재합류하는가
5. 서버 스펙을 키우지 않고 처리량을 늘릴 수 있는 개선 후보가 무엇인가
```

VM에 직접 접속해 Docker를 설치하고 compose를 실행하는 작업은 배포 이후 단계에서 수행한다. 이 문서는 그 전에 저장소에 준비해야 할 구성, 테스트 스크립트, 관측 기준을 정의한다.

## 최종 인프라 구성

```text
Internet / Local k6
        |
        v
---------------------------------------------------------
| Oracle App VM                                          |
|--------------------------------------------------------|
| docker compose: infra/oracle/app                       |
|                                                        |
|  ---------      ---------      -----------             |
| | nginx   | -> | app     | -> | redis     |            |
| | 80/443  |    | 8080    |    | session   |            |
|  ---------      ---------      -----------             |
|      |             |                                  |
|      |             +-----------> AWS MongoDB members   |
|      |                                                |
|  --------------------------------------------------    |
| | prometheus | grafana | loki | promtail | node-exp |  |
|  --------------------------------------------------    |
---------------------------------------------------------
        |
        | HTTPS webhook / external API simulation
        v
---------------------------------------------------------
| Oracle Mock OTA VM                                     |
|--------------------------------------------------------|
| docker compose: infra/oracle/mock-ota                  |
|                                                        |
|  ---------      --------------      ----------------   |
| | nginx   | -> | mock-ota-app | -> | mock-ota mongo |  |
| | 80/443  |    | 8081         |    | local only     |  |
|  ---------      --------------      ----------------   |
|                                                        |
|  node-exporter / promtail                              |
---------------------------------------------------------

---------------------------------------------------------
| AWS MongoDB VM 1                                       |
|--------------------------------------------------------|
| docker compose: infra/aws/mongo                        |
| mongod data-bearing voting member                      |
| mongodb-exporter / node-exporter / promtail            |
---------------------------------------------------------
        ^                 ^                  ^
        | replica traffic | replica traffic  | replica traffic
        v                 v                  v
---------------------------------------------------------
| AWS MongoDB VM 2                                       |
|--------------------------------------------------------|
| mongod data-bearing voting member                      |
| mongodb-exporter / node-exporter / promtail            |
---------------------------------------------------------
        ^
        |
        v
---------------------------------------------------------
| AWS MongoDB VM 3                                       |
|--------------------------------------------------------|
| mongod data-bearing voting member                      |
| mongodb-exporter / node-exporter / promtail            |
---------------------------------------------------------
```

MongoDB는 `Primary - Secondary - Secondary`로 둔다. Arbiter는 비용을 줄일 수 있지만 데이터를 저장하지 않으므로 이번 실험에서는 제외한다. `w=majority`, `readPreference=primary`, `retryWrites=true`를 사용해 예약/결제/재고 데이터의 쓰기 안정성을 우선한다.

## Phase 1. 배포 전 정적 검증

가설:

```text
Compose, k6, 테스트 코드가 로컬에서 깨지면 VM 배포 이후 문제 원인을 분리하기 어렵다.
```

액션:

```bash
node --check loadtest/k6/stayops-app-load.js
node --check loadtest/k6/stayops-db-load.js
docker compose --env-file infra/aws/mongo/env.example -f infra/aws/mongo/docker-compose.yml config
docker compose --env-file infra/oracle/app/env.example -f infra/oracle/app/docker-compose.yml config
docker compose --env-file infra/oracle/mock-ota/env.example -f infra/oracle/mock-ota/docker-compose.yml config
./gradlew test --no-daemon
```

판단 기준:

- k6 스크립트 문법 오류가 없어야 한다.
- compose config가 생성되어야 한다.
- Spring Boot 테스트가 성공해야 한다.

## Phase 2. Application 병목 기준선 측정

가설:

```text
MongoDB를 DB 부하 한계로 해석하려면 먼저 App 서버가 같은 부하에서 버티는지 확인해야 한다.
```

액션:

```bash
BASE_URL=https://<api-domain> \
EXPERIMENT_ID=<yyyymmdd-app-baseline-n> \
LOADTEST_PHASE=app-baseline \
TEST_MODE=app-baseline \
LIGHTWEIGHT_RATE=50 \
BUSINESS_RATE=10 \
k6 run loadtest/k6/stayops-app-load.js
```

확인할 메트릭:

- k6: request rate, failed rate, p95/p99 latency, dropped iterations
- App: `http.server.requests`, JVM heap, GC pause, live threads, Tomcat busy threads
- VM: CPU, memory, network, load average
- MongoDB: read control 요청에서 CPU/IO가 과도하게 증가하는지

판단 기준:

```text
App CPU/thread가 먼저 포화되면 DB 한계 실험 전에 App rate를 낮춘다.
MongoDB 지표가 먼저 증가하면 DB 부하 테스트로 넘어간다.
```

## Phase 3. MongoDB 부하 한계 측정

가설:

```text
read-heavy와 write-mixed 부하를 점진적으로 올리면 MongoDB CPU, disk I/O, connection, write concern timeout 중 먼저 악화되는 지표를 찾을 수 있다.
```

액션:

```bash
BASE_URL=https://<api-domain> \
EXPERIMENT_ID=<yyyymmdd-db-ramp-n> \
LOADTEST_PHASE=db-ramp \
TEST_MODE=db-ramp \
PROPERTY_ID=<property-id> \
ROOM_TYPE_ID=<room-type-id> \
CUSTOMER_EMAIL=<customer-email> \
CUSTOMER_PASSWORD=<runtime-password> \
READ_RATE=20 \
WRITE_RATE=2 \
WRITE_DATE_SPREAD_DAYS=90 \
k6 run loadtest/k6/stayops-db-load.js
```

k6 부하 방식:

- `read_heavy`는 한 iteration에서 숙소 목록, 상세, 객실 타입, 재고, 요금을 조회한다.
- `write_mixed`는 한 iteration에서 예약 생성을 한 번 요청한다.
- arrival-rate executor를 사용하므로 k6는 초당 목표 iteration 수를 만들려고 한다.
- `sleep`은 제거했다. arrival-rate에서 sleep은 목표 도착률 해석을 흐릴 수 있다.
- `dropped_iterations`가 늘면 서버 병목이 아니라 k6 실행 머신의 VU 부족일 수 있다.

확인할 로그:

- Spring Boot `X-Experiment-Id`, `phase`, `scenario`가 포함된 JSON 로그
- 예약 생성 시작/성공 로그
- `GlobalExceptionHandler` 오류 로그
- MongoDB slow query, replication lag, write concern timeout, connection 관련 로그

개선 후보:

- 조회 API 인덱스와 쿼리 튜닝
- 읽기/쓰기 요청 rate 분리
- MongoDB connection pool 크기 조정
- App thread pool 조정
- 반복 조회 경로 캐시 도입
- reservation write path의 불필요한 동기 작업 제거

## Phase 4. MongoDB primary failover 실험

가설:

```text
P-S-S replica set에서 primary가 중지되면 남은 secondary 중 하나가 primary로 선출되고, 장애 시간 동안 write 실패와 지연이 관측된다.
```

액션:

1. `TEST_MODE=failover-steady`로 k6를 실행한다.
2. 3분 warm-up 뒤 현재 primary를 확인한다.
3. primary가 있는 MongoDB VM에서 `docker compose stop mongo`를 실행한다.
4. election, App 오류율, write latency를 관찰한다.
5. `docker compose start mongo`로 장애 노드를 복귀시킨다.
6. 복귀 노드가 `SECONDARY`로 돌아오고 replication lag가 줄어드는지 본다.

확인할 메트릭:

- MongoDB `rs.status()`의 member state 변화
- election이 완료될 때까지의 시간
- k6 failed request rate
- write concern timeout
- App MongoDB driver exception
- secondary replication lag
- 복귀 노드의 initial sync 또는 oplog catch-up 여부

판단 기준:

```text
짧은 장애:
- 기존 data directory가 보존되고 oplog window 안에 있으면 SECONDARY로 재합류해야 한다.

긴 장애:
- oplog window를 초과하면 stale member가 될 수 있다.
- 이 경우 자동으로 처리하지 않고, snapshot restore 또는 initial sync runbook으로 전환한다.
```

## 자동 recovery 범위

자동 recovery는 안전한 범위만 포함한다는 말은 다음을 의미한다.

```text
자동화한다:
- Docker restart policy
- MongoDB primary election
- 짧은 장애 후 기존 data directory를 가진 member의 oplog catch-up
- exporter와 log collector 재시작

자동화하지 않는다:
- stale member data directory 삭제
- replica set reconfig
- snapshot restore
- 데이터 정합성 검증 없이 새 노드를 강제로 투입
```

DB는 상태를 가진 시스템이므로, 데이터 삭제나 재동기화는 자동화보다 명시적인 runbook과 운영자 확인이 우선이다.

## 결과 기록 형식

```text
실험 ID:
실행 시각:
Phase:
TEST_MODE:
READ_RATE / WRITE_RATE:
LIGHTWEIGHT_RATE / BUSINESS_RATE:
App p95 / p99:
DB p95 / p99:
failed request rate:
dropped iterations:
App CPU / memory / thread:
Mongo primary CPU / disk I/O:
Mongo secondary replication lag:
장애 주입 시각:
election 완료 시각:
복구 완료 시각:
병목 판단:
다음 개선 후보:
```
