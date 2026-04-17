# AWS MongoDB Load Test Infra Plan

작성일: 2026-04-17
상태: 구현 준비

## 목표

StayOps의 단일 인스턴스 구조에서 MongoDB를 분리하고, 제한된 인스턴스 수 안에서 MongoDB data node 장애 복구와 DB 부하 한계를 측정한다.

이번 단계는 전체 고가용성 구성이 아니다. Boot EC2와 Redis는 여전히 단일 장애 지점으로 남긴다.

## 최종 구성

```text
AWS VPC

Public Subnet
+------------------------------------------------+
| Boot EC2                                       |
| - Nginx                                        |
| - StayOps Spring Boot                          |
| - Redis                                        |
| - Mongo Arbiter                                |
| - Prometheus / Grafana / Loki                  |
| - Promtail / node-exporter                     |
+---------------------+--------------------------+
                      |
                      | private traffic
                      v
Private Subnet
+-----------------------+     +-----------------------+
| Mongo EC2 1           |     | Mongo EC2 2           |
| - mongod data + vote  |<--->| - mongod data + vote  |
| - mongodb-exporter    |     | - mongodb-exporter    |
| - promtail            |     | - promtail            |
| - node-exporter       |     | - node-exporter       |
+-----------------------+     +-----------------------+

Oracle VM
+------------------------------------------------+
| Mock OTA                                       |
| - Nginx / HTTPS                                |
| - Mock OTA Spring Boot                         |
| - Mock OTA MongoDB                             |
+------------------------------------------------+
```

## 판단 근거

### MongoDB는 2 data node + arbiter로 둔다

가용 가능한 MongoDB 인스턴스 수가 2대로 제한되어 있으므로, MongoDB data node는 2대만 둔다. data node 2대만으로는 한 대 장애 시 투표 과반을 잃어 자동 primary 선출이 어렵다.

Boot EC2에 arbiter를 추가하면 `Primary-Secondary-Arbiter` 구성이 되어 MongoDB data node 1대 장애 시 남은 data node가 primary로 승격될 수 있다.

한계는 명확하다. Arbiter는 데이터를 저장하지 않으므로 데이터 복제본은 2개뿐이고, Boot EC2 장애 시 App, Redis, Arbiter가 함께 중단된다.

### writeConcern은 majority로 둔다

예약, 결제, 재고 데이터는 성능보다 쓰기 안정성이 우선이다. 따라서 Spring MongoDB URI에는 `w=majority`를 명시한다.

이 선택은 secondary 지연 또는 장애 시 write latency 증가와 timeout 가능성을 만든다. 이 비용은 k6와 MongoDB 메트릭으로 측정한다.

### Redis HA는 제외한다

현재 Redis는 Spring Session 저장소이다. Redis 장애는 로그인 세션 장애를 만들지만, 이번 테스트의 핵심은 MongoDB 부하와 failover이다.

Redis는 Boot EC2 단일 컨테이너로 유지하고, 다음 단계에서 ElastiCache Multi-AZ 또는 Redis Sentinel을 검토한다.

### Mock OTA는 Oracle에 둔다

Mock OTA는 실제 외부 OTA 벤더 역할이다. Oracle VM에 분리하면 StayOps가 외부 HTTPS API와 webhook으로 통신하는 흐름을 더 현실적으로 검증할 수 있다.

DB 부하 테스트에서는 Mock OTA 호출을 제외하고, 별도의 OTA E2E 테스트에서 timeout, server error, rate limit을 검증한다.

## 작업 순서

1. 현재 단일 compose 구조를 As-Is로 기록한다.
2. AWS VPC, public subnet, private subnet, security group을 구성한다.
3. Boot EC2, Mongo EC2 1, Mongo EC2 2, Oracle Mock OTA VM을 준비한다.
4. `infra/aws/boot` compose를 Boot EC2에 배포한다.
5. `infra/aws/mongo` compose를 Mongo EC2 1, 2에 배포한다.
6. `infra/aws/mongo/init-replica-set.js`로 replica set을 초기화한다.
7. `infra/oracle/mock-ota` compose를 Oracle VM에 배포한다.
8. Spring Boot health check, `rs.status()`, Prometheus scrape, Loki log ingestion을 확인한다.
9. `loadtest/k6/stayops-app-load.js`로 Application thread-pool 기준선을 측정한다.
10. `loadtest/k6/stayops-db-load.js` smoke test를 실행한다.
11. DB baseline, ramp-up, saturation 테스트를 순서대로 실행한다.
12. MongoDB primary를 중지해 failover를 측정한다.
13. 병목 원인별 개선안을 하나씩 적용하고 같은 k6 시나리오로 재측정한다.
14. 테스트 종료 후 EC2를 중지하고 credit/EBS/Public IPv4/NAT Gateway 비용을 확인한다.

## 부하 테스트 원칙

부하 테스트는 Application 한계와 MongoDB 한계를 분리한다. 단일 Boot EC2가 먼저 병목이 되면 API 부하 테스트 결과를 MongoDB 한계로 해석할 수 없기 때문이다.

### 1. Application thread-pool 테스트

먼저 다음 경로를 측정한다.

```text
k6 -> Nginx -> StayOps Spring Boot
```

`loadtest/k6/stayops-app-load.js`는 `/actuator/info`를 호출하는 lightweight flow와 `/api/v1/customer/properties`를 호출하는 MongoDB read control flow를 함께 실행한다.

관찰 기준:

- Tomcat busy/current thread
- HTTP request duration
- JVM heap and GC pause
- Boot EC2 CPU, memory, network
- MongoDB CPU/IO가 낮은 상태에서도 App latency가 증가하는지

해석:

```text
App CPU/thread가 먼저 포화되고 MongoDB가 낮게 유지됨
-> 현재 병목은 Boot Application이다.

App 지표가 여유 있고 MongoDB CPU/IO/replication lag가 증가함
-> MongoDB 병목으로 해석할 수 있다.
```

### 2. MongoDB DB 부하 테스트

Application 한계 기준선을 확인한 뒤 다음 경로를 측정한다.

```text
k6 -> Nginx -> StayOps Spring Boot -> MongoDB replica set
```

외부 PG 결제 승인과 Mock OTA 호출은 첫 번째 DB 부하 테스트에서 제외한다. 이 둘은 별도 E2E 시나리오로 분리한다.

필수 메트릭:

- k6: throughput, p95/p99 latency, failed request rate
- Spring Boot: endpoint latency, 4xx/5xx, JVM heap, GC pause, request threads, MongoDB connection pool
- MongoDB: primary/secondary state, election, replication lag, opcounters, slow query, write concern timeout
- Node: CPU, memory, disk I/O, network, load average

필수 로그:

- Spring Boot exception log
- MongoDB election, stepdown, write concern log
- Nginx access/error log
- k6 threshold failure output
- Promtail and Loki ingestion status

## 비용 통제

AWS credit은 사용량 기반 비용에 적용된다. 포트폴리오 공개와 부하 테스트 시간에만 EC2를 켠다.

주의 대상:

- EC2 instance-hours
- EBS volume
- Public IPv4
- NAT Gateway
- snapshot
- inter-cloud traffic to Oracle Mock OTA

테스트 종료 후 Boot EC2와 Mongo EC2 2대를 중지하고, 사용하지 않는 NAT Gateway나 public IPv4가 남아 있지 않은지 확인한다.
