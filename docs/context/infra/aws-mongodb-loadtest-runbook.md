# AWS MongoDB Load Test Runbook

작성일: 2026-04-17
수정일: 2026-04-19
상태: 초안

## 목적

이 문서는 StayOps를 Oracle App VM, AWS MongoDB VM 3대, Oracle Mock OTA VM으로 배포한 뒤 MongoDB 부하와 장애 복구를 측정하기 위한 실행 절차를 정의한다.

계획 문서가 구조와 판단 근거를 설명한다면, 이 문서는 실제 실행 순서를 고정한다. VM에 Docker를 설치하고 compose를 실행하는 작업은 PR 이후 배포 단계에서 수행한다.

## 전제

- App 서버는 Oracle VM에 둔다.
- MongoDB는 AWS VM 3대에 각각 1개 `mongod` 컨테이너로 둔다.
- MongoDB replica set은 `Primary - Secondary - Secondary` 구성이다.
- Redis는 이번 단계에서 App VM의 단일 컨테이너로 유지한다.
- Mock OTA는 Oracle VM에서 HTTPS endpoint로 분리한다.
- k6는 App VM이 아닌 로컬 또는 별도 load generator에서 실행한다.
- MongoDB runtime secret, keyfile, deploy.env는 커밋하지 않는다.

## 0. 배포 전 준비 체크리스트

### 인스턴스

- Oracle App VM 1대: public HTTPS endpoint 연결 가능
- AWS MongoDB VM 1대: MongoDB data-bearing voting member
- AWS MongoDB VM 2대: MongoDB data-bearing voting member
- AWS MongoDB VM 3대: MongoDB data-bearing voting member
- Oracle Mock OTA VM 1대: public HTTPS endpoint 연결 가능
- 운영자 로컬 또는 별도 load generator: k6 실행 위치

### 네트워크

- Oracle App VM에서 AWS MongoDB VM 1, 2, 3의 `27017`에 접근 가능해야 한다.
- MongoDB VM 1, 2, 3은 서로의 `27017`에 접근 가능해야 한다.
- Oracle App VM의 Prometheus에서 MongoDB VM 1, 2, 3의 `9100`, `9216` exporter port에 접근 가능해야 한다.
- Oracle Mock OTA VM은 App API public HTTPS endpoint로 webhook을 보낼 수 있어야 한다.
- App VM은 Oracle Mock OTA public HTTPS endpoint로 API 요청을 보낼 수 있어야 한다.
- MongoDB `27017`, exporter port는 public 전체에 열지 않는다. App VM, MongoDB peer, 운영자 IP만 허용한다.

### 런타임

- 모든 VM에 Docker와 Docker Compose plugin을 설치한다.
- 각 VM의 system clock이 동기화되어 있어야 한다.
- App VM과 Mock OTA VM에는 TLS 인증서 발급 도구를 준비한다.
- MongoDB VM의 disk 여유 공간과 I/O 한계를 기록한다.
- App VM의 CPU, memory, network baseline을 기록한다.

### 배포 파일

- Oracle App VM: `infra/app`, 커밋하지 않는 `deploy.env`
- AWS MongoDB VM 1, 2, 3: `infra/mongodb`, 커밋하지 않는 `deploy.env`, 커밋하지 않는 `mongo-keyfile`
- Oracle Mock OTA VM: `infra/mock-ota`, 커밋하지 않는 `deploy.env`, `.htpasswd`
- 실제 secret 값은 Git, 문서, issue, PR, k6 output에 남기지 않는다.

## 1. 배포 전 로컬 검증

로컬에서는 실제 성능을 측정하지 않는다. 배포 전에 스크립트와 compose 구성이 깨지지 않았는지만 확인한다.

```bash
node --check loadtest/k6/stayops-app-load.js
node --check loadtest/k6/stayops-db-load.js
docker compose --env-file infra/mongodb/env.example -f infra/mongodb/docker-compose.yml config
docker compose --env-file infra/app/env.example -f infra/app/docker-compose.yml config
docker compose --env-file infra/mock-ota/env.example -f infra/mock-ota/docker-compose.yml config
./gradlew test --no-daemon
```

## 2. 인스턴스 역할

