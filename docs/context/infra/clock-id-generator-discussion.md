# Clock / IdGenerator 추상화 도입 논의 기록

> **상태**: 논의 진행 중 (의사결정 미완료)
> **작성일**: 2026-04-09
> **관련 작업**: R-10-a (Clock + IdGenerator 최소 도입)
> **선행 결정 ADR**: `exception-boundary.md`, `cache-port-design.md`

이 문서는 R-10-a 시작 전 사용자와 문답으로 진행한 논의를 시간순으로 기록한 것이다. 최종 결정 후에는 별도의 **decision** 섹션을 추가하여 확정 사항을 명시할 예정이다.

---

## 1. 배경 및 발견된 문제

### 1.1 현황

조사 결과, StayOps 코드베이스에서 시간/ID 생성이 다음과 같이 **전역 함수로 직접 호출**되고 있다:

| 호출 패턴 | 발생 횟수 | 영향 범위 |
|---|---|---|
| `Instant.now()` | **81개** | 도메인 모델 10+ 개, Application 서비스 7+ 개, 스케줄러 1개 |
| `LocalDate.now()` | **5개** | `GuestEventHandler`, `RoomInventoryApplication`, `DashboardApi`(2곳), `ChannelApplication` |
| `UUID.randomUUID()` | **13개** | Application 서비스 11곳 + 도메인 모델 (`SyncTask`) 2곳 |

대표 예시:
```kotlin
// reservation/domain/model/Reservation.kt
fun confirm(): Reservation = copy(
    status = ReservationStatus.CONFIRMED,
    updatedAt = Instant.now()  // 전역 함수 호출
)

// booking/application/service/BookingApplication.kt
val expiresAt = Instant.now().plusSeconds(PENDING_TTL_MINUTES * 60)
val reservation = Reservation.create(
    id = UUID.randomUUID().toString(),
    ...
)
```

### 1.2 이것이 왜 문제인가

R-9 테스트 작성을 시도하면서 다음 시나리오들이 **사실상 테스트 불가능**함을 발견:

1. **#4 트랜잭션 롤백 검증**: `expiresAt`을 정확히 단언하려면 `Instant.now()`를 고정해야 함
2. **#9 노쇼 자동 전환**: 체크인 시각 + 2시간 시뮬레이션 필수
3. **#10 PENDING TTL 만료**: 15분 경과 시뮬레이션 필수 → 실제로 15분 기다릴 수 없음
4. **결정적 단언 불가**: `id`, `createdAt` 등이 매번 다른 값이라 `shouldBe`로 검증 불가

근본 원인은 **시간/ID 조회가 숨겨진 전역 의존성**이라는 점이다. 클래스의 생성자나 메서드 시그니처에 드러나지 않으므로 테스트가 통제할 수단이 없다.

---

## 2. DDD 프로덕션 레퍼런스 조사 결과

이 주제를 논의하기 전에, 실제 업계가 어떻게 다루는지 외부 레퍼런스를 조사했다. **두 가지 대립되는 정설**이 존재함을 확인.

### 2.1 공통 합의: 직접 `Instant.now()` 호출은 안티 패턴

모든 레퍼런스가 만장일치로 반대하는 패턴은 **도메인/서비스에서 정적 함수 직접 호출**이다.

- **Vladimir Khorikov** (*Enterprise Craftsmanship*): *"정적 클래스로 시간에 접근하는 것은 hidden input이며, 테스트 격리와 도메인 순수성을 모두 해친다"*
- **Tomas Tulka** (*blog.ttulka.com*): *"정적 메서드 mocking은 테스트 격리를 손상시킨다"*
- **Rémy Nowif** (DEV Community): *"시간에 대한 의존성을 인정하고 다른 의존성처럼 취급해야 한다"*

StayOps 현재 코드의 `Instant.now()` 81개 호출은 **모든 진영이 공통으로 지적하는 안티 패턴**에 해당한다.

### 2.2 패턴 1: Java `Clock` Spring Bean 주입 (실용주의·주류)

**지지자**: Tomas Tulka, Jonas G., Rémy Nowif, Spring Boot 메인 진영, Baeldung 다수.

