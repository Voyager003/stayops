# AWS MongoDB Load Test Runbook

작성일: 2026-04-17
수정일: 2026-04-19
상태: 초안

## 목적

이 문서는 로컬 PC에서 k6를 실행하고, AWS App Stack EC2 1대와 AWS MongoDB EC2 3대로 부하 테스트와 MongoDB failover/recovery를 검증하는 절차를 정의한다.

## 최종 전제

- k6는 로컬 PC에서 실행한다.
- App Stack EC2는 4 GiB 메모리로 시작한다.
- MongoDB EC2 1/2/3은 가능한 작은 메모리로 시작한다.
- App Stack EC2에는 StayOps App, Mock OTA, Redis, Mock OTA MongoDB, Prometheus, Loki, Grafana를 함께 둔다.
- Mock OTA는 별도 도메인 없이 `https://api.<domain>/mock-ota` 경로로 제공한다.
- MongoDB replica set은 `Primary - Secondary - Secondary` 구성이다.
- MongoDB runtime secret, keyfile, deploy.env, `.htpasswd`는 커밋하지 않는다.

## 0. 인스턴스 생성

초기 권장값:

```text
App Stack EC2:
- t3.medium
- 2 vCPU / 4 GiB
- EBS 30 GiB 이상

MongoDB EC2 1:
- t3.micro
- 2 vCPU / 1 GiB
- EBS 30 GiB 이상

MongoDB EC2 2:
- t3.micro
- 2 vCPU / 1 GiB
- EBS 30 GiB 이상

MongoDB EC2 3:
- t3.micro
- 2 vCPU / 1 GiB
- EBS 30 GiB 이상
```

MongoDB가 `t3.micro`에서 OOM 또는 기동 불안정으로 failover/recovery 실험 자체가 어려워지면 `t3.small`로 올리고, 그 판단을 결과에 기록한다.

## 1. Security Group

App Stack EC2 inbound:

```text
80   from 0.0.0.0/0
443  from 0.0.0.0/0
22   from operator IP only
3001 from operator IP only 또는 SSH tunnel
9090 from operator IP only 또는 SSH tunnel
3100 from operator IP only 또는 SSH tunnel
9100 public open 금지
```

MongoDB EC2 inbound:

```text
27017 from App Stack EC2 Security Group
27017 from MongoDB EC2 Security Group
27017 from operator IP only, 필요 시 임시
9216  from App Stack EC2 Security Group
9100  from App Stack EC2 Security Group
22    from operator IP only
```

MongoDB `27017`, `9100`, `9216`을 public 전체에 열지 않는다.

## 2. 모든 EC2 공통 초기 설정

```bash
sudo timedatectl set-timezone Asia/Seoul
timedatectl
```

이후 모든 EC2에 Docker와 Docker Compose plugin을 설치한다.

확인:

```bash
docker --version
docker compose version
df -h
free -h
```

## 3. 배포 전 로컬 검증

로컬 PC에서 실행한다.

```bash
node --check loadtest/k6/stayops-app-load.js
node --check loadtest/k6/stayops-db-load.js
docker compose --env-file infra/app/env.example -f infra/app/docker-compose.yml config
docker compose --env-file infra/mongodb/env.mongo1.example -f infra/mongodb/docker-compose.yml config
docker compose --env-file infra/mongodb/env.mongo2.example -f infra/mongodb/docker-compose.yml config
docker compose --env-file infra/mongodb/env.mongo3.example -f infra/mongodb/docker-compose.yml config
```

## 4. MongoDB keyfile 준비

모든 MongoDB EC2는 같은 keyfile을 가져야 한다.

```bash
cd infra/mongodb
openssl rand -base64 756 > mongo-keyfile
chmod 400 mongo-keyfile
```

생성한 `mongo-keyfile`을 MongoDB EC2 1/2/3의 `infra/mongodb/mongo-keyfile`에 같은 내용으로 배치한다.

## 5. MongoDB EC2 배포

세 MongoDB EC2는 같은 compose를 사용하고, `deploy.env`의 `HOSTNAME`만 다르게 둔다.

```text
MongoDB EC2 1:
- infra/mongodb/env.mongo1.example 기준
- HOSTNAME=mongo1

MongoDB EC2 2:
- infra/mongodb/env.mongo2.example 기준
- HOSTNAME=mongo2

MongoDB EC2 3:
- infra/mongodb/env.mongo3.example 기준
- HOSTNAME=mongo3
```

각 MongoDB EC2에서 실행:

```bash
cd infra/mongodb
docker compose --env-file deploy.env up -d
docker compose ps
docker compose logs mongo --tail=100
```

## 6. Replica Set 초기화

MongoDB EC2 중 한 곳에서 한 번만 실행한다.

```bash
cd infra/mongodb
docker compose --env-file deploy.env exec \
  -e MONGO_REPLICA_SET=rs0 \
  -e MONGO1_HOST=<mongo1-private-ip> \
  -e MONGO2_HOST=<mongo2-private-ip> \
  -e MONGO3_HOST=<mongo3-private-ip> \
  mongo mongosh \
  -u <root-user> \
  -p <root-password> \
  --authenticationDatabase admin \
  /opt/stayops/init-replica-set.js
```

