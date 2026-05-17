# ALB + 앱 Nginx 제거 기반 Scale-out 적용 메모

## 목표 구조

현재 StayOps는 앱 서버를 수평 확장할 때 app EC2 내부의 Nginx를 제거하고, ALB가 private
App EC2의 Spring Boot app 컨테이너로 직접 요청을 전달하는 구조를 기준으로 둔다.

```text
Client
  -> Public DNS record
  -> Internet-facing ALB:443 (ACM 인증서로 TLS 종료)
  -> App Target Group HTTP:8080
  -> private App EC2:8080
  -> StayOps Spring Boot
```

최종 production 계획은 EC2 8대로 나눈다.

- App EC2 2대: StayOps app, node-exporter, Promtail
- Redis EC2 1대: Redis, Redis exporter, node-exporter, Promtail
- Mock OTA EC2 1대: Nginx, Mock OTA app, Mock OTA MongoDB, node-exporter, Promtail
- MongoDB EC2 3대: MongoDB replica set node, MongoDB exporter, node-exporter, Promtail
- Observability EC2 1대: Prometheus, Loki, Grafana, node-exporter

Mock OTA는 앱 서버에서 분리한다. 실제 OTA는 StayOps 내부 프로세스가 아니라 외부 채널이므로,
프로덕션 유사 환경에서도 별도 private EC2에 둔다.

```text
StayOps App
  -> Route 53 private record: mock-ota.stayops.internal
  -> Mock OTA EC2 Nginx:80
  -> mock-ota-app:8081
  -> mock-ota-mongodb

Mock OTA
  -> StayOps public webhook endpoint
  -> ALB:443
  -> App Target Group HTTP:8080
```

Redis와 관측 스택도 app EC2에서 분리한다.

```text
StayOps App
  -> redis.stayops.internal:6379

Promtail on each EC2
  -> observability.stayops.internal:3100

Prometheus on Observability EC2
  -> App /actuator/prometheus
  -> Redis exporter
  -> MongoDB exporter
  -> node-exporter
```

## 판단 근거

기존 app EC2의 Nginx는 다음 역할을 한 번에 맡고 있었다.

- public TLS endpoint 역할
- Spring Boot app reverse proxy
- `/mock-ota/*` path rewrite
- Mock OTA 제어 API Basic Auth
- `/actuator/prometheus` 외부 노출 차단
- proxy timeout, forwarded header 전달

ALB를 도입하면 public TLS 종료, target health check, path rule, fixed response, target group
routing은 ALB가 맡을 수 있다. 따라서 app EC2의 Nginx는 더 이상 필수 컴포넌트가 아니다.
반대로 Mock OTA에는 아직 애플리케이션 자체 인증이 없고, 제어 API 보호와 프록시 규칙이 필요하므로
Mock OTA 전용 EC2에서는 Nginx를 유지한다.

참고:

- AWS ALB listener rules: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-rules.html
- AWS target group health checks: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/target-group-health-checks.html
- AWS ALB security groups: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-update-security-groups.html
- AWS Route 53 private hosted zones: https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/hosted-zones-private.html
- AWS Systems Manager Session Manager: https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager.html
- NGINX reverse proxy: https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy/
- NGINX Basic Auth: https://nginx.org/en/docs/http/ngx_http_auth_basic_module.html

## 변경한 지점

### App compose

`infra/production/app/docker-compose.yml`은 Spring Boot app만 실행한다. ALB target group이 EC2의
8080 포트로 접근할 수 있도록 컨테이너 포트를 host에 publish한다.

```yaml
services:
  app:
    ports:
      - "8080:8080"
```

이 포트는 인터넷에 직접 열기 위한 것이 아니다. Security Group에서 `ALB SG -> App EC2:8080`만
허용해야 한다.

`infra/production/app/docker-compose.agent.yml`은 app EC2의 운영 agent만 실행한다.

- `node-exporter`: EC2 host metric 노출
- `promtail`: Docker container log를 Loki로 전송

```env
LOKI_URL=http://observability.stayops.internal:3100/loki/api/v1/push
HOSTNAME=app-1
```

### Redis compose

`infra/production/redis`는 private Redis EC2에서 실행한다.

- `redis`: Spring Session과 재고 캐시를 공유하는 단일 Redis endpoint
- `redis-exporter`: Prometheus scrape 대상
- `node-exporter`: Redis EC2 host metric
- `promtail`: Redis EC2 container log 전송

```env
SPRING_DATA_REDIS_HOST=redis.stayops.internal
SPRING_DATA_REDIS_PORT=6379
```

### Mock OTA compose

`infra/production/mock-ota`를 별도로 만들고 다음 서비스를 배치한다.

- `nginx`: private reverse proxy, 제어 API Basic Auth
- `mock-ota-app`: Mock OTA Spring Boot app
- `mock-ota-mongodb`: Mock OTA 전용 데이터 저장소
- `node-exporter`, `promtail`: Mock OTA EC2 metric/log 수집

StayOps가 OTA로 ARI를 push할 때는 private DNS를 사용한다.

