# Index

- A. [프로젝트 소개](#a-프로젝트-소개)
  - a. [프로젝트 설명](#a-프로젝트-설명)
  - b. [빌드 및 실행법](#b-빌드-및-실행법)
  - c. [사용 기술](#c-사용-기술)
- B. [아키텍처](#b-아키텍처)
  - a. [인프라 아키텍처](#a-인프라-아키텍처)
  - b. [데이터 모델](#b-데이터-모델)
- C. [프로젝트 달성 목표](#c-프로젝트-달성-목표)
  - a. [부하 테스트로 서버 성능 측정](#a-부하-테스트로-서버-성능-측정)
  - b. [결제 승인 흐름의 외부 API 불일치 해결](#b-결제-승인-흐름의-외부-api-불일치-해결)
  - c. [OTA 재고 동기화와 Webhook 멱등성 설계](#c-ota-재고-동기화와-webhook-멱등성-설계)
- D. [문서 업데이트 내역](#d-문서-업데이트-내역)
---

# A. 프로젝트 소개

## a. 프로젝트 설명

`StayOps`는 숙박업의 객실·재고·예약·결제와 OTA 채널 동기화를 처리하는 PMS/CMS 백엔드 서버입니다.

### 주요 용어

- `PMS(Property Management System)` 는 숙소 내부 운영 시스템입니다. 객실, 재고, 예약, 고객, 결제, 정산처럼 숙소 운영의 기준 데이터를 관리합니다.

- `OTA(Online Travel Agency)` 는 Booking.com, Agoda, Expedia처럼 숙소를 외부 고객에게 판매하는 온라인 예약 채널입니다.

- `CMS(Channel Management System)/채널매니저`는 PMS와 여러 OTA 사이에서 객실 재고, 요금, 예약 정보를 동기화하는 시스템입니다. 여러 채널에서 같은 객실을 동시에 판매할 때 오버부킹을 방지하는 역할을 합니다.

### 실제 운영 환경의 동작 방식

실제 운영 환경에서는 PMS가 객실, 재고, 요금, 예약의 기준 시스템 역할을 합니다. 운영자가 PMS에서 객실 재고나 요금을 변경하면, 채널매니저가 이 정보를 여러 OTA에 전파합니다.

반대로 OTA에서 예약이 발생하면, OTA 또는 채널매니저가 PMS로 예약 정보를 전달합니다. PMS는 해당 예약을 내부 예약 데이터로 저장하고, 객실 재고를 차감한 뒤 다른 판매 채널에도 변경된 재고를 다시 동기화합니다.

이 구조를 통해 여러 OTA에서 동시에 객실을 판매하더라도, 각 채널의 재고 상태를 최대한 일관되게 유지하고 중복 예약을 방지합니다.

### 현재 프로젝트의 동작 방식

실제 OTA 연동은 파트너 계약, 인증 환경, 채널별 API 스펙이 필요하기 때문에 이 프로젝트에서는 Mock OTA라는 서버로 대체했습니다.

Mock OTA를 통해 PMS에서 OTA로 객실 재고를 동기화하는 흐름과, OTA에서 발생한 예약 Webhook을 PMS가 수신해 내부 예약과 재고에 반영하는 흐름을 구현했습니다.

### 서버 모듈 구성

```text
apps/stayops-api   # PMS/CMS 백엔드 서버
apps/mock-ota      # 외부 OTA 연동을 대체하는 Mock OTA 서버
infra/             # 운영용/최소 실행용 Docker Compose 및 인프라 설정
loadtest/          # k6 부하 테스트와 테스트 데이터 시드 스크립트
```

`stayops-api`와 `mock-ota`는 서로 다른 서버로 실행됩니다. 실제 운영 환경의 외부 OTA를 직접 연동할 수 없는 제약을 Mock OTA 서버로 분리해 재현했습니다.

## b. 빌드 및 실행법

### MongoDB, Redis 실행

```bash
docker compose up -d mongodb mongo-init redis mongo-ota
./gradlew :apps:mock-ota:bootRun
./gradlew :apps:stayops-api:bootRun
```

### 애플리케이션 실행

```bash
./gradlew :apps:stayops-api:bootRun
./gradlew :apps:mock-ota:bootRun
```

### 빌드 및 테스트

```bash
./gradlew clean build
./gradlew test
```

## b. 사용 기술

| Category | Tool/Library | Version |
  |---|---|---:|
| Language | Kotlin JVM | 2.2.21 |
| Java | JDK | 24 |
| Build | Gradle Wrapper | 9.3.1 |
| Spring | Spring Boot | 4.0.3 |
|  | Spring Boot Starter WebMVC | 4.0.3 |
|  | Spring Boot Starter Security | 4.0.3 |
|  | Spring Boot Starter Data MongoDB | 4.0.3 |
|  | Spring Boot Starter Data Redis | 4.0.3 |
|  | Spring Boot Starter Session Data Redis | 4.0.3 |
|  | Spring Boot Starter Validation | 4.0.3 |
|  | Spring Boot Starter Actuator | 4.0.3 |
| Server | Embedded Tomcat | 11.0.18 |
| Database | MongoDB | 8 |
|  | MongoDB Java Driver | 5.6.3 |
| Cache / Session | Redis | 7-alpine |
| External Java Library  | Logback | 1.5.32 |
| Monitoring | Micrometer | 1.16.3 |
|  | Prometheus Metrics | 1.4.3 |
|  | Grafana | latest |
|  | Loki | latest |
|  | Promtail | latest |
| Test | Kotest | 6.1.0 |
|  | MockK | 1.14.2 |
|  | SpringMockK | 4.0.2 |
|  | MockWebServer | 4.12.0 |
|  | Testcontainers | Spring Boot managed |
| Deploy | Docker Image JDK | eclipse-temurin:24-jdk-alpine |
|  | Docker Image JRE | eclipse-temurin:24-jre-alpine |
| Load Test | k6 | not pinned in repo |

# B. 아키텍처

## a. 인프라 아키텍처

[인프라 설정](./infra)은 `Production`과 `Minimal`로 구분됩니다.

`Production`은 프로덕션 상황에서 발생할 수 있는 DB failover를 지원하는 Replica set(P-S-S) 구성과 로깅/메트릭을 지원하는 아키텍처 구성입니다. 

`Minimal`은 서비스를 저비용으로 유지하기 위한 최소 구성입니다. failover와 메트릭/로깅을 지원하지 않습니다.

프로젝트의 전반적인 설명은 `Production` 기준으로 합니다.

### Production

![](docs/img/04.png)
 
## b. 데이터 모델

![](docs/img/02.png)
![](docs/img/03.png)

---

# C. 프로젝트 달성 목표

숙박 도메인에서 실제 운영 상황에서 마주칠 수 있는 문제를 예상하여 직접 재현했습니다.

단순 CRUD 기능이 아닌 복잡한 도메인에서 실제 운영 환경과 유사한 환경을 만들어 어떤 문제가 생길지 예상하여 문제 해결을 위한 가설을 세운 뒤 검증하는 것을 목표로 했습니다.

## a. 부하 테스트로 서버 성능 측정

DAU, 결제 전환율 같은 운영 기준 지표가 없는 상태에서 애플리케이션과 DB 서버가 어느 정도의 트래픽을 수용할 수 있는지 확인할 필요가 있었습니다.

로깅과 메트릭을 구성하고 부하를 올려가며 Smoke test부터 Break-point 테스트까지 진행하면서 현재 서버 스펙에서 MongoDB primary write path가 병목이 되는 것을 확인하여 시스템의 처리 한계와 확장 기준을 수치로 파악했습니다.

부하 테스트 중에 API의 지연율 문제를 발견하고 'Pagenation'과 '복합 인덱스'를 적용해 p95 응답 시간을 **2.25s에서 230ms**로 개선했습니다.

또한 Failover 테스트를 통해 MongoDB의 Replica-Set 구성에서 발생하는 election이 정상 동작함을 검증하고 장애 상황을 대비한 timeout, 서버 응답 개선, 재시도 전략을 도입했습니다. 

자세한 해결 과정은 [블로그 글](https://www.romedev.kr/blog/load_test_on_mongodb)에 담았습니다. 

## b. 결제 승인 흐름의 외부 API 불일치 해결

예약 결제 승인 과정에서는 내부 MongoDB 트랜잭션과 외부 결제 PG(Payment Gateway) API 호출이 동시에 관여합니다. 

이때 PG 승인 요청이 성공했지만 DB 저장이 실패하거나, PG 호출 timeout같은 상황으로 실제 승인 여부를 알 수 없는 상황이 발생할 수 있다고 판단했습니다.

초기 구조에서는 결제 승인 API 안에서 PG를 직접 호출하고, 같은 흐름에서 Payment와 Reservation 상태를 변경했습니다. 

하지만 DB 트랜잭션은 외부 PG 호출을 rollback할 수 없기 때문에, 결제는 성공했지만 내부 예약 상태는 PENDING으로 남는 불일치가 생길 수 있습니다.

이를 해결하기 위해 결제 승인 요청을 즉시 완료 처리하지 않고, `Payment.CONFIRM_REQUESTED` 상태와 `PaymentOutboxMessage`를 같은 MongoDB 트랜잭션에 저장하도록 변경했습니다. API는 `202 Accepted`로 요청 접수만 응답하고, 실제 PG 승인과 상태 복구는 Outbox worker가 처리하도록 분리했습니다.

Outbox 메시지에는 paymentId, reservationId, orderId, amount, idempotencyKey, retryCount, lockedUntil 같은 재처리 정보를 저장했습니다. 이를 통해 서버가 중간에 종료되거나 외부 PG 응답이 불명확한 경우에도 처리해야 할 작업이 MongoDB에 남고, worker가 멱등성 키를 사용해 안전하게 재시도할 수 있도록 했습니다.

이 과정에서 Redis Queue를 단독 Outbox로 사용하지 않았습니다. 업무 상태는 MongoDB에 있는데 Redis에만 작업을 넣으면 다시 dual write 문제가 생기기 때문입니다. 

따라서 현재 단계에서는 MongoDB 기반 Outbox를 먼저 선택하고, 추후 처리량이나 분산 worker 요구가 커질 때 메시지 브로커를 보조 전달 채널로 도입하는 방향으로 결정했습니다.

의사 결정은 [결제 Outbox 설계](./docs/context/domain-model/blog-payment-outbox-refactoring.md)에 정리했습니다.

## c. OTA 재고 동기화와 Webhook 멱등성 설계

숙소 예약 시스템은 내부 예약만 처리하는 것으로 끝나지 않고 PMS의 객실 재고 변경이 OTA 채널로 전파되고, OTA에서 발생한 예약 Webhook이 다시 PMS에 반영되어야 합니다.

다만 실제 OTA API는 파트너 계약과 인증 환경이 필요하기 때문에 직접 연동할 수 없었습니다. 

단순히 메모리 안에서 성공/실패를 흉내 내는 방식은 네트워크 장애, 서명 검증, 재시도, Webhook 중복 수신 같은 운영 문제를 검증하기 어렵다고 판단했습니다.

그래서 Mock OTA를 별도 Spring Boot 서버로 분리했습니다. PMS와 Mock OTA가 실제 HTTP로 통신하게 만들고, 재고 변경이나 예약 생성 이벤트가 발생하면 `SyncTask`를 Outbox처럼 저장한 뒤 스케줄러가 OTA로 가용 재고를 전송하도록 구성했습니다.

Webhook은 중복 수신될 수 있다는 전제를 두고 `ProcessedWebhookEventRepository.saveIfAbsent()` 계약을 만들었습니다. MongoDB unique index가 중복 저장을 마지막으로 차단하고, 애플리케이션은 이미 처리한 이벤트라면 후속 로직을 실행하지 않도록 했습니다.

또한 OTA HTTP 클라이언트는 Adapter마다 직접 생성하지 않고 `otaRestClient` Bean으로 공유하도록 정리했습니다. 이 과정에서 connect timeout과 read timeout을 설정해 OTA 서버 지연이 스케줄러와 서블릿 스레드 고갈로 번지지 않도록 했습니다.

이 구조를 통해 실제 OTA를 붙이지 않고도 PMS -> OTA 재고 동기화, OTA -> PMS 예약 Webhook, 중복 Webhook 방지, 실패 작업 재시도 흐름을 하나의 시나리오로 검증할 수 있게 했습니다.

자세한 내용은 [채널 동기화 의사결정 기록](./docs/context/phase/phase-07/context.md)과 [OTA RestClient 설계 기록](./docs/context/infra/rest-client-bean-sharing.md)에 정리했습니다.

---

# D. 문서 업데이트 내역

업데이트 날짜: 2026-05-21