**핵심 구조**:
```kotlin
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}

@Service
class OrderService(private val clock: Clock) {
    fun markAsProcessed() {
        val processedAt = Instant.now(clock)  // Java 표준 API의 Clock 오버로드
    }
}
```

**특징**:
- `java.time.Clock`이 JDK 8 표준이므로 추가 커스텀 인터페이스 불필요
- `LocalDate.now(clock)`, `Instant.now(clock)` 등 모든 `java.time` 타입이 Clock 인자 오버로드 보유
- Spring DI 철학과 잘 맞음
- `@MockBean` 또는 `@Primary` Bean으로 테스트 교체 용이

**내부 분파**:
- **1-A**: 도메인 객체에도 Clock 주입 (Jonas G.)
- **1-B**: Application 서비스에만 주입, 도메인은 `Instant` 값을 받음 (Tomas Tulka, Spring 메인)

### 2.3 패턴 2: "Application이 시각 계산 → 도메인에 값 전달" (순수주의)

**지지자**: Vladimir Khorikov (*"Unit Testing: Principles, Practices, and Patterns"* 저자), Microsoft .NET DDD 가이드, ABP.IO.

**핵심 주장**:
> *"Injecting IDateTimeService directly into domain models violates purity because it represents a hidden input — data fetched at runtime rather than passed explicitly."*
>
> *"A good rule of thumb is to inject the time as a service at the start of a business operation and then pass it as a value in the remainder of that operation."*

**핵심 구조**:
```kotlin
// Application 경계에서만 Clock 호출 (또는 Instant.now() 직접 호출)
@Service
class ReservationApplication(private val clock: Clock, ...) {
    fun createReservation(...): Reservation {
        val now = clock.instant()
        return Reservation.create(..., now = now)  // 값으로 전달
    }
}

// 도메인은 시간을 값 파라미터로 받음
class Reservation {
    companion object {
        fun create(..., now: Instant): Reservation =
            Reservation(..., createdAt = now, updatedAt = now)
    }
    fun confirm(now: Instant): Reservation = copy(status = CONFIRMED, updatedAt = now)
}
```

**철학적 배경**: Functional Core, Imperative Shell (Gary Bernhardt). 도메인은 순수 함수에 가까워야 하며, 모든 외부 입력은 명시적 파라미터여야 한다.

### 2.4 두 패턴 비교

| 관점 | 패턴 1 (Clock 주입) | 패턴 2 (값 전달) |
|---|---|---|
| 시간 획득 위치 | Application 또는 Service (선택적으로 도메인) | Application 경계에서만 |
| 도메인 의존성 | `Clock` 인터페이스 import (Jonas G.) | 없음 (순수 `Instant`) |
| hidden input | 여전히 존재 (Khorikov 관점) | 없음 |
| 보일러플레이트 | 최소 (생성자 1개 주입) | 약간 (값 전파) |
| 프로덕션 확산도 | Spring/Java 주류 | .NET/순수 DDD 주류 |
| 테스트 단순성 | `@Primary` Bean 교체 또는 mock | 값 직접 전달 (mock 불필요) |
| Kotlin 적합성 | 좋음 (Spring 친화) | 매우 좋음 (data class + 순수 함수) |
| StayOps 적용 파급 | Application만 수정 시 도메인 변경 없음 | 도메인 10+ 파일 시그니처 변경 |

### 2.5 Vaughn Vernon의 입장

*"Implementing Domain-Driven Design"*(Red Book)과 *"Domain-Driven Design Distilled"*에는 시간 처리에 대한 명시적 가이드가 거의 없다. 이것이 **많은 Spring Boot/Kotlin DDD 프로젝트가 StayOps와 똑같이 `Instant.now()`를 직접 호출하게 된 구조적 원인**으로 추정된다.

실제 오픈소스 프로젝트(`dustinsand/hex-arch-kotlin-spring-boot`, `allousas/implementing-hexagonal-architecture`)도 Clock 추상화 없이 `Instant.now()`를 직접 호출하는 상태이다. 시간 추상화를 도입한 프로젝트들은 대부분 **프로덕션 이슈를 겪은 이후** 리팩터링한 사례이다.

---

## 3. `Instant` vs `LocalDateTime` 타입 선택 논의

### 3.1 핵심 구분