App/exporter 계정도 한 번만 생성한다.

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

확인:

```bash
docker compose --env-file deploy.env exec mongo mongosh \
  -u <root-user> \
  -p <root-password> \
  --authenticationDatabase admin \
  --eval "rs.status().members.map(m => ({ name: m.name, stateStr: m.stateStr }))"
```

기대 상태:

```text
PRIMARY
SECONDARY
SECONDARY
```

## 7. App Stack EC2 배포

`infra/app/env.example`을 기준으로 `deploy.env`를 만든다.

중요 값:

```env
API_DOMAIN=api.<domain>
MOCK_OTA_ENDPOINT=https://api.<domain>/mock-ota
MOCK_OTA_PMS_WEBHOOK_URL=https://api.<domain>/api/v1/properties/{propertyId}/channels/webhook/{channelCode}
MOCK_OTA_HTPASSWD_PATH=./.htpasswd
SPRING_MONGODB_URI=mongodb://<app-user>:<password>@<mongo1>:27017,<mongo2>:27017,<mongo3>:27017/stayops?replicaSet=rs0&w=majority&readPreference=primary&retryWrites=true&authSource=admin
```

Mock OTA 제어 API 보호를 위해 `.htpasswd`를 생성한다. 파일은 커밋하지 않는다.

```bash
htpasswd -bc .htpasswd <user> <password>
```

TLS 인증서를 준비한다.

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
curl -f https://<api-domain>/health
curl -i https://<api-domain>/actuator/prometheus
curl -i https://<api-domain>/mock-ota/api/v1/ari/received
```

외부 `/actuator/prometheus` 기대 결과는 `404`이다.

## 8. 관측 계층 확인

App Stack EC2에서 확인한다.

```bash
curl -f http://localhost:9090/-/ready
curl -f http://localhost:3100/ready
```

Grafana는 SSH tunnel로 접근한다.

```bash
ssh -L 3001:localhost:3001 <app-stack-ec2>
```

브라우저:

```text
http://localhost:3001
```

Prometheus target에서 다음이 `UP`인지 확인한다.

- StayOps App
- App Stack node-exporter
- MongoDB EC2 1/2/3 node-exporter
- MongoDB EC2 1/2/3 mongodb-exporter

## 9. 로컬 k6 smoke test

로컬 PC에서 실행한다.

```bash
BASE_URL=https://<api-domain> \
EXPERIMENT_ID=local-smoke-001 \
LOADTEST_PHASE=smoke \
TEST_MODE=smoke \
LIGHTWEIGHT_RATE=5 \
BUSINESS_RATE=1 \
k6 run loadtest/k6/stayops-app-load.js
```

## 10. 로컬 k6 DB ramp

```bash
BASE_URL=https://<api-domain> \
EXPERIMENT_ID=local-db-ramp-001 \
LOADTEST_PHASE=db-ramp \
TEST_MODE=db-ramp \
PROPERTY_ID=<property-id> \
ROOM_TYPE_ID=<room-type-id> \
CUSTOMER_EMAIL=<customer-email> \
CUSTOMER_PASSWORD=<customer-password> \
READ_RATE=10 \
WRITE_RATE=1 \
WRITE_DATE_SPREAD_DAYS=90 \
k6 run loadtest/k6/stayops-db-load.js
```

## 11. MongoDB Failover 테스트

로컬 PC에서 steady load를 실행한다.

```bash
BASE_URL=https://<api-domain> \
EXPERIMENT_ID=local-failover-001 \
LOADTEST_PHASE=failover-steady \
TEST_MODE=failover-steady \
PROPERTY_ID=<property-id> \
ROOM_TYPE_ID=<room-type-id> \
CUSTOMER_EMAIL=<customer-email> \
CUSTOMER_PASSWORD=<customer-password> \
READ_RATE=10 \
WRITE_RATE=1 \
WRITE_DATE_SPREAD_DAYS=90 \
k6 run loadtest/k6/stayops-db-load.js
```

3분 warm-up 후 primary를 확인하고, primary EC2에서 `mongod`를 중지한다.

```bash
docker compose --env-file deploy.env stop mongo
```

복구:

```bash
docker compose --env-file deploy.env start mongo
```

관찰 항목:

- election 시간
- k6 failed request rate
- p95/p99 latency 증가폭
- write concern timeout
- MongoDB driver error
- replication lag
- 복귀 노드의 SECONDARY 재합류 여부

## 12. 종료 절차

테스트 종료 후 비용이 계속 발생하지 않도록 확인한다.

```bash
docker compose ps
```

AWS 콘솔에서 확인한다.

- EC2 중지 여부
- 사용하지 않는 Public IPv4
- Elastic IP
- EBS volume
- snapshot
- NAT Gateway

MongoDB volume은 결과 보존 여부를 먼저 결정한 뒤 삭제한다.
