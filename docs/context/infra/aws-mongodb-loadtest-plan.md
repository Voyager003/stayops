# AWS MongoDB Load Test Infra Plan

작성일: 2026-04-17
수정일: 2026-04-19
상태: 구현 준비

## 목표

StayOps의 단일 인스턴스 구조에서 App, Mock OTA, MongoDB를 분리하고, MongoDB 부하 한계와 replica set failover/recovery를 측정한다.

이번 단계는 완전한 프로덕션 고가용성 구성이 아니다. App 서버와 Redis는 여전히 단일 장애 지점으로 남긴다. 대신 MongoDB는 `Primary - Secondary - Secondary` 구조로 두어 arbiter 없이 데이터 복제본 3개를 확보한다.

## 최종 구성

```text
Local / Load Generator
+-------------------------------+
| k6                            |
+---------------+---------------+
                |
                | HTTPS
                v
+------------------------------------------------+
| Oracle App VM                                  |
|------------------------------------------------|
| Nginx                                          |
| StayOps Spring Boot                            |
| Redis                                          |
| Prometheus / Grafana / Loki                    |
| Promtail / node-exporter                       |
+---------------+--------------------------------+
                |
                | MongoDB replica set URI
                v
+-----------------------+     +-----------------------+
| AWS MongoDB VM 1      |     | AWS MongoDB VM 2      |
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
              | AWS MongoDB VM 3      |
              | - mongod data + vote  |
              | - mongodb-exporter    |
              | - promtail            |
              | - node-exporter       |
              +-----------------------+

Oracle Mock OTA VM
+------------------------------------------------+
| Nginx / HTTPS                                  |
| Mock OTA Spring Boot                           |
| Mock OTA MongoDB                               |
| Promtail / node-exporter                       |
+------------------------------------------------+
```

## 판단 근거

### MongoDB는 P-S-S로 둔다

초기에는 인스턴스 비용을 줄이기 위해 `Primary - Secondary - Arbiter`를 검토했다. 하지만 arbiter는 투표에는 참여해도 데이터를 저장하지 않는다. 이번 실험은 장애 복구와 데이터 정합성을 보여주는 것이 목적이므로 MongoDB VM을 3대로 늘리고 data-bearing secondary를 2대 둔다.

이 선택의 이점:

- 데이터 복제본이 3개가 된다.
- primary 장애 시 남은 2대가 과반을 구성할 수 있다.
- arbiter가 데이터 내구성에 기여하지 않는다는 설명 부담을 줄인다.
- failover 이후 복귀 노드의 oplog catch-up을 관찰하기 쉽다.

트레이드 오프:

- VM 비용과 운영 절차가 늘어난다.
- MongoDB 간 네트워크 보안그룹과 keyfile 관리가 필요하다.
- App 서버가 Oracle, DB가 AWS에 있으면 cross-cloud network latency와 egress 비용을 같이 관찰해야 한다.

### writeConcern은 majority로 둔다

예약, 결제, 재고 데이터는 성능보다 쓰기 안정성이 우선이다. Spring MongoDB URI에는 `w=majority`, `readPreference=primary`, `retryWrites=true`를 둔다.

이 선택은 secondary 지연 또는 장애 시 write latency 증가와 timeout 가능성을 만든다. 이 비용을 k6와 MongoDB exporter로 측정한다.

### Redis HA는 제외한다

현재 Redis는 Spring Session 저장소다. Redis 장애는 로그인 세션 장애를 만들지만, 이번 실험의 핵심은 MongoDB 부하와 failover다. Redis는 Oracle App VM의 단일 컨테이너로 유지하고 다음 단계에서 Redis Sentinel, ElastiCache Multi-AZ, stateless auth 전환을 검토한다.

### Mock OTA는 별도 Oracle VM에 둔다

Mock OTA는 실제 OTA 벤더를 대체하는 외부 시스템이다. Oracle VM에 분리하면 StayOps가 외부 HTTPS API와 webhook으로 통신하는 흐름을 보여줄 수 있다. DB 부하 테스트에서는 Mock OTA 호출을 제외하고, 별도 E2E에서 timeout, 5xx, rate limit을 검증한다.

## 작업 순서

1. 저장소에 Oracle App VM용 compose를 추가한다.
2. AWS MongoDB compose를 인증이 켜진 P-S-S 구성으로 바꾼다.
3. MongoDB keyfile과 runtime secret은 커밋하지 않도록 제외한다.
4. replica set 초기화 스크립트에서 arbiter를 제거하고 MongoDB 3대를 member로 둔다.
5. App과 Prometheus가 MongoDB 3대를 바라보도록 env와 scrape target을 보강한다.
6. k6 스크립트에 실험 ID, phase, scenario 헤더를 추가한다.
7. Spring Boot prod 로그에 실험 MDC 값을 포함한다.
8. 예약 생성 write path에 부하 테스트 식별 로그를 추가한다.
9. 문서에 phase별 가설, 액션, 메트릭, 판단 기준을 기록한다.
10. 로컬에서 테스트와 compose config 검증을 끝낸 뒤 커밋한다.
11. PR 이후 VM에 Docker/compose를 설치하고 deploy.env를 주입한다.
12. k6 smoke, app baseline, DB ramp, failover-steady 순으로 실행한다.
13. 결과를 문서화하고 병목별 개선 작업을 별도 커밋으로 진행한다.

## 비용 통제

AWS credit은 사용량 기반 비용에 적용된다. 포트폴리오 공개와 부하 테스트 시간에만 EC2를 켠다.

주의 대상:

- EC2 instance-hours
- EBS volume
- Public IPv4
- snapshot
- cross-cloud traffic
- NAT Gateway가 생성되었다면 시간당 비용

이번 구성은 cross-cloud 통신을 전제로 하므로 NAT Gateway가 필수는 아니다. MongoDB port는 public internet에 열지 않고 App VM, 운영자 IP, MongoDB peer만 허용한다.