| 타입 | 의미 | 적합 사례 |
|---|---|---|
| `Instant` | 절대 시점 (UTC epoch 기반, 타임존 무관) | 시스템 기록 시각, 만료 판단, 이벤트 발생 시점, 분산 시스템 비교 |
| `LocalDate` | 달력의 날짜 (시각 무의미) | 체크인/아웃 날짜, 요금 적용 기간, 재고 단위 |
| `LocalDateTime` | 타임존 없는 벽시계 시각 | 사용자 입력의 임시 보관, 단일 타임존 시스템 (제한적) |
| `ZonedDateTime` | 시각 + 타임존 | 항공편, 글로벌 이벤트 |

### 3.2 한국 현업 관행 vs 권장 패턴

**관찰된 현실**: 한국 SI/SaaS 현장에서 `LocalDateTime`이 압도적(~70%)으로 사용되고 있다. 이유:

1. JPA `@CreationTimestamp`/`@UpdateTimestamp` 디폴트 패턴
2. MySQL `DATETIME` 타입과 1:1 매칭
3. 단일 타임존(KST) 시스템 가정
4. 프론트엔드 표시 시 변환 불필요
5. Jackson 직렬화 호환성
6. 학습 곡선 (신입 개발자가 가장 먼저 만나는 타입)

**그러나 다음 조건 중 하나라도 해당되면 `Instant`가 안전**:

- 서버 다른 리전 배포 가능성
- 외부 API 시각 데이터 교환 (PG, OTA, 항공편)
- 다중 데이터센터·재해 복구
- 이벤트 시각의 정확한 비교 (감사 로그, 분산 트랜잭션)
- DB의 `TIMESTAMP`(자동 UTC 변환) 또는 MongoDB BSON Date

### 3.3 StayOps 컨텍스트 분석

StayOps는 외부 시스템 연동 비중이 크다:

- **OTA 연동**: Agoda, Booking.com 등 글로벌 서비스. 자체 시스템에서 UTC/ISO 8601 형식으로 시각 송신
- **MongoDB 사용**: BSON Date는 UTC 기반. `LocalDateTime` 매핑 시 변환 시점에 모호성 발생
- **Webhook 멱등성** (`processedAt`): 외부 시스템과 시각 비교
- **결제 게이트웨이** (Toss): PG사 표준 (UTC) 따름
- **재시도 스케줄러** (`SyncTask.nextRetryAt`): 정확한 시점 비교

**결론**: StayOps는 **`Instant` + `LocalDate` 조합이 의미론적으로 올바른 선택**이다. 현재 도메인 모델이 이미 그렇게 설계되어 있는 것은 잘 된 결정이며, R-10에서 변경하지 않는다.

### 3.4 `LocalDateTime` 검토 후 결론

> 사용자 질문: *"LocalDateTime으로 인스턴스를 생성한다고 했을 때 순수한 Java 코드라고 해도 되지 않나요?"*

**답변 요지**: 문법적/구조적으로는 100% 순수한 JDK 표준 코드이다. 다만 `Instant`와 `LocalDateTime`은 "순수성" 차원의 차이가 아니라 **"표현하는 개념"** 차원의 차이이다. 컴파일은 되지만 의미가 부정확하면 분산 환경·DB 매핑·DST 변환 시 버그의 원인이 된다.

---

## 4. DI와 테스트 용이성의 관계

> 사용자 질문: *"의존성 주입으로 테스트를 쉽게 만든다고 볼 수 있는 것인가요?"*

### 4.1 답변

**네, 그렇게 볼 수 있다.** 다만 더 정확한 표현은:

> *"DI는 클래스의 협력 객체를 외부에서 결정 가능하게 만든다. 그 결과 (a) 프로덕션에서는 진짜 객체를 주입받고, (b) 테스트에서는 가짜/제어 가능한 객체를 주입받을 수 있다. 같은 코드가 두 환경에서 다른 협력 객체와 함께 동작하므로, 테스트가 쉬워지는 것은 자연스러운 결과다."*

### 4.2 DI의 효과

