# 부하 테스트와 MongoDB 장애 복구 실험 계획

작성일: 2026-04-19
상태: 구현 준비

## 목적

이번 작업은 포트폴리오 비용 범위 안에서 다음 가설을 검증하기 위한 것이다.

```text
1. App Stack EC2 4 GiB가 기본 요청 부하를 어느 정도 처리하는가
2. 작은 MongoDB EC2 3대가 어느 부하에서 먼저 한계에 닿는가
3. MongoDB primary 장애 시 P-S-S replica set이 자동 failover 되는가
4. 장애 중 들어온 write/read 요청의 오류율과 지연은 어떤가
5. 장애 노드가 복귀했을 때 oplog 기반 recovery로 SECONDARY에 재합류하는가
```

## 최종 인프라 구성

```text
Local PC
+-------------------------------+
| k6                            |
+---------------+---------------+
                |
                | HTTPS
                v
+---------------------------------------------------------+
| AWS App Stack EC2                                       |
|---------------------------------------------------------|
| docker compose: infra/app                               |
|                                                         |
|  ---------      -----------------      -----------      |
| | nginx   | -> | StayOps App     | -> | Redis     |     |
| | 80/443  |    | 8080            |    | session   |     |
|  ---------      -----------------      -----------      |
|      |                                                  |
|      | /mock-ota                                        |
|      v                                                  |
|  ----------------       ---------------------------     |
| | Mock OTA App   | ->  | Mock OTA MongoDB          |    |
| | 8081           |     | local simulation storage  |    |
|  ----------------       ---------------------------     |
|                                                         |
|  ---------------------------------------------------    |
| | Prometheus | Grafana | Loki | Promtail | node-exp |   |
|  ---------------------------------------------------    |
+-------------------------+-------------------------------+
                          |
                          | MongoDB replica set URI
                          v
+----------------------+   +----------------------+   +----------------------+
| AWS MongoDB EC2 1    |   | AWS MongoDB EC2 2    |   | AWS MongoDB EC2 3    |
| mongod + exporter    |<->| mongod + exporter    |<->| mongod + exporter    |
| node-exporter        |   | node-exporter        |   | node-exporter        |
| promtail             |   | promtail             |   | promtail             |
+----------------------+   +----------------------+   +----------------------+
```

## Phase 1. 배포 전 정적 검증

가설:

```text
Compose와 k6 스크립트가 로컬에서 깨지면 배포 이후 문제 원인을 분리하기 어렵다.
```

액션:

```bash
node --check loadtest/k6/stayops-app-load.js
node --check loadtest/k6/stayops-db-load.js
docker compose --env-file infra/app/env.example -f infra/app/docker-compose.yml config
docker compose --env-file infra/mongodb/env.mongo1.example -f infra/mongodb/docker-compose.yml config
docker compose --env-file infra/mongodb/env.mongo2.example -f infra/mongodb/docker-compose.yml config
docker compose --env-file infra/mongodb/env.mongo3.example -f infra/mongodb/docker-compose.yml config
```

## Phase 2. Smoke Test

Smoke test는 본격적인 부하 측정 전, 배포와 설정이 최소 동작하는지 확인하는 짧은 테스트다.

확인 대상:

```text
HTTPS 인증서
Nginx 라우팅
App health
MongoDB 연결
로그 MDC
테스트 데이터 ID
로그인 세션
예약 생성 API
```

## Phase 3. App 병목 기준선 측정

가설:

```text
DB 한계 실험 전에 App Stack EC2가 낮은 부하를 정상 처리해야 한다.
```

액션:

```bash
BASE_URL=https://<api-domain> \
EXPERIMENT_ID=<yyyymmdd-app-baseline-n> \
LOADTEST_PHASE=app-baseline \
TEST_MODE=app-baseline \
LIGHTWEIGHT_RATE=30 \
BUSINESS_RATE=5 \
k6 run loadtest/k6/stayops-app-load.js
```

확인할 메트릭:

- k6: failed rate, p95/p99 latency, dropped iterations
- App: HTTP latency, JVM heap, GC pause, Tomcat busy threads
- App Stack EC2: CPU, memory, network, load average

## Phase 4. MongoDB 부하 한계 측정

가설:

```text
t3.micro MongoDB 3대는 낮은 write/read 부하에서도 CPU, memory, disk I/O, connection, replication lag 중 하나가 먼저 한계에 닿을 수 있다.
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
READ_RATE=10 \
WRITE_RATE=1 \
WRITE_DATE_SPREAD_DAYS=90 \
k6 run loadtest/k6/stayops-db-load.js
```

판단 기준:

```text
App Stack EC2가 먼저 포화:
- App Stack 스펙 또는 App thread/connection 설정을 본다.

MongoDB EC2가 먼저 포화:
- MongoDB CPU, memory, disk I/O, replication lag, write concern timeout을 본다.

k6 dropped_iterations 증가:
- 로컬 PC가 목표 부하를 만들지 못하는지 먼저 확인한다.
```

## Phase 5. MongoDB primary failover 실험

가설:

```text
P-S-S replica set에서 primary가 중지되면 남은 secondary 중 하나가 primary로 선출되고, 장애 시간 동안 write 실패와 지연이 관측된다.
```

액션:

1. 로컬 PC에서 `TEST_MODE=failover-steady`로 k6를 실행한다.
2. 3분 warm-up 뒤 현재 primary를 확인한다.
3. primary가 있는 MongoDB EC2에서 `docker compose stop mongo`를 실행한다.
4. election, App 오류율, write latency를 관찰한다.
5. `docker compose start mongo`로 장애 노드를 복귀시킨다.
6. 복귀 노드가 `SECONDARY`로 돌아오고 replication lag가 줄어드는지 본다.

확인할 메트릭:

- `rs.status()` member state
- election 시간
- k6 failed request rate
- write concern timeout
- App MongoDB driver exception
- replication lag
- 복귀 노드의 oplog catch-up 여부

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
App Stack EC2 CPU / memory:
Mongo primary CPU / memory / disk I/O:
Mongo secondary replication lag:
장애 주입 시각:
election 완료 시각:
복구 완료 시각:
병목 판단:
다음 개선 후보:
```
