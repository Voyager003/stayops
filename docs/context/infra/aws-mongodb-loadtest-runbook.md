# AWS MongoDB Load Test Runbook

작성일: 2026-04-17
상태: 초안

## 목적

이 문서는 StayOps 인프라를 AWS Boot EC2, AWS Mongo EC2 2대, Oracle Mock OTA VM으로 배포한 뒤 MongoDB 부하와 장애 복구를 측정하기 위한 실행 절차를 정의한다.

계획 문서가 구조와 판단 근거를 설명한다면, 이 문서는 실제 실행 순서를 고정한다.

## 전제

- Boot EC2는 public subnet에 둔다.
- Mongo EC2 1, Mongo EC2 2는 private subnet에 둔다.
- MongoDB는 `Primary-Secondary-Arbiter` 구성으로 시작한다.
- Boot EC2의 `mongo-arbiter`는 투표만 담당하며 데이터를 저장하지 않는다.
- Redis는 이번 단계에서 단일 컨테이너로 유지한다.
- Mock OTA는 Oracle VM에서 HTTPS endpoint로 분리한다.
- k6는 Boot EC2가 아닌 로컬 또는 별도 load generator에서 실행한다.

## 0. 배포 전 준비 체크리스트

배포 전에 다음 항목을 먼저 확정한다. 이 단계가 끝나기 전에는 compose를 실행하지 않는다.

### 인스턴스

- AWS Boot EC2 1대: public subnet, public IP 또는 DNS 연결 가능
- AWS Mongo EC2 1대: private subnet, Boot EC2와 Mongo EC2 2에서 접근 가능
- AWS Mongo EC2 2대: private subnet, Boot EC2와 Mongo EC2 1에서 접근 가능
- Oracle Mock OTA VM 1대: public HTTPS endpoint 연결 가능
- 운영자 로컬 또는 별도 load generator: k6 실행 위치

### 네트워크

- Boot EC2에서 Mongo EC2 1, 2의 private IP `27017`에 접근 가능해야 한다.
- Mongo EC2 1과 Mongo EC2 2는 서로의 private IP `27017`에 접근 가능해야 한다.
- Mongo EC2 1, 2에서 Boot EC2의 private IP `27017`에 접근 가능해야 한다. 이는 arbiter 통신을 위한 경로다.
- Boot EC2에서 Mongo EC2 1, 2의 `9100`, `9216` exporter port에 접근 가능해야 한다.
- Oracle Mock OTA VM은 Boot API public HTTPS endpoint로 webhook을 보낼 수 있어야 한다.
- Boot EC2는 Oracle Mock OTA public HTTPS endpoint로 API 요청을 보낼 수 있어야 한다.

### 런타임

- 모든 VM에 Docker와 Docker Compose plugin을 설치한다.
- 각 VM의 system clock이 동기화되어 있어야 한다. TLS, log timestamp, MongoDB election 분석에 필요하다.
- Mongo EC2 1, 2의 disk 여유 공간과 I/O 한계를 기록한다. 부하 테스트 결과 해석 기준이 된다.
- Boot EC2의 CPU, memory, network baseline을 기록한다. App 병목과 DB 병목을 분리하기 위한 기준이다.

### 배포 파일

- Boot EC2에는 `infra/aws/boot` 파일과 커밋하지 않는 `deploy.env`를 둔다.
- Mongo EC2 1, 2에는 `infra/aws/mongo` 파일을 둔다.
- Oracle Mock OTA VM에는 `infra/oracle/mock-ota` 파일과 커밋하지 않는 `deploy.env`, `.htpasswd`를 둔다.
- 실제 secret 값은 Git, 문서, issue, PR, k6 output에 남기지 않는다.

## 1. 배포 전 로컬 검증

로컬에서는 실제 성능을 측정하지 않는다. 배포 전에 스크립트와 compose 구성이 깨지지 않았는지만 확인한다.

```bash
node --check loadtest/k6/stayops-app-load.js
node --check loadtest/k6/stayops-db-load.js
docker compose --env-file infra/aws/env.example -f infra/aws/boot/docker-compose.yml config
docker compose --env-file infra/oracle/mock-ota/env.example -f infra/oracle/mock-ota/docker-compose.yml config
LOKI_URL=http://boot:3100/loki/api/v1/push HOSTNAME=mongo1 \
  docker compose -f infra/aws/mongo/docker-compose.yml config
./gradlew test --no-daemon
```

## 2. 인스턴스 역할

