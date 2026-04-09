# OTA RestClient Bean 공유 설계

## 배경

Phase 7(Channel) 구현 중 OTA와 HTTP 통신하는 두 개의 Infrastructure Adapter가 각자 독립적으로 `RestClient`를 생성하는 구조가 누적되었다.

**리팩터링 전 상태**:

```kotlin
// src/main/kotlin/com/stayops/channel/infrastructure/external/HttpChannelSyncAdapter.kt
@Component
class HttpChannelSyncAdapter : ChannelSyncAdapter {
    private val restClient = RestClient.create()
    // ...
}

// src/main/kotlin/com/stayops/channel/infrastructure/external/HttpChannelInventoryQueryAdapter.kt
@Component
class HttpChannelInventoryQueryAdapter : ChannelInventoryQueryAdapter {
    private val restClient = RestClient.create()
    // ...
}
```

두 Adapter 모두 동일한 OTA 서버(`https://mock-ota:8081` 또는 실제 OTA 엔드포인트) 에 요청하지만 **독립된 `RestClient` 인스턴스**를 사용한다.

### 이것이 왜 문제인가

1. **연결 풀 분리**: `RestClient.create()` 의 기본 팩토리는 `SimpleClientHttpRequestFactory`(`HttpURLConnection` 기반) 이며 인스턴스 간 TCP 연결을 재사용하지 않는다. 같은 호스트에 대한 요청이 두 Adapter에서 발생해도 연결 재사용 이득 없음.
2. **타임아웃 부재**: 기본 `RestClient.create()` 는 타임아웃 설정이 없다. OTA 서버 장애 시 요청 스레드가 무한 대기 → `ChannelSyncApplication.processPendingTasks()` 가 `@Scheduled` 로 돌면서 대기 큐가 쌓이고, Spring MVC 스레드 풀 고갈로 번져 전체 서비스 가용성 위협.
3. **설정 중복**: 인증 헤더·에러 핸들러·ObjectMapper 설정을 추가하면 Adapter 2곳에 각각 반복 작성해야 함 (SRP 위반 + DRY 위반).
4. **Cross-cutting concern 추가 비용**: 로깅 인터셉터·분산 추적·메트릭스 수집을 넣으려면 N개 Adapter를 수정해야 한다. AOP 관점에서 관심사 분리가 안 된 상태.
5. **Spring PSA / DIP 관점**: Adapter 가 HTTP 클라이언트 저수준 세부사항(팩토리 선택, 빌더 호출)을 직접 알고 있다. 프레임워크가 주입하는 POJO 원칙 위반이며, HTTP 라이브러리를 교체하려면 여러 파일을 수정해야 한다.

## 선행 사례

Payment 도메인의 `TossPaymentsConfig` 는 이미 정석 패턴으로 구현되어 있다:

```kotlin
@Configuration
class TossPaymentsConfig {
    @Bean
    fun tossRestClient(
        properties: TossPaymentsProperties,
        objectMapper: ObjectMapper
    ): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient)
        requestFactory.setReadTimeout(properties.readTimeout)

        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $encodedKey")
            .defaultStatusHandler({ it.isError }) { _, response -> /* ... */ }
            .build()
    }
}
```

핵심은 **(1) `@Bean` 으로 한 군데서 생성, (2) `ConfigurationProperties` 로 타임아웃 외부화, (3) `JdkClientHttpRequestFactory` 로 `HttpClient` 재사용, (4) 기본 헤더·에러 핸들러 일괄 적용**.

OTA Adapter 도 동일한 패턴을 따라야 한다.

## 결정

**OTA 용 `RestClient` 를 `@Bean` 으로 한 곳에서 생성하고 두 Adapter 가 생성자 주입으로 공유한다.**

### 적용 규칙