```env
MOCK_OTA_ENDPOINT=http://mock-ota.stayops.internal
```

다만 Mock OTA가 예약 webhook을 보낼 때는 실제 외부 OTA와 유사하게 public API endpoint를 호출한다.

```env
MOCK_OTA_PMS_WEBHOOK_URL=https://api.example.com/api/v1/properties/{propertyId}/channels/webhook/{channelCode}
```

### Observability compose

`infra/production/observability`는 private Observability EC2에서 실행한다.

- `prometheus`: app, node, MongoDB, Redis metric scrape
- `loki`: 각 EC2의 Promtail이 전송하는 로그 저장
- `grafana`: Prometheus/Loki 대시보드

Grafana/Prometheus/Loki는 인터넷에 직접 노출하지 않는다. 운영자는 Systems Manager Session
Manager port forwarding으로 접근한다.

### ALB rule

App Nginx가 제거되면서 `/actuator/prometheus` 외부 차단은 ALB listener rule로 옮긴다.

```text
IF path == /actuator/prometheus
THEN fixed response 404 or 403
```

Spring Security에서는 현재 `/actuator/prometheus`가 허용되어 있으므로, public edge에서 막는
규칙이 필요하다. Prometheus는 private Observability EC2에서 각 app EC2의 private endpoint를
scrape한다.

### 기존 채널 endpoint 갱신

`MOCK_OTA_ENDPOINT`는 채널 생성 시 `ChannelConnectionInfo.apiEndpoint`로 저장된다. 따라서 기존에
생성된 OTA 채널은 환경변수만 바꿔도 자동으로 endpoint가 변경되지 않는다.

운영 데이터가 이미 있다면 배포 전 다음 값을 새 private endpoint로 갱신해야 한다.

```text
channels.connectionInfo.apiEndpoint
  https://api.example.com/mock-ota
  -> http://mock-ota.stayops.internal
```

실제 데이터 갱신은 별도 승인 후 실행한다.

## 수평 확장 시 함께 해결한 문제

- Redis는 app EC2 내부가 아니라 private Redis EC2 endpoint를 사용한다. 같은 사용자가 어느 app
  인스턴스로 가도 동일한 세션과 캐시를 읽기 위해서다.
- Spring `@Scheduled`는 인스턴스마다 실행되므로, PaymentOutbox/SyncTask/Inventory rolling 작업은
  lease 또는 scheduler lock으로 중복 실행을 방지한다.
- ALB가 TLS를 종료하므로 Spring Boot는 `server.forward-headers-strategy: framework`로 forwarded
  header를 해석한다.
- Observability는 별도 private EC2로 분리한다. 운영 로그와 metric은 민감한 내부 상태를 포함할 수
  있으므로 public endpoint로 노출하지 않는다.

## AWS 설정 체크리스트

1. Public subnet에는 Internet-facing ALB와 NAT Gateway만 둔다.
2. Private subnet에는 App EC2 2대, Redis EC2, Mock OTA EC2, MongoDB EC2 3대, Observability EC2를 둔다.
3. 모든 private EC2에는 `AmazonSSMManagedInstanceCore` 권한을 가진 IAM role을 연결한다.
4. Private subnet route table은 VPC CIDR local route와 `0.0.0.0/0 -> NAT Gateway` route를 가진다.
5. App EC2 Security Group은 ALB Security Group에서 오는 8080만 허용한다.
6. Redis Security Group은 App SG에서 오는 6379만 허용한다.
7. Mock OTA Security Group은 App SG에서 오는 80만 허용한다.
8. MongoDB Security Group은 App SG에서 오는 27017과 MongoDB SG 내부 27017만 허용한다.
9. Observability Security Group은 각 service SG에서 오는 Loki 3100과 scrape port를 필요한 방향으로만 허용한다.
10. Internet-facing ALB를 만들고 HTTPS 443 listener에 ACM 인증서를 연결한다.
11. App target group은 HTTP 8080, health check path는 `/actuator/health`로 둔다.
12. ALB listener rule에서 `/actuator/prometheus`를 fixed response로 차단한다.
13. DNS provider 또는 Route 53 public hosted zone에서 `api.example.com`을 ALB DNS로 연결한다.
14. Route 53 private hosted zone에 `app-1.stayops.internal`, `app-2.stayops.internal`, `redis.stayops.internal`, `mock-ota.stayops.internal`, `mongo-1.stayops.internal`, `mongo-2.stayops.internal`, `mongo-3.stayops.internal`, `observability.stayops.internal`을 등록한다.
15. 모든 app EC2에 같은 `SPRING_MONGODB_URI`, `SPRING_DATA_REDIS_HOST`, `MOCK_OTA_ENDPOINT`를 설정한다.
16. 기존 OTA 채널 데이터의 `connectionInfo.apiEndpoint`를 새 private endpoint로 갱신한다.
17. 배포 후 ALB target health, app 5xx, ALB 5xx, p95 latency, scheduler 중복 실행 로그, Redis 연결, Prometheus scrape 상태, Loki 로그 수집 상태를 확인한다.
