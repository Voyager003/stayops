# ALB + Nginx 유지 기반 Scale-out 적용 메모

## 목표 구조

현재 StayOps는 단일 EC2가 모든 트래픽을 받고 있다. 앱 서버를 2대 이상으로 늘릴 때는
다음 흐름을 기준으로 둔다.

```text
Client
  -> ALB:443 (ACM 인증서로 TLS 종료)
  -> Target Group
  -> 각 EC2의 Nginx:80
  -> app:8080 / mock-ota-app:8081
```

ALB stickiness는 기본적으로 끈다. StayOps는 Spring Session Redis를 사용하므로 같은
사용자 요청이 어느 app EC2로 가도 공용 Redis에서 같은 세션을 읽을 수 있어야 한다.

## ALB가 맡는 역할

- ACM 인증서를 연결한 HTTPS listener로 TLS를 종료한다.
- Target Group health check로 정상 EC2에만 요청을 보낸다.
- 여러 app EC2로 트래픽을 분산한다.
- `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Port`로 원래 클라이언트 정보를 전달한다.

참고:

- AWS ALB HTTPS listener: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/create-https-listener.html
- AWS ALB listener rules: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-rules.html
- AWS ALB X-Forwarded headers: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/x-forwarded-headers.html
- AWS target group health checks: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/target-group-health-checks.html

## Nginx를 유지하는 이유

ALB가 HTTPS 종료와 분산을 담당하더라도 현재 Nginx를 바로 제거하지 않는다. 현재 Nginx는
인증서 외에도 다음 역할을 수행한다.

- `/` 요청을 Spring Boot app으로 전달
- `/mock-ota/*` 요청을 Mock OTA 컨테이너로 전달하고 path rewrite 수행
- Mock OTA 제어 API에 Basic Auth 적용
- `/actuator/prometheus` 외부 노출 차단
- 요청 크기, proxy timeout, forwarded header 정리

따라서 1차 전환에서는 Nginx를 "공인 HTTPS endpoint"가 아니라 "EC2 내부 reverse proxy"로
남긴다. NGINX 공식 문서 기준으로 reverse proxy는 클라이언트 요청을 받아 proxied server로
전달하며, `proxy_pass`와 `proxy_set_header`로 upstream과 헤더를 제어한다.

참고:

- NGINX reverse proxy: https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy/

## 애플리케이션에서 수정한 지점

### 프록시 헤더

ALB가 HTTPS를 종료하고 Nginx로 HTTP 요청을 보내면 Nginx의 `$scheme`은 `http`가 된다.
기존처럼 `X-Forwarded-Proto $scheme`을 전달하면 사용자는 HTTPS로 접근했는데 app은 HTTP로
인식할 수 있다.

그래서 production Nginx는 ALB가 준 `X-Forwarded-Proto`, `X-Forwarded-Port`를 우선 전달한다.
Spring Boot prod 설정에는 `server.forward-headers-strategy: framework`를 추가했다.

### Redis 공용화

기존 production compose는 app EC2 내부에 Redis를 같이 띄웠다. EC2를 2대 이상으로 복제하면
각 서버마다 Redis가 생기므로 세션이 분리된다. production compose는 `SPRING_DATA_REDIS_HOST`를
외부 공용 Redis endpoint로 받도록 바꿨다.

### 스케줄러 중복 실행

Spring `@Scheduled`는 각 애플리케이션 인스턴스에서 실행된다. 앱 서버가 2대가 되면 같은
스케줄러가 2번 돈다.

- PaymentOutbox는 이미 `lockedBy`, `lockedUntil`, optimistic lock으로 lease를 사용한다.
  인스턴스 추적을 위해 worker id를 `payment-outbox-{instanceId}`로 전달한다.
- SyncTask는 lease가 없어서 인스턴스 장애 시 `IN_PROGRESS`에 고착될 수 있었다. PaymentOutbox와
  같은 방식으로 `lockedBy`, `lockedUntil`을 추가하고 만료된 lease를 다시 처리 대상으로 포함했다.
- Inventory rolling scheduler는 매일 2시에 모든 EC2에서 실행될 수 있으므로 MongoDB 기반
  `scheduler_locks` 컬렉션으로 하나의 인스턴스만 실행하도록 했다.

Spring scheduling 참고:

- Spring Framework scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html

## 배포 체크리스트

1. ACM에서 API 도메인 인증서를 발급한다.
2. ALB HTTPS listener 443에 ACM 인증서를 연결한다.
3. Target Group protocol은 HTTP, port는 EC2 Nginx 80으로 둔다.
4. Health check path는 `/health`로 둔다.
5. Security Group은 `Internet -> ALB:443`, `ALB SG -> App EC2:80`만 허용한다.
6. 모든 app EC2에 같은 `SPRING_MONGODB_URI`, 공용 `SPRING_DATA_REDIS_HOST`를 설정한다.
7. 각 EC2의 `STAYOPS_INSTANCE_ID`는 `app-1`, `app-2`처럼 고유하게 설정한다.
8. Route 53에서 API 도메인을 ALB로 연결한다.
9. 배포 후 세션 유지, target health, 5xx, p95 latency, scheduler 중복 실행 여부를 확인한다.