| 항목 | 결정 |
|---|---|
| Bean 이름 | `otaRestClient` (명시적 이름, Adapter 생성자의 `@Qualifier` 불필요 — OTA 전용 유일 Bean) |
| 위치 | `src/main/kotlin/com/stayops/channel/infrastructure/external/OtaHttpClientConfig.kt` |
| HTTP 엔진 | `JdkClientHttpRequestFactory(java.net.http.HttpClient)` — Toss 설정과 일관성 유지 |
| `connectTimeout` | `5초` (Toss 와 동일) |
| `readTimeout` | `10초` (OTA 는 Toss 보다 짧게 — OTA 응답 지연이 예약 흐름 지연으로 번지지 않도록) |
| `baseUrl` | 설정하지 않음 — Adapter 가 `endpoint` 파라미터로 여러 OTA 호스트를 동적 호출 |
| 공통 헤더 | `Content-Type: application/json` 만 기본 설정 — OTA 별 API Key/서명은 호출 시점에 Adapter 가 개별 추가 |
| 에러 핸들러 | 전역 핸들러 설정 안 함 — Adapter 계약이 "예외를 `SyncResult(success=false)` 로 흡수"이므로 개별 `try/catch` 유지 |
| 설정 외부화 | `OtaHttpClientProperties(connectTimeout, readTimeout)` + `application.yml` 의 `ota.http.*` 키 |

### 제외 항목

- **BaseUrl 설정 안 하는 이유**: 여러 OTA (Agoda/Booking.com/Expedia) 를 하나의 PMS 에서 동시 연동할 때 채널마다 다른 호스트로 요청해야 한다. `baseUrl` 을 고정하면 유연성 상실.
- **전역 에러 핸들러 미도입**: `HttpChannelSyncAdapter` 는 `SyncResult(success=false, errorMessage=…)` 반환 계약이고, `HttpChannelInventoryQueryAdapter` 는 `BusinessException("OTA_CONNECTION_FAILED", …)` 으로 변환하는 계약이다. 두 계약이 다르므로 전역 핸들러는 부적절.
- **재시도(Retry) 미도입**: Spring Retry 나 Resilience4j 도입은 별도 주제. 현재 재시도는 `SyncTask` 엔티티의 `retryCount` + `@Scheduled` 폴링 방식으로 이미 구현되어 있어 HTTP 레벨 재시도는 중복이 된다.

## 효과

| 측면 | Before | After |
|---|---|---|
| 연결 재사용 | 없음 (Adapter 별 독립) | `HttpClient` 공유 → 같은 호스트 TCP 연결 재사용, HTTP/2 multiplexing 가능 |
| 타임아웃 | 무한 대기 가능 (기본값) | `connect 5s / read 10s` 로 상한 설정, 스레드 풀 고갈 방지 |
| 설정 지점 | Adapter 2곳에 분산 | `@Configuration` 1곳 |
| Cross-cutting (로깅/추적/메트릭) | Adapter 마다 중복 작성 필요 | `RestClient.builder().requestInterceptor(...)` 한 번으로 전파 |
| HTTP 엔진 교체 | 여러 파일 수정 | `@Bean` 메서드 한 줄 수정 |
| 테스트 | Adapter 별 개별 설정 | Bean 을 `@MockBean` 으로 교체 가능, 공통 `MockWebServer` 재사용 가능 |

## 적용 대상 파일

**신규**:
- `src/main/kotlin/com/stayops/channel/infrastructure/external/OtaHttpClientConfig.kt`

**수정**:
- `src/main/kotlin/com/stayops/channel/infrastructure/external/HttpChannelSyncAdapter.kt` — `private val restClient = RestClient.create()` 제거, 생성자 파라미터 `otaRestClient: RestClient` 추가
- `src/main/kotlin/com/stayops/channel/infrastructure/external/HttpChannelInventoryQueryAdapter.kt` — 동일 변경
- `src/main/resources/application.yml` — `ota.http.connect-timeout`, `ota.http.read-timeout` 추가

## 검증 방법

1. `./gradlew compileKotlin` 성공
2. `./gradlew :test --tests "com.stayops.channel.*"` 통과
3. grep 으로 `RestClient.create()` 호출이 OTA Adapter 에서 0건임을 확인 (남은 곳은 `TossPaymentsConfig` 하나뿐, 이는 별도 도메인의 이미 정석 패턴)
4. `application.yml` 에 `ota.http.*` 키 존재 확인

## 참고

- [Spring RestClient 공식 문서](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
- 기존 정석 사례: `src/main/kotlin/com/stayops/payment/infrastructure/external/TossPaymentsConfig.kt`
- 블로그: `blog/romedev/src/content/posts/ko/spring_psa_clock_injection.md` — R-10-b 의 "Feature 5: RestClient Bean 공유" 섹션