| 효과 | 설명 | 테스트와의 관계 |
|---|---|---|
| 명시적 의존성 | 생성자만 봐도 무엇이 필요한지 알 수 있음 | 무엇을 mock해야 하는지 명확 |
| 교체 가능성 | 인터페이스 기반 주입 시 구현체 교체 자유 | 테스트에서 fake/mock 주입 |
| 결합도 감소 | 추상화에 의존, 구체 구현 무관 | 테스트가 인프라에 결합되지 않음 |
| 단일 책임 강제 | 너무 많이 주입받으면 SRP 위반이 보임 | 거대한 mock 셋업이 SRP 위반 신호 |
| 수명 주기 분리 | Spring이 객체 생성/소멸 관리 | 테스트가 직접 통제 |

### 4.3 학계의 두 관점

- **관점 A** (Robert Martin): *"DI의 본질은 결합도 감소이며, 테스트는 결과"*
- **관점 B** (Martin Fowler, Misko Hevery): *"DI는 사실상 테스트를 쉽게 하기 위해 발명되었다"*

**현실적 답변**: 둘 다 맞다. DI는 결합도 감소와 테스트 용이성을 동시에 달성한다.

---

## 5. Bean의 환경별 동작

> 사용자 질문: *"저 Bean은 테스트에서만 사용되는 것인가요?"*

### 5.1 답변: 아니다

**같은 인터페이스의 다른 구현체가 환경별로 주입된다.**

```kotlin
// 인터페이스 — 도메인 레이어
interface IdGenerator {
    fun generate(): String
}

// 프로덕션 구현체
@Component
class UuidIdGenerator : IdGenerator {
    override fun generate(): String = UUID.randomUUID().toString()
}

// 테스트 구현체
class FixedIdGenerator(vararg ids: String) : IdGenerator {
    private val iter = ids.iterator()
    override fun generate(): String = iter.next()
}
```

### 5.2 환경별 동작

| 환경 | `IdGenerator` 구현체 | `Clock` 구현체 |
|---|---|---|
| 프로덕션 | `UuidIdGenerator` | `Clock.systemDefaultZone()` |
| 통합 테스트 | `UuidIdGenerator` 또는 sequential | `Clock.systemDefaultZone()` 또는 `Clock.fixed()` |
| 단위 테스트 | `FixedIdGenerator` 또는 mock | `Clock.fixed()` 또는 `MutableClock` |
| 로컬 개발 | `UuidIdGenerator` | `Clock.systemDefaultZone()` |

### 5.3 핵심

**`BookingApplication.createBooking()` 코드 자체는 한 줄도 변하지 않는다**. 같은 코드가 환경에 따라 다른 협력 객체와 함께 동작한다. 호출자 코드는 어떤 구현체와 동작하는지 모르며, 알 필요도 없다.

---

## 6. Spring PSA와의 관계

> 사용자 질문: *"Spring PSA와도 연관이 있는 건가요?"*

### 6.1 답변: 본질적으로 같은 개념이다

**Spring PSA(Portable Service Abstraction)**는 *"서로 다른 외부 기술을 일관된 인터페이스로 추상화하여, 구현 기술이 바뀌어도 비즈니스 코드는 변하지 않도록 한다"*는 원칙이다.

Spring이 제공하는 PSA 사례:
- **트랜잭션**: `PlatformTransactionManager` (JpaTransactionManager / MongoTransactionManager / JtaTransactionManager 등 → `@Transactional`로 통일)
- **캐시**: `CacheManager` (Redis / EhCache / Caffeine / Hazelcast → `@Cacheable`로 통일)
- **메일**: `JavaMailSender`
- **메시징**: `JmsTemplate`, `RabbitTemplate`
- **데이터 액세스**: `Repository` 패턴

R-10-a에서 도입하는 Clock과 IdGenerator는 **Spring의 PSA 철학을 도메인 레벨에 적용**한 것이다. Spring은 인프라 관심사(트랜잭션, 캐시, 메일)에 PSA를 적용했고, 우리는 더 작은 관심사(시간, ID 생성)에도 같은 패턴을 적용한다.

### 6.2 차이점

| 항목 | Spring PSA | 우리의 Clock | 우리의 IdGenerator |
|---|---|---|---|
| 인터페이스 정의자 | Spring Framework | JDK (`java.time.Clock`) | StayOps 도메인 |
| 구현체 제공자 | Spring + 벤더 | JDK + 우리 | 우리 |
| Bean 등록 방식 | Spring Boot AutoConfig | `@Configuration` 수동 | `@Component` 자동 |
| 비즈니스 코드 영향 | 없음 | 없음 | 없음 |
| PSA 철학 준수 | 100% | 100% | 100% |

