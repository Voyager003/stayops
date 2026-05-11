# ALB + 앱 Nginx 제거 기반 Scale-out 적용 메모

## 목표 구조

현재 StayOps는 앱 서버를 수평 확장할 때 app EC2 내부의 Nginx를 제거하고, ALB가 Spring Boot
app 컨테이너로 직접 요청을 전달하는 구조를 기준으로 둔다.

```text
Client
  -> Route 53 public record
  -> Internet-facing ALB:443 (ACM 인증서로 TLS 종료)
  -> App Target Group HTTP:8080
  -> private App EC2:8080
  -> StayOps Spring Boot
```

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

### Mock OTA compose

`infra/production/mock-ota`를 별도로 만들고 다음 서비스를 배치한다.

- `nginx`: private reverse proxy, 제어 API Basic Auth
- `mock-ota-app`: Mock OTA Spring Boot app
- `mock-ota-mongodb`: Mock OTA 전용 데이터 저장소

StayOps가 OTA로 ARI를 push할 때는 private DNS를 사용한다.

```env
MOCK_OTA_ENDPOINT=http://mock-ota.stayops.internal
```

다만 Mock OTA가 예약 webhook을 보낼 때는 실제 외부 OTA와 유사하게 public API endpoint를 호출한다.

```env
MOCK_OTA_PMS_WEBHOOK_URL=https://api.example.com/api/v1/properties/{propertyId}/channels/webhook/{channelCode}
```

### ALB rule

App Nginx가 제거되면서 `/actuator/prometheus` 외부 차단은 ALB listener rule로 옮긴다.

```text
IF path == /actuator/prometheus
THEN fixed response 404 or 403
```

Spring Security에서는 현재 `/actuator/prometheus`가 허용되어 있으므로, public edge에서 막는
규칙이 필요하다. Prometheus가 같은 EC2 Docker network 안에서 `app:8080`을 scrape하는 흐름은
유지된다.

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

- Redis는 app EC2 내부가 아니라 공용 Redis endpoint를 사용한다. 같은 사용자가 어느 app 인스턴스로
  가도 동일한 세션을 읽기 위해서다.
- Spring `@Scheduled`는 인스턴스마다 실행되므로, PaymentOutbox/SyncTask/Inventory rolling 작업은
  lease 또는 scheduler lock으로 중복 실행을 방지한다.
- ALB가 TLS를 종료하므로 Spring Boot는 `server.forward-headers-strategy: framework`로 forwarded
  header를 해석한다.

## 배포 체크리스트

1. App EC2는 private subnet에 2대 이상 배치한다.
2. App EC2 Security Group은 ALB Security Group에서 오는 8080만 허용한다.
3. Internet-facing ALB를 만들고 HTTPS 443 listener에 ACM 인증서를 연결한다.
4. App target group은 HTTP 8080, health check path는 `/actuator/health`로 둔다.
5. ALB listener rule에서 `/actuator/prometheus`를 fixed response로 차단한다.
6. Route 53 public record `api.example.com`을 ALB alias로 연결한다.
7. Mock OTA EC2는 private subnet에 배치하고 Security Group은 App SG에서 오는 80만 허용한다.
8. Route 53 private hosted zone에 `mock-ota.stayops.internal`을 Mock OTA EC2 private IP로 연결한다.
9. 모든 app EC2에 같은 `SPRING_MONGODB_URI`, `SPRING_DATA_REDIS_HOST`, `MOCK_OTA_ENDPOINT`를 설정한다.
10. 기존 OTA 채널 데이터의 `connectionInfo.apiEndpoint`를 새 private endpoint로 갱신한다.
11. 배포 후 ALB target health, app 5xx, ALB 5xx, p95 latency, scheduler 중복 실행 로그를 확인한다.
