# StayOps 인프라 구성

StayOps 서버 운영 방식에 따라 인프라 구성을 분리한다.

- `production`: 운영 수준의 고가용성 구성
- `minimal`: 부하 테스트 이후 비용을 줄이기 위한 최소 실행 구성

## production

`infra/production`은 실제 운영 또는 운영에 가까운 부하 테스트, failover/recovery 검증에 사용한다.

- `app`: StayOps app
- `mock-ota`: Mock OTA Nginx, Mock OTA app, Mock OTA MongoDB
- `app/docker-compose.observability.yml`: Prometheus, Loki, Promtail, Grafana, node-exporter
- `mongodb-rss`: 3-node MongoDB replica set, MongoDB exporter, node-exporter, Promtail

운영 수준에서 app EC2를 2대 이상으로 확장할 때 Redis는 app 인스턴스 내부가 아니라
공용 Redis endpoint(예: ElastiCache)를 사용한다. StayOps는 Redis 기반 Spring Session과
재고 캐시를 사용하므로, 각 EC2가 자기 Redis를 가지면 ALB가 요청을 분산할 때 세션과 캐시가
서버별로 분리된다.

기본 앱 실행:

```bash
cd infra/production/app
cp env.example .env
docker compose --env-file .env -f docker-compose.yml up -d
```

Mock OTA 실행:

```bash
cd infra/production/mock-ota
cp env.example .env
# MOCK_OTA_HTPASSWD_PATH에 지정한 Basic Auth 파일을 먼저 만든다.
docker compose --env-file .env -f docker-compose.yml up -d
```

관측 스택까지 함께 실행:

```bash
cd infra/production/app
cp env.example .env
docker compose --env-file .env -f docker-compose.yml -f docker-compose.observability.yml up -d
```

MongoDB R-S-S 실행:

```bash
cd infra/production/mongodb-rss
cp env.example .env
docker compose --env-file .env up -d
./bootstrap-replica-set.sh .env
./provision-mongo-users.sh .env
```

## minimal

`infra/minimal`은 부하 테스트가 끝난 뒤 서비스를 켜두기만 하면 되는 저비용 구성을 제공한다.

- `app`: StayOps app, Redis, Nginx, Mock OTA, Mock OTA MongoDB
- `mongodb-single-rs`: 단일 MongoDB 노드로 구성한 single-node replica set

최소 구성의 MongoDB도 standalone으로 두지 않는다. StayOps는 `MongoTransactionManager` 기반
MongoDB transaction을 사용하므로 MongoDB가 replica set 모드로 실행되어야 한다.
따라서 비용 절감 구성에서는 secondary 노드만 제거하고 `replicaSet=rs0`은 유지한다.

최소 MongoDB 실행:

```bash
cd infra/minimal/mongodb-single-rs
cp env.example .env
docker compose --env-file .env up -d
./bootstrap-single-replica-set.sh .env
./provision-mongo-users.sh .env
```

최소 앱 실행:

```bash
cd infra/minimal/app
cp env.example .env
docker compose --env-file .env up -d
```

## 선택 기준

- 장애 조치, 복구 시간, MongoDB replication, 운영 관측이 필요하면 `production`을 사용한다.
- 부하 테스트 이후 비용을 줄이면서 API 서버만 유지하려면 `minimal`을 사용한다.
- `minimal`은 고가용성 구성이 아니다. MongoDB 노드가 내려가면 자동 failover는 불가능하다.
- 실제 운영 트래픽을 받을 계획이라면 `production`의 3-node R-S-S 구성을 기준으로 둔다.