### 6.3 결론

> *"Spring PSA는 Spring이 제공하는 인프라 추상화이고, R-10-a에서 도입하는 Clock과 IdGenerator는 같은 PSA 철학을 우리 손으로 도메인 관심사(시간, ID 생성)에 적용하는 마이크로 PSA다. 둘은 본질적으로 같은 패턴이다."*

이미 StayOps가 사용 중인 PSA들:
- 트랜잭션 PSA (`@Transactional`)
- Repository PSA (`MongoXxxRepository`)
- HTTP Client (`RestClient`)
- 보안 추상화 (`SecurityContext`)
- Jackson 직렬화 추상화
- **`RoomInventoryCache` (R-3에서 우리가 직접 추가한 마이크로 PSA)**

R-10-a에서 추가될 마이크로 PSA:
- **시간 PSA** (`java.time.Clock`)
- **ID 생성 PSA** (`IdGenerator`)

R-10-b에서 추가 후보:
- `DomainEventPublisher` (Spring `ApplicationEventPublisher` 추상화)
- `CurrentUserProvider` (`SecurityContextHolder` 정적 호출 추상화)
- `OtaConfigProvider` (`@Value` 추상화)

---

## 7. R-10-a "최소 도입"의 의미

### 7.1 두 가지 도입 범위

**전면 도입 (옵션 A)**:
- 모든 도메인 모델(10+ 개)의 factory/transition 메서드에 `clock: Clock` 또는 `now: Instant` 파라미터 추가
- 모든 호출자 수정 (Application 서비스, 단위 테스트 수십 개)
- 1주일 이상의 작업
- R-9 시작 전까지 완료해야 함

