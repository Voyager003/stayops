# 로컬 Redis DNS 해석 실패 수정

## 문제

Redis 세션 저장소 전환 이후, 통합 테스트는 통과했지만 로컬 서버 실행 시 다음 증상이 발생했다.

- `/actuator/health`에서 `redis`가 `DOWN`
- 로그인 또는 세션 저장 시 `RedisConnectionFailureException`
- 로그에 `UnknownHostException: Failed to resolve 'localhost'`

대표 로그:

```text
RedisConnectionFailureException: Unable to connect to Redis
Caused by: java.net.UnknownHostException: Failed to resolve 'localhost'
```

## 왜 테스트는 통과했고 로컬 실행은 실패했는가

테스트와 로컬 실행 환경이 달랐다.

### 테스트 환경

- `RedisSessionIntegrationTest`는 `@Import(TestcontainersConfiguration::class)`로 테스트 전용 설정 사용
- Testcontainers Redis를 별도로 기동
- `TestcontainersConfiguration` 안에 Lettuce DNS 우회 설정이 이미 존재

```kotlin
@Bean(destroyMethod = "shutdown")
fun lettuceClientResources(): ClientResources {
    return DefaultClientResources.builder()
        .dnsResolver(DnsResolvers.JVM_DEFAULT)
        .build()
}
```

즉, 테스트는 Redis 컨테이너도 있고 DNS 우회도 있어서 정상 연결됐다.

### 로컬 서버 실행 환경

- `application.yml`에서 Redis host를 `localhost`로 사용
- 메인 애플리케이션에는 테스트와 같은 DNS 우회 설정이 없었음
- 결과적으로 Spring Session이 Redis에 세션을 저장하려는 시점에 Lettuce/Netty DNS가 `localhost`를 해석하지 못해 실패

## 근본 원인

원인은 두 단계였다.

### 1. macOS + Lettuce/Netty DNS 조합에서 `localhost` 해석 실패

로그에 다음 경고가 먼저 나타났다.

```text
Can not find io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider in the classpath,
fallback to system defaults.
```

이후 실제 연결 시점에:

```text
UnknownHostException: Failed to resolve 'localhost'
```

즉, Redis 프로세스에 연결하기 전에 `localhost`를 IP로 바꾸는 단계에서 실패한 것이다.

### 2. 환경별 설정이 충분히 분리되어 있지 않음

기존에는 `application.yml`에 로컬 MongoDB/Redis 주소가 직접 들어 있었다.

```yaml
spring:
  mongodb:
    uri: mongodb://localhost:27017/stayops?replicaSet=rs0
  data:
    redis:
      host: localhost
      port: 6379
```

이 구조는 다음 문제를 만들었다.

- local 전용 우회 설정을 추가하기 어려움
- 운영 설정과 로컬 설정의 책임이 섞임
- DNS 이슈가 프로필 단위로 관리되지 않음

## 추가 발견: 운영 프로필 Redis 키 경로 오류

조사 중 `application-prod.yml`에 Redis 설정이 잘못 중첩되어 있음을 확인했다.

### 잘못된 기존 구조

```yaml
spring:
  mongodb:
    uri: mongodb://mongodb:27017/stayops?replicaSet=rs0
    redis:
      host: redis
      port: 6379
```

위 구조는 `spring.mongodb.redis.*`로 해석되므로 Spring Boot 4가 읽는 `spring.data.redis.*`와 다르다.

실제 배포가 바로 깨지지 않았던 이유는 `docker-compose.prod.yml`의 환경변수가 우연히 이를 덮어주고 있었기 때문이다.

```yaml
- SPRING_DATA_REDIS_HOST=redis
```

즉, 프로덕션은 "정상 설정"이 아니라 "환경변수 override에 의존해 간신히 동작하던 상태"였다.

## 해결

### 1. local 프로필 전용 Lettuce DNS 설정 추가

메인 코드에 `@Profile("local")` 설정 클래스를 추가했다.

```kotlin
@Configuration
@Profile("local")
class LocalLettuceDnsConfig {

    @Bean
    fun localClientResourcesBuilderCustomizer(): ClientResourcesBuilderCustomizer {
        return ClientResourcesBuilderCustomizer { builder ->
            builder.addressResolverGroup(DefaultAddressResolverGroup.INSTANCE)
        }
    }
}
```

의도:

- local 프로필에서만 JVM/Netty 기본 loopback resolver 경로를 사용
- macOS에서 `localhost` DNS 해석 실패가 Redis 연결 전체를 깨뜨리지 않도록 방지
- prod에는 영향 없이 local만 보정

### 2. local 설정을 `application-local.yml`로 분리

새 파일:

```yaml
spring:
  mongodb:
    uri: mongodb://localhost:27017/stayops?replicaSet=rs0
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

핵심 포인트는 Redis host를 `localhost` 대신 `127.0.0.1`로 바꾼 것이다.

이렇게 하면:

- 이름 해석(DNS)이 필요 없음
- loopback IP로 직접 접속
- 로컬 DNS 문제를 가장 단순하게 피할 수 있음

### 3. 공통 설정에는 세션 기본값만 유지

`application.yml`에는 공통 세션 설정만 남겼다.

```yaml
spring:
  session:
    redis:
      repository-type: default
    timeout: 30m
```

즉:

- local/prod 접속 주소는 프로필 파일이 담당
- 세션 저장소 동작 규칙은 공통 파일이 담당

### 4. 운영 프로필 Redis 설정 경로 수정

수정 후:

```yaml
spring:
  mongodb:
    uri: mongodb://mongodb:27017/stayops?replicaSet=rs0
  data:
    redis:
      host: redis
      port: 6379
```

이제 `application-prod.yml` 자체만으로도 Spring Boot 4가 올바르게 Redis 설정을 읽는다.

## 변경 파일

- `src/main/kotlin/com/stayops/shared/config/LocalLettuceDnsConfig.kt`
- `src/main/resources/application-local.yml`
- `src/main/resources/application.yml`
- `src/main/resources/application-prod.yml`
- `src/test/kotlin/com/stayops/shared/config/LocalLettuceDnsConfigTest.kt`
- `src/test/kotlin/com/stayops/shared/config/RedisPropertiesConfigurationTest.kt`
- `src/test/kotlin/com/stayops/TestcontainersConfiguration.kt`

## 검증

### 자동 검증

1. `LocalLettuceDnsConfigTest` 통과
   - local 프로필에서만 DNS 커스터마이저 빈 등록 확인
2. `RedisPropertiesConfigurationTest` 통과
   - `application-local.yml`의 `127.0.0.1`
   - `application-prod.yml`의 `spring.data.redis.*`
   - 공통 세션 설정 유지 확인
3. `RedisSessionIntegrationTest` 통과
4. `./gradlew test` 전체 통과

### 수동 검증

1. `docker compose up -d mongodb mongo-init redis`
2. `./gradlew bootRun --args='--server.port=18080'`
3. `http://127.0.0.1:18080/actuator/health` 확인

실제 응답:

```json
"redis":{"details":{"version":"7.4.8"},"status":"UP"}
```

즉, 수정 후 로컬 서버 실기동에서 Redis 연결이 정상화되었다.

## 프로덕션 영향 검토

### 영향 없음

- local DNS 보정 클래스는 `@Profile("local")` 이므로 prod에서 로드되지 않음
- 운영 Redis 연결 방식 자체는 `redis:6379` 유지
- 세션 저장소 구조나 API 동작은 변경 없음

### 오히려 개선된 점

- `application-prod.yml`의 Redis 설정 키 경로가 Spring Boot 4 규약에 맞게 수정됨
- 기존처럼 환경변수 override가 있어도 그대로 동작
- override가 누락돼도 프로필 파일 자체가 올바른 설정을 가짐

## 남은 사항

수정 후에도 다음 경고는 남아 있다.

```text
Can not find io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider in the classpath
```

하지만 이 경고는 현재 치명 장애가 아니다.

- 실제 Redis health는 `UP`
- 세션 저장도 정상
- `UnknownHostException`과 `RedisConnectionFailureException`은 재현되지 않음

이 경고까지 제거하려면 `netty-resolver-dns-native-macos` 의존성 추가를 별도 개선 작업으로 다루면 된다.

## 교훈

1. 테스트가 통과한다고 로컬 실행 환경도 같은 것은 아니다.
2. 테스트 전용 우회 설정이 메인 코드에 없으면, 통합 테스트는 성공해도 실제 개발 서버는 실패할 수 있다.
3. Spring Boot 메이저 버전 전환 이후에는 프로퍼티 키 경로(`spring.data.redis.*`, `spring.mongodb.*`)를 반드시 다시 확인해야 한다.
4. 환경별 접속 정보는 공통 설정에 두지 말고 프로필 파일로 분리하는 것이 안전하다.