```text
Oracle App VM
- infra/app/docker-compose.yml
- StayOps app
- Nginx
- Redis
- Prometheus / Grafana / Loki
- Promtail / node-exporter

AWS MongoDB VM 1
- infra/mongodb/docker-compose.yml
- MongoDB data node
- mongodb-exporter
- node-exporter
- promtail

AWS MongoDB VM 2
- infra/mongodb/docker-compose.yml
- MongoDB data node
- mongodb-exporter
- node-exporter
- promtail

AWS MongoDB VM 3
- infra/mongodb/docker-compose.yml
- MongoDB data node
- mongodb-exporter
- node-exporter
- promtail

Oracle Mock OTA VM
- infra/mock-ota/docker-compose.yml
- Mock OTA app
- Mock OTA MongoDB
- Nginx / HTTPS
- node-exporter / promtail
```

## 3. Security Group / 방화벽 확인

Oracle App VM inbound:

- `80`, `443`: public
- `22`: operator IP only
- `3001`, `9090`, `3100`: public open 금지. SSH tunnel 또는 제한된 operator IP만 허용
- `9100`: public open 금지

AWS MongoDB VM inbound:

- `27017`: Oracle App VM, MongoDB peer, operator IP only
- `9100`, `9216`: Oracle App VM only
- `22`: operator IP only

Oracle Mock OTA VM inbound:

- `80`, `443`: public
- `22`: operator IP only
- `9100`: 필요 시 App VM 또는 operator IP only

Outbound는 기본 허용으로 시작하되, 테스트 후 최소 범위로 줄인다.

## 4. MongoDB keyfile 준비

MongoDB replica set에서 `--auth`와 내부 member 인증을 함께 사용하므로 모든 MongoDB VM은 같은 keyfile을 가져야 한다. keyfile은 Git에 커밋하지 않는다.

예시 절차:

```bash
cd infra/mongodb
openssl rand -base64 756 > mongo-keyfile
chmod 400 mongo-keyfile
```

생성한 `mongo-keyfile`은 MongoDB VM 1, 2, 3에 같은 내용으로 배치한다.

## 5. MongoDB VM 배포

각 MongoDB VM에는 `infra/mongodb/env.example`을 기준으로 `deploy.env`를 만든다.

작성 기준:

- `HOSTNAME`은 각 노드에서 다르게 둔다. 예: `mongo1`, `mongo2`, `mongo3`
- `MONGO1_HOST`, `MONGO2_HOST`, `MONGO3_HOST`는 세 VM에서 같은 값으로 둔다.
- `MONGO_INITDB_ROOT_USERNAME`, `MONGO_INITDB_ROOT_PASSWORD`는 root 계정 생성에 사용한다.
- `MONGO_APP_USERNAME`, `MONGO_APP_PASSWORD`는 App URI와 일치해야 한다.
- `MONGO_EXPORTER_USERNAME`, `MONGO_EXPORTER_PASSWORD`는 exporter URI와 일치해야 한다.
- `LOKI_URL`은 App VM의 Loki endpoint를 향하게 한다.

각 VM에서 실행:

```bash
cd infra/mongodb
docker compose --env-file deploy.env up -d
docker compose ps
docker compose logs mongo --tail=100
```

## 6. Replica Set 초기화

초기화는 MongoDB VM 중 한 곳에서 한 번만 수행한다. root 계정으로 인증한 뒤 실행한다.

```bash
cd infra/mongodb
docker compose --env-file deploy.env exec \
  -e MONGO_REPLICA_SET=rs0 \
  -e MONGO1_HOST=<mongo1-host> \
  -e MONGO2_HOST=<mongo2-host> \
  -e MONGO3_HOST=<mongo3-host> \
  mongo mongosh \
  -u <root-user> \
  -p <root-password> \
  --authenticationDatabase admin \
  /opt/stayops/init-replica-set.js
```

애플리케이션과 exporter 계정을 생성한다.

```bash
docker compose --env-file deploy.env exec \
  -e MONGO_APP_USERNAME=<app-user> \
  -e MONGO_APP_PASSWORD=<app-password> \
  -e MONGO_EXPORTER_USERNAME=<exporter-user> \
  -e MONGO_EXPORTER_PASSWORD=<exporter-password> \
  mongo mongosh \
  -u <root-user> \
  -p <root-password> \
  --authenticationDatabase admin \
  /opt/stayops/create-users.js
```

검증:

```bash
docker compose --env-file deploy.env exec mongo mongosh \
  -u <root-user> \
  -p <root-password> \
  --authenticationDatabase admin \
  --eval "rs.status()"
```

확인 기준:

- data node 1대가 `PRIMARY`
- data node 2대가 `SECONDARY`
- arbiter가 없어야 한다.

## 7. Oracle App VM 배포

App VM에는 실제 런타임 값을 담은 `deploy.env` 파일을 별도로 둔다.

작성 기준:

- `infra/app/env.example`을 기준으로 필요한 key 목록만 맞춘다.
- `change-me`, `replace-with-runtime-secret`, `example.com` 값은 실제 런타임 값으로 교체한다.
- `API_DOMAIN`은 Nginx `server_name`과 Let's Encrypt certificate path에 사용되므로 실제 API domain과 일치해야 한다.
- `SPRING_MONGODB_URI`에는 MongoDB 3대 host, `replicaSet=rs0`, `w=majority`, `readPreference=primary`, `retryWrites=true`, `authSource=admin`을 유지한다.
- secret 값은 문서, 이슈, PR, commit message, k6 output에 남기지 않는다.

TLS 인증서를 먼저 확인한다.

```bash
sudo test -f /etc/letsencrypt/live/<api-domain>/fullchain.pem
sudo test -f /etc/letsencrypt/live/<api-domain>/privkey.pem
```

인증서가 없다면 DNS가 App VM을 향하는지 확인한 뒤 먼저 발급한다.

```bash
sudo certbot certonly --standalone -d <api-domain>
```

배포:

```bash
cd infra/app
docker compose --env-file deploy.env up -d
docker compose ps
```

확인:

```bash
curl -f http://localhost:8080/actuator/health
curl -f http://localhost:8080/actuator/prometheus
curl -i https://<api-domain>/actuator/prometheus
```

외부 `/actuator/prometheus` 기대 결과는 `404`이다.

## 8. Oracle Mock OTA 배포

Oracle Mock OTA VM에는 runtime env와 Basic Auth 파일을 별도로 둔다.

작성 기준:

- `infra/mock-ota/env.example`을 기준으로 필요한 key 목록만 맞춘다.
- `MOCK_OTA_DOMAIN`은 실제 Mock OTA domain과 일치해야 한다.
- `MOCK_OTA_PMS_WEBHOOK_URL`은 App API domain을 향하게 한다.
- `MOCK_OTA_HTPASSWD_PATH`는 Git에 커밋하지 않는 `.htpasswd` 파일을 가리키게 한다.

```bash
sudo certbot certonly --standalone -d <mock-ota-domain>

cd infra/mock-ota
docker compose --env-file deploy.env up -d
docker compose ps
curl -f https://<mock-ota-domain>/actuator/health
```

## 9. 관측 계층 확인

App VM에서 확인한다.

```bash
curl -f http://localhost:9090/-/ready
curl -f http://localhost:3100/ready
```

Prometheus target에서 다음 scrape가 `UP`인지 확인한다.

- StayOps app
- App node-exporter
- MongoDB VM 1, 2, 3 node-exporter
- MongoDB VM 1, 2, 3 mongodb-exporter

Grafana는 public expose 대신 SSH tunnel로 확인한다.

```bash
ssh -L 3001:localhost:3001 <app-vm>
```

## 10. 부하 테스트 시작 전 최종 점검

- `rs.status()`에서 `PRIMARY`, `SECONDARY`, `SECONDARY`가 확인된다.
- Prometheus target에서 App, MongoDB exporters, node exporters가 `UP`이다.
- Loki가 App, MongoDB, Mock OTA Docker log를 수집한다.
- k6 실행 위치는 App VM이 아니다.
- 부하 테스트 대상 데이터의 `PROPERTY_ID`, `ROOM_TYPE_ID`, customer 계정, 요금이 준비되어 있다.
- `WRITE_DATE_SPREAD_DAYS` 범위를 감당할 테스트 데이터 기간을 준비했다.

## 11. App 부하 테스트

먼저 Application이 MongoDB보다 먼저 병목이 되는지 확인한다.

```bash
BASE_URL=https://<api-domain> \
EXPERIMENT_ID=<yyyymmdd-app-baseline-n> \
LOADTEST_PHASE=app-baseline \
TEST_MODE=app-baseline \
LIGHTWEIGHT_RATE=50 \
BUSINESS_RATE=10 \
k6 run loadtest/k6/stayops-app-load.js
```