```text
AWS Boot EC2
- infra/aws/boot/docker-compose.yml
- StayOps app
- Nginx
- Redis
- Mongo arbiter
- Prometheus / Grafana / Loki

AWS Mongo EC2 1
- infra/aws/mongo/docker-compose.yml
- MongoDB data node
- mongodb-exporter
- node-exporter
- promtail

AWS Mongo EC2 2
- infra/aws/mongo/docker-compose.yml
- MongoDB data node
- mongodb-exporter
- node-exporter
- promtail

Oracle Mock OTA VM
- infra/oracle/mock-ota/docker-compose.yml
- Mock OTA app
- Mock OTA MongoDB
- Nginx / HTTPS
```

## 3. Security Group 확인

Boot EC2 inbound:

- `80`, `443`: public
- `22`: operator IP only
- `27017`: Mongo EC2 private IP only
- `3001`, `9090`, `3100`: public open 금지. SSH tunnel 또는 제한된 operator IP만 허용
- `9100`: public open 금지. 필요하면 operator IP만 임시 허용

Mongo EC2 inbound:

- `27017`: Boot EC2, Mongo EC2 peer only
- `9100`, `9216`: Boot EC2 only
- `22`: operator IP 또는 bastion only

Oracle Mock OTA inbound:

- `80`, `443`: public
- `22`: operator IP only
- `9100`: 필요 시 Boot EC2 또는 operator IP only

Outbound는 기본 허용으로 시작하되, 테스트가 끝난 뒤 최소 범위로 줄인다. 특히 MongoDB `27017`, Prometheus exporter port, Loki ingestion port가 public internet에 열려 있으면 안 된다.

보안그룹 확인 기준:

```text
public 허용:
- Boot Nginx 80/443
- Oracle Mock OTA Nginx 80/443

operator만 허용:
- SSH 22
- Grafana 3001
- Prometheus 9090
- Loki 3100

private만 허용:
- MongoDB 27017
- node-exporter 9100
- mongodb-exporter 9216
```

## 4. Mongo EC2 배포

Mongo EC2 1과 Mongo EC2 2에 동일한 compose를 배포한다. 단, `HOSTNAME`은 각 노드에서 다르게 둔다.

```bash
cd infra/aws/mongo
LOKI_URL=http://<boot-private-ip>:3100/loki/api/v1/push HOSTNAME=mongo1 docker compose up -d
```

Mongo EC2 2:

```bash
cd infra/aws/mongo
LOKI_URL=http://<boot-private-ip>:3100/loki/api/v1/push HOSTNAME=mongo2 docker compose up -d
```

확인:

```bash
docker compose ps
docker compose logs mongo --tail=100
```

## 5. Boot EC2 배포

Boot EC2에는 실제 런타임 값을 담은 `deploy.env` 파일을 별도로 둔다. 이 파일은 Git에 커밋하지 않는다.

작성 기준:

- `infra/aws/env.example`을 기준으로 필요한 key 목록만 맞춘다.
- `change-me`, `replace-with-runtime-secret`, `example.com` 값은 실제 런타임 값으로 교체한다.
- secret 값은 문서, 이슈, PR, commit message, k6 output에 남기지 않는다.
- `TOSS_SECRET_KEY`, `GRAFANA_PASSWORD`는 운영자가 VM 안에서만 주입한다.
- MongoDB URI에는 `replicaSet=rs0`, `w=majority`, `readPreference=primary`를 유지한다.
- `deploy.env`, `.htpasswd`, private key 파일은 `.gitignore` 대상이다.

필수 값:

- `STAYOPS_IMAGE`
- `SPRING_MONGODB_URI`
- `TOSS_SECRET_KEY`
- `MOCK_OTA_ENDPOINT`
- `MONGO1_HOST`
- `MONGO2_HOST`
- `GRAFANA_PASSWORD`

배포:

```bash
cd infra/aws/boot
docker compose --env-file deploy.env up -d
```

확인:

```bash
docker compose ps
curl -f http://localhost:8080/actuator/health
curl -f http://localhost:8080/actuator/prometheus
```

외부에서는 `/actuator/prometheus`가 Nginx에서 차단되어야 한다.

```bash
curl -i https://<api-domain>/actuator/prometheus
```

기대 결과는 `404`이다.

## 6. Replica Set 초기화

초기화는 Mongo EC2 중 한 곳에서 한 번만 수행한다.

```bash
cd infra/aws/mongo
docker compose exec \
  -e MONGO_REPLICA_SET=rs0 \
  -e MONGO1_HOST=<mongo1-private-ip> \
  -e MONGO2_HOST=<mongo2-private-ip> \
  -e MONGO_ARBITER_HOST=<boot-private-ip> \
  mongo mongosh /opt/stayops/init-replica-set.js
```

검증:

```bash
docker compose exec mongo mongosh --eval "rs.status()"
docker compose exec mongo mongosh --eval "db.hello()"
```

확인 기준:

- data node 1대가 `PRIMARY`
- data node 1대가 `SECONDARY`
- Boot EC2 arbiter가 `ARBITER`

## 7. Oracle Mock OTA 배포

