# AWS MongoDB Load Test Infra Plan

작성일: 2026-04-17
수정일: 2026-04-19
상태: 구현 준비

## 목표

StayOps의 부하 테스트와 MongoDB failover/recovery 가설을 검증한다. 이번 단계의 목적은 완전한 프로덕션 고가용성 구성이 아니라, 작은 MongoDB 인스턴스 3대에서 P-S-S replica set이 어떤 부하와 장애 상황을 버티는지 관찰하는 것이다.

## 최종 구성

```text
Local PC
+-------------------------------+
| k6                            |
+---------------+---------------+
                |
                | HTTPS
                v
AWS
+------------------------------------------------+
| App Stack EC2                                  |
|------------------------------------------------|
| Nginx 80/443                                   |
| - /           -> StayOps Spring Boot           |
| - /mock-ota   -> Mock OTA Spring Boot          |
| Redis                                           |
| Mock OTA MongoDB                                |
| Prometheus / Grafana / Loki                     |
| Promtail / node-exporter                        |
+---------------+--------------------------------+
                |
                | MongoDB replica set URI
                v
+-----------------------+     +-----------------------+
| MongoDB EC2 1         |     | MongoDB EC2 2         |
| - mongod data + vote  |<--->| - mongod data + vote  |
| - mongodb-exporter    |     | - mongodb-exporter    |
| - promtail            |     | - promtail            |
| - node-exporter       |     | - node-exporter       |
+-----------+-----------+     +-----------+-----------+
            ^                             ^
            |                             |
            +-------------+---------------+
                          |
                          v
              +-----------------------+
              | MongoDB EC2 3         |
              | - mongod data + vote  |
              | - mongodb-exporter    |
              | - promtail            |
              | - node-exporter       |
              +-----------------------+
```

## 인스턴스 산정

초기 산정은 다음과 같이 둔다.

```text
App Stack EC2:
- t3.medium
- 2 vCPU / 4 GiB
- App, Mock OTA, Redis, Mock OTA MongoDB, Observability를 함께 실행

MongoDB EC2 1/2/3:
- t3.micro
- 2 vCPU / 1 GiB
- 의도적으로 작은 DB 노드로 시작해 DB 부하 한계와 failover/recovery를 관찰
```

MongoDB가 `t3.micro`에서 기동 안정성 문제나 OOM으로 failover 가설 검증 자체가 어려워지면 `t3.small`로 올린다. 이 변경은 실험 결과에 기록한다.

## 판단 근거

### k6는 로컬 PC에서 실행한다

k6를 별도 EC2에 배포하지 않는다. 처음에는 로컬 PC에서 `BASE_URL=https://api.<domain>`을 향해 부하를 발생시킨다.

로컬 k6의 한계는 `dropped_iterations`, 로컬 CPU, 로컬 네트워크 상태로 판단한다. 로컬 PC가 목표 부하를 만들지 못하면 그때 별도 k6 EC2를 도입한다.

### App과 Mock OTA는 같은 App Stack EC2에 둔다

이번 실험의 핵심은 Mock OTA의 인프라 독립성이 아니라 MongoDB 부하와 장애 복구다. 따라서 Mock OTA를 별도 서버로 분리하지 않고 App Stack EC2에 함께 둔다.

도메인은 하나만 사용한다.

```text
https://api.<domain>/             -> StayOps App
https://api.<domain>/mock-ota/... -> Mock OTA App
```

### MongoDB는 P-S-S로 둔다

Arbiter는 투표에는 참여하지만 데이터를 저장하지 않는다. 장애 복구와 데이터 정합성 실험이 목적이므로 세 노드를 모두 data-bearing member로 둔다.

```text
Primary - Secondary - Secondary
```

쓰기 안정성을 위해 App의 MongoDB URI에는 `w=majority`, `readPreference=primary`, `retryWrites=true`를 둔다.

## 작업 순서

1. `infra/app`에 StayOps App, Mock OTA, Redis, Observability를 통합한다.
2. `infra/mock-ota` 단독 배포 구성을 제거한다.
3. `infra/mongodb`에 MongoDB 노드별 env 예시를 둔다.
4. 문서를 로컬 k6, AWS App Stack, AWS MongoDB 3대 기준으로 갱신한다.
5. compose config와 k6 문법을 로컬에서 검증한다.
6. PR 이후 AWS EC2 4대를 생성한다.
7. MongoDB EC2 3대에 같은 compose와 노드별 `deploy.env`를 배포한다.
8. App Stack EC2에 `infra/app`을 배포한다.
9. 로컬 PC에서 k6 smoke, app-baseline, db-ramp, failover-steady 순으로 실행한다.

## 비용 통제

- EC2는 실험 시간에만 실행한다.
- Elastic IP, EBS volume, snapshot, public IPv4 비용을 확인한다.
- NAT Gateway는 이번 실험에 필수로 두지 않는다.
- 테스트 종료 후 EC2 중지와 사용하지 않는 리소스 정리를 수행한다.