**최소 도입 (옵션 C)**:
- R-9 첫 시나리오(#4 트랜잭션 롤백)가 의존하는 핵심 3곳만:
  - `BookingApplication`
  - `PendingReservationScheduler`
  - `WebhookApplication`
- 도메인 모델 시그니처 그대로
- 30분 작업
- 즉시 R-9 시작 가능
- R-9 완료 후 R-10-b에서 일관성 회복

### 7.2 "최소 도입"의 정당화

Martin Fowler의 *Refactoring* 원칙:
> *"Make the change easy, then make the easy change."*

R-10-a는 "변경을 쉽게 만드는" 단계이고, R-9는 "쉬운 변경을 하는" 단계이다. 점진적 리팩터링의 정석.

### 7.3 의도된 과도기 상태

R-10-a 직후 코드는 **일관되지 않은 상태**가 된다:
- `BookingApplication`은 Clock + IdGenerator 사용
- `RoomApplication`은 여전히 `UUID.randomUUID()` 직접 호출

이것은 **위험 분산을 위한 의도된 과도기**이며, R-10-b에서 일관성을 회복한다.

---

## 8. 미결정 사항

다음 항목들이 사용자 결정 대기 중이다:

### 8.1 Clock 도입 패턴

- [ ] **패턴 1-B (권장)**: Spring `java.time.Clock` Bean 등록 → Application에만 주입 → 도메인은 `Instant` 값 받기
- [ ] **패턴 2 (Khorikov 순수주의)**: Clock 주입 없이 Application이 `Instant.now()` 직접 호출 → 값 전달
- [ ] **패턴 1-A (Jonas G.)**: 도메인 transition 메서드까지 Clock 파라미터 전달

### 8.2 R-10-a 범위

- [ ] **Application 3곳만 Clock 주입** (`BookingApplication`, `PendingReservationScheduler`, `WebhookApplication`)
- [ ] **Application 전체 + IdGenerator 동시 적용**
- [ ] **Clock만 먼저, IdGenerator는 R-10-b로**

### 8.3 IdGenerator 위치 및 범위

- [ ] 인터페이스 위치: `shared/domain/IdGenerator.kt`
- [ ] 구현체 위치: `shared/infrastructure/UuidIdGenerator.kt`
- [ ] 적용 범위: 13개 호출 전체 vs 핵심 2~3곳만

### 8.4 도메인 모델 시그니처 변경 여부

- [ ] R-10-a에서는 도메인 변경 없음 (Application 내부에서만 Clock 사용)
- [ ] R-10-b에서 도메인 transition 메서드에 `now: Instant` 파라미터 추가 (전면 적용)

---

## 9. 작업자 가이드라인 (잠정)

이 논의를 바탕으로 최종 결정이 내려지면 다음 가이드라인이 확립될 예정:

1. **신규 코드는 절대 `Instant.now()` / `LocalDate.now()` / `UUID.randomUUID()`를 직접 호출하지 않는다.** 주입받은 Clock 또는 IdGenerator를 사용한다.
2. **`LocalDateTime`을 도메인 모델에 사용하지 않는다.** StayOps의 외부 시스템 연동 특성상 `Instant`(시점) 또는 `LocalDate`(날짜) 중 하나를 선택한다.
3. **Clock과 IdGenerator는 인터페이스이며, 환경별 구현체로 교체된다.** 프로덕션은 `Clock.systemDefaultZone()` / `UuidIdGenerator`, 테스트는 `Clock.fixed()` / `FixedIdGenerator`.
4. **`Instant.now()` 직접 호출이 발견되면 코드 리뷰에서 거부한다.** Clock 주입을 요구한다.

---

## 10. 참고 자료

### 외부 레퍼런스
- [Domain model purity and the current time · Enterprise Craftsmanship (Vladimir Khorikov)](https://enterprisecraftsmanship.com/posts/domain-model-purity-current-time/)
- [How to Effectively Test Time-Dependent Code · jonasg.io](https://jonasg.io/posts/how-to-effectively-test-time-dependent-code/)
- [How to Test Date and Time in Spring Boot · Tomas Tulka's Blog](https://blog.ttulka.com/how-to-test-date-and-time-in-spring-boot/)
- [Controlling the Time in Java · DEV Community (Rémy Nowif)](https://dev.to/rnowif/controlling-the-time-in-java-43kh)
- [Time Travel In Spring · Industrial Logic](https://www.industriallogic.com/blog/time-travel-in-spring/)
- [Spring Boot Issue #31397 · Auto-configure java.time.Clock](https://github.com/spring-projects/spring-boot/issues/31397)
- [Hexagonal Architecture, DDD, and Spring · Baeldung](https://www.baeldung.com/hexagonal-architecture-ddd-spring)

### 관련 ADR
- `docs/context/infra/exception-boundary.md` — Infrastructure 예외 경계 설계 (R-2)
- `docs/context/infra/cache-port-design.md` — RoomInventoryCache 도메인 포트 설계 (R-3)

### 영향받을 파일 목록 (R-10-a 최소 도입 기준)

**신규**:
- `src/main/kotlin/com/stayops/shared/infrastructure/ClockConfig.kt`
- `src/main/kotlin/com/stayops/shared/domain/IdGenerator.kt`
- `src/main/kotlin/com/stayops/shared/infrastructure/UuidIdGenerator.kt`
- `src/test/kotlin/com/stayops/shared/domain/FixedIdGenerator.kt` (테스트 헬퍼)

**수정 (3~5개)**:
- `src/main/kotlin/com/stayops/booking/application/service/BookingApplication.kt`
- `src/main/kotlin/com/stayops/payment/infrastructure/scheduler/PendingReservationScheduler.kt`
- `src/main/kotlin/com/stayops/channel/application/service/WebhookApplication.kt`
- `src/main/kotlin/com/stayops/reservation/application/service/ReservationApplication.kt` (선택적)

**수정 (테스트)**:
- `src/test/kotlin/com/stayops/booking/application/service/BookingApplicationTest.kt`
- `src/test/kotlin/com/stayops/channel/application/service/WebhookApplicationTest.kt`
- `src/test/kotlin/com/stayops/payment/infrastructure/scheduler/PendingReservationSchedulerTest.kt`

---

## 11. 다음 단계

1. 사용자가 8절의 미결정 사항을 확정
2. 결정 사항을 이 문서의 새 섹션 **"12. 최종 결정 (Decision)"**으로 기록
3. R-10-a 실제 구현 진행
4. R-10-a 완료 후 이 문서를 ADR 형식으로 정리 (`clock-id-generator.md` 또는 `time-abstraction.md`로 rename)