Oracle VM에는 Mock OTA runtime env와 Basic Auth 파일을 별도로 둔다.

작성 기준:

- `infra/oracle/mock-ota/env.example`을 기준으로 필요한 key 목록만 맞춘다.
- `MOCK_OTA_PMS_WEBHOOK_URL`은 Boot API domain을 향하게 한다.
- `MOCK_OTA_HTPASSWD_PATH`는 Git에 커밋하지 않는 `.htpasswd` 파일을 가리키게 한다.
- Basic Auth 계정과 비밀번호는 문서와 로그에 남기지 않는다.

```bash
cd infra/oracle/mock-ota
docker compose --env-file deploy.env up -d
```

확인:

```bash
docker compose ps
curl -f https://<mock-ota-domain>/actuator/health
```

PMS webhook URL은 Boot API domain을 향해야 한다.

## 8. 관측 계층 확인

Boot EC2에서 확인한다.

```bash
curl -f http://localhost:9090/-/ready
curl -f http://localhost:3100/ready
```

Prometheus target에서 다음 scrape가 `UP`인지 확인한다.

- StayOps app
- Boot node-exporter
- Mongo EC2 1 node-exporter
- Mongo EC2 2 node-exporter
- Mongo EC2 1 mongodb-exporter
- Mongo EC2 2 mongodb-exporter

Grafana는 public expose 대신 SSH tunnel로 확인한다.

```bash
ssh -L 3001:localhost:3001 <boot-ec2>
```

## 9. App 부하 테스트

먼저 Application이 MongoDB보다 먼저 병목이 되는지 확인한다.

```bash
BASE_URL=https://<api-domain> \
TEST_MODE=baseline \
LIGHTWEIGHT_RATE=50 \
BUSINESS_RATE=10 \
k6 run loadtest/k6/stayops-app-load.js
```

판단:

- App CPU, Tomcat busy thread, JVM GC가 먼저 증가하면 Boot EC2 또는 App thread-pool이 병목이다.
- App이 안정적이고 MongoDB CPU/IO가 증가하면 다음 DB 부하 테스트로 넘어간다.

## 10. DB 부하 테스트

먼저 smoke로 배포 상태를 확인한다.

```bash
BASE_URL=https://<api-domain> \
TEST_MODE=smoke \
PROPERTY_ID=<property-id> \
ROOM_TYPE_ID=<room-type-id> \
CUSTOMER_EMAIL=<customer-email> \
CUSTOMER_PASSWORD=<customer-password> \
READ_RATE=2 \
WRITE_RATE=1 \
k6 run loadtest/k6/stayops-db-load.js
```

이후 baseline, ramp 순으로 올린다.

```bash
BASE_URL=https://<api-domain> \
TEST_MODE=ramp \
PROPERTY_ID=<property-id> \
ROOM_TYPE_ID=<room-type-id> \
CUSTOMER_EMAIL=<customer-email> \
CUSTOMER_PASSWORD=<customer-password> \
READ_RATE=20 \
WRITE_RATE=2 \
k6 run loadtest/k6/stayops-db-load.js
```

## 11. MongoDB Failover 테스트

failover는 steady window가 필요하므로 `TEST_MODE=failover`로 실행한다.

```bash
BASE_URL=https://<api-domain> \
TEST_MODE=failover \
PROPERTY_ID=<property-id> \
ROOM_TYPE_ID=<room-type-id> \
CUSTOMER_EMAIL=<customer-email> \
CUSTOMER_PASSWORD=<customer-password> \
READ_RATE=20 \
WRITE_RATE=2 \
k6 run loadtest/k6/stayops-db-load.js
```

실행 후 3분 warm-up이 끝나면 현재 primary를 확인한다.

```bash
docker compose exec mongo mongosh --eval "rs.status().members.map(m => ({ name: m.name, stateStr: m.stateStr }))"
```

primary가 있는 Mongo EC2에서 mongod를 중지한다.

```bash
docker compose stop mongo
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
docker compose start mongo
```

복구 후 `rs.status()`로 PRIMARY, SECONDARY, ARBITER 상태가 정상인지 확인한다.

## 12. 결과 기록 기준

각 테스트는 같은 형식으로 기록한다.

```text
테스트명:
실행 시각:
TEST_MODE:
READ_RATE / WRITE_RATE 또는 LIGHTWEIGHT_RATE / BUSINESS_RATE:
App p95 / p99:
DB p95 / p99:
failed request rate:
Boot EC2 CPU / memory:
Mongo primary CPU / disk I/O:
Mongo secondary CPU / replication lag:
병목 판단:
다음 개선 후보:
```

## 13. 종료 절차

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

데이터를 유지해야 하는 MongoDB volume은 삭제하지 않는다. 단, 테스트용 데이터만 있다면 snapshot 보존 여부를 먼저 결정한다.