판단:

- App CPU, Tomcat busy thread, JVM GC가 먼저 증가하면 App VM 또는 thread-pool이 병목이다.
- App이 안정적이고 MongoDB CPU/IO가 증가하면 DB 부하 테스트로 넘어간다.

## 12. DB 부하 테스트

Smoke:

```bash
BASE_URL=https://<api-domain> \
EXPERIMENT_ID=<yyyymmdd-db-smoke-n> \
LOADTEST_PHASE=smoke \
TEST_MODE=smoke \
PROPERTY_ID=<property-id> \
ROOM_TYPE_ID=<room-type-id> \
CUSTOMER_EMAIL=<customer-email> \
CUSTOMER_PASSWORD=<customer-password> \
READ_RATE=2 \
WRITE_RATE=1 \
WRITE_DATE_SPREAD_DAYS=30 \
k6 run loadtest/k6/stayops-db-load.js
```

Ramp:

```bash
BASE_URL=https://<api-domain> \
EXPERIMENT_ID=<yyyymmdd-db-ramp-n> \
LOADTEST_PHASE=db-ramp \
TEST_MODE=db-ramp \
PROPERTY_ID=<property-id> \
ROOM_TYPE_ID=<room-type-id> \
CUSTOMER_EMAIL=<customer-email> \
CUSTOMER_PASSWORD=<customer-password> \
READ_RATE=20 \
WRITE_RATE=2 \
WRITE_DATE_SPREAD_DAYS=90 \
k6 run loadtest/k6/stayops-db-load.js
```

## 13. MongoDB Failover 테스트

failover는 steady window가 필요하므로 `TEST_MODE=failover-steady`로 실행한다.

```bash
BASE_URL=https://<api-domain> \
EXPERIMENT_ID=<yyyymmdd-failover-n> \
LOADTEST_PHASE=failover-steady \
TEST_MODE=failover-steady \
PROPERTY_ID=<property-id> \
ROOM_TYPE_ID=<room-type-id> \
CUSTOMER_EMAIL=<customer-email> \
CUSTOMER_PASSWORD=<customer-password> \
READ_RATE=20 \
WRITE_RATE=2 \
WRITE_DATE_SPREAD_DAYS=90 \
k6 run loadtest/k6/stayops-db-load.js
```

실행 후 3분 warm-up이 끝나면 현재 primary를 확인한다.

```bash
docker compose --env-file deploy.env exec mongo mongosh \
  -u <root-user> \
  -p <root-password> \
  --authenticationDatabase admin \
  --eval "rs.status().members.map(m => ({ name: m.name, stateStr: m.stateStr }))"
```

primary가 있는 MongoDB VM에서 mongod를 중지한다.

```bash
docker compose --env-file deploy.env stop mongo
```

관찰 항목:

- election에 걸린 시간
- k6 failed request rate
- p95/p99 latency 증가폭
- write concern timeout 발생 여부
- Spring Boot MongoDB driver error
- MongoDB replication lag

복구:

```bash
docker compose --env-file deploy.env start mongo
```

복구 후 `rs.status()`로 PRIMARY, SECONDARY, SECONDARY 상태가 정상인지 확인한다.

## 14. 결과 기록 기준

```text
테스트명:
실행 시각:
EXPERIMENT_ID:
TEST_MODE:
READ_RATE / WRITE_RATE:
LIGHTWEIGHT_RATE / BUSINESS_RATE:
App p95 / p99:
DB p95 / p99:
failed request rate:
dropped iterations:
App VM CPU / memory:
Mongo primary CPU / disk I/O:
Mongo secondary CPU / replication lag:
장애 주입 시각:
election 완료 시각:
복구 완료 시각:
병목 판단:
다음 개선 후보:
```

## 15. 종료 절차

테스트 종료 후 비용이 계속 발생하지 않도록 확인한다.

```bash
docker compose ps
docker compose down
```

AWS 콘솔에서 확인한다.

- EC2 중지 여부
- 사용하지 않는 Public IPv4
- NAT Gateway
- EBS volume
- snapshot

MongoDB volume은 데이터 보존 여부를 먼저 결정한 뒤 삭제한다.
