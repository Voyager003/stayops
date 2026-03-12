# AGENTS.md

## Project Overview

This is a **PMS system** built with Kotlin and Spring Boot.
It follows **Domain-Driven Design (DDD)** and **Layered Architecture** principles.

---

## Architecture

### Design Principles

- **Domain-Driven Design (DDD)**: Model the core domain with rich domain objects. Business logic lives inside the domain layer, not in services or controllers.
- **Layered Architecture**: Strict unidirectional dependency flow.

```
Controller (API Layer)
↓
Service (Application Layer)
↓
Domain (Domain Layer)
↑
Repository / External Adapters (Infrastructure Layer)
```

### Package Structure

```
src/main/java/com/example/reservation/
├── domain/               # Pure Java domain models, no framework annotations
│   ├── model/            # Aggregates, Entities, Value Objects
│   ├── repository/       # Repository interfaces (not implementations)
│   └── service/          # Domain services
├── application/          # Use cases, orchestration, transaction boundaries
│   └── service/
├── infrastructure/       # DB implementations, external API clients
│   ├── persistence/      # Repository implementations, RowMappers
│   └── external/         # Toss Payments, etc.
├── api/                  # Controllers, request/response DTOs
│   ├── controller/
│   └── dto/
└── common/               # Exceptions, utils, constants
```

### Layer Rules

- `domain/` must contain **pure Java only** — no Spring, JPA, or framework annotations
- `application/` orchestrates domain objects; owns `@Transactional` boundaries
- `infrastructure/` implements domain repository interfaces; owns DB/external concerns
- `api/` handles HTTP in/out only; delegates all logic to application services
- Dependencies always point **inward** toward the domain

---

## Development Workflow: Phase Process

### Structure

Each Phase is divided into sub-steps (Phase N-1, Phase N-2, ...).

### Rules

1. **Plan**: At the start of each Phase, present a full list of sub-steps and their tasks before writing any code.
2. **Execute per sub-step**:
- Write the code
- Validate together with the user (test results, lint, code review)
- Commit only after validation passes
3. **Wait**: After each sub-step completes, **always stop and wait for the user's next command**.
4. **Never auto-proceed**: Do not move to the next sub-step without an explicit user instruction.

### Flow

```
Phase N-1: Write code + test → [STOP] → Validate with user → Wait for user command → Commit
↓ "Continue"
Phase N-2: Write code + test → [STOP] → Validate with user → Wait for user command → Commit
↓ "Continue"
...
```

### Sub-step 완료 보고 형식

각 sub-step 작업 완료 후 반드시 아래 형식으로 보고한다:

**1. 작업 내역**
- 생성/수정/삭제한 파일 목록
- 각 파일에서 수행한 작업 요약
- 테스트 실행 결과

**2. 판단 근거**
- 왜 이 구조/패턴을 선택했는지
- Phase 문서의 어떤 요구사항을 충족하는지
- 대안이 있었다면 왜 이 방식을 택했는지

### Refactoring Flow

리팩토링/코드 수정 시에도 동일한 흐름을 따른다:
```
Refactor code + Update tests (must pass) → [STOP] → Validate with user → Wait for user command → Commit
```

### Commit Unit Rules

- **새 코드 작성**: 프로덕션 코드 + 테스트 코드 = **하나의 커밋**
- **리팩토링/수정**: 수정된 코드 + 수정된 테스트 코드(성공) = **하나의 커밋**
- 테스트 없는 코드는 커밋하지 않는다
- 테스트가 실패하는 상태로 커밋하지 않는다

---

## TDD (Test-Driven Development)

**Always write tests first.** No production code without a failing test.

### Cycle

1. **RED**: Write a failing test that describes the desired behavior
2. **GREEN**: Write the minimum production code to make the test pass
3. **REFACTOR**: Clean up the code while keeping all tests green

### Test Framework

- **Kotest** (BehaviorSpec) — Given/When/Then 구조로 도메인 행위를 표현
- **MockK** — Kotlin 네이티브 mocking
- **Testcontainers** — 실제 MongoDB/Redis 기반 통합 테스트

### Test Strategy

| Layer | Test Type | Tool |
|---|---|---|
| Domain model | Unit test | Kotest BehaviorSpec |
| Application service | Unit test with mocks | Kotest BehaviorSpec + MockK |
| Repository | Unit test | Kotest + Testcontainers (MongoDB, Redis) |
| API (E2E) | Integration test | Kotest + Testcontainers |

### Test Style Convention

All tests use **Kotest BehaviorSpec** with Given/When/Then structure.

```kotlin
class ReservationTest : BehaviorSpec({
    given("CONFIRMED 상태의 예약") {
        val reservation = Reservation.createConfirmed(...)

        `when`("체크인하면") {
            val result = reservation.checkIn(roomId = "R101")

            then("상태가 CHECKED_IN으로 변경된다") {
                result.status shouldBe ReservationStatus.CHECKED_IN
            }
            then("roomId가 배정된다") {
                result.roomId shouldBe "R101"
            }
        }

        `when`("취소하면") {
            val result = reservation.cancel()

            then("상태가 CANCELLED로 변경된다") {
                result.status shouldBe ReservationStatus.CANCELLED
            }
        }
    }
})
```

---

## Commit Convention

### Commit Unit

- **새 코드 작성**: 프로덕션 코드 + 테스트 코드 = **하나의 커밋**
- **리팩토링/수정**: 수정된 코드 + 수정된 테스트 코드(성공) = **하나의 커밋**
- 테스트 없는 코드는 커밋하지 않는다
- 테스트가 실패하는 상태로 커밋하지 않는다 (`./gradlew test` must succeed)

### Commit Message Format

- Written in **Korean**
- Format: `<type>: <description>`

        | Type | Usage |
        |---|---|
        | `feat` | New feature |
        | `fix` | Bug fix |
        | `refactor` | Code restructuring without behavior change |
        | `test` | Adding or modifying tests |
        | `chore` | Build, config, dependency changes |
        | `docs` | Documentation only |

        **Examples**
        ```
        feat: 티타임 슬롯 예약 도메인 모델 및 단위 테스트 추가
        feat: 결제 승인 API 및 Webhook 수신 처리 구현
        fix: JWT 액세스 토큰 만료 검증 로직 수정
        refactor: 예약 서비스 중복 검증 코드 제거
        test: 동시 예약 요청 Race Condition 테스트 추가
        chore: Testcontainers MySQL 의존성 추가
        ```

        ---

        ## Naming & Language Conventions

        | Target | Convention | Language |
        |---|---|---|
        | Code comments | free-form prose | English |
        | Commit messages | `<type>: <description>` | Korean |
                | Variable / method names | camelCase | English |
                | Class names | PascalCase | English |
                | Database columns | snake_case | English |
                | Package names | lowercase | English |
                | Test method names | snake_case | English |

                ---

                ## SOLID Principles

                Apply these principles at all times:

                - **S — Single Responsibility**: Each class has one reason to change. Controllers handle HTTP, services handle orchestration, domain models handle business rules.
                - **O — Open/Closed**: Extend behavior through interfaces and new implementations. Avoid modifying existing stable code.
                - **L — Liskov Substitution**: Any implementation of an interface must be substitutable without breaking correctness. Repository implementations must honor the interface contract fully.
                - **I — Interface Segregation**: Keep interfaces small and focused. Prefer `ReservationReader` and `ReservationWriter` over one fat `ReservationRepository` if consumers differ.
                - **D — Dependency Inversion**: High-level modules depend on abstractions. Always inject dependencies via constructor. Domain layer defines interfaces; infrastructure implements them.

                ---

                ## Domain Model Rules

                - Domain objects are **pure Java** — no `@Entity`, `@Column`, or Spring annotations
                - Use **static factory methods** (`from()`, `of()`, `create()`) instead of public constructors
                - Use **Value Objects** (Java records) for concepts like `Money`, `PlayerId`, `OrderId`
                - Business rules and invariants are enforced **inside the domain object**, not in services
                - Domain objects are **immutable** where possible; return new instances on state change

                ```java
                // Good — business rule lives in the domain
                public Reservation cancel() {
                if (!isCancellable()) {
                throw new IllegalStateException("Cannot cancel a reservation in status: " + this.status);
                }
                return Reservation.builder()
                .id(this.id)
                // ... copy fields
                .status(ReservationStatus.CANCELLED)
                .build();
                }

                // Bad — business rule leaks into service
                if (reservation.getStatus() != PAID) {
                reservationRepository.updateStatus(id, CANCELLED);
                }
                ```

                ---

                ## Security Rules

                - **Never hardcode secrets** — use `application-secret.yml` (gitignored) or environment variables
                - Sensitive fields (passwords, card numbers) must never appear in logs
                - Webhook endpoints must verify the signature before processing any payload
                - JWT secret key must be at least 256 bits
                - All environment-specific configs use Spring profiles: `local`, `dev`, `prod`

                ---

                ## Environment Configuration

                ```
                application.yml          # Common config
                application-local.yml    # Local MySQL (Docker)
                application-prod.yml     # AWS RDS MySQL (loaded from env vars)
                application-secret.yml   # Secrets — never commit to Git
                ```

                `.gitignore` must include:
                ```
                application-secret.yml
                .env
                ```

                ---

                ## What Agents Must Never Do

                - Never skip writing a test before production code
                - Never commit with failing tests
                - Never auto-proceed to the next Phase sub-step without user confirmation
                - Never hardcode secrets, API keys, or passwords in source code
                - Never place business logic in controllers or repository implementations
                - Never allow the domain layer to import from infrastructure or API layers

---

## Phase Reference

각 Phase의 상세 구현 계획은 `docs/phases/` 디렉토리에 분리되어 있다. 작업 시작 전 해당 Phase 파일을 반드시 참조할 것.

```
docs/phases/
├── phase-01-foundation.md    # 공통 도메인, MongoDB/Redis 설정, 예외 핸들러
├── phase-02-property.md      # Property 도메인
├── phase-03-room.md          # Room, RoomType 도메인
├── phase-04-inventory.md     # RoomInventory 도메인 (동시성 제어)
├── phase-05-guest.md         # Guest 도메인 (등급, 방문 이력)
├── phase-06-rate.md          # RatePlan, RateResolver 도메인
├── phase-07-channel.md       # Channel (FineStay/OTA), Outbox 동기화, 가상 채널 어댑터
├── phase-08-reservation.md   # Reservation 도메인 (핵심, 8+ sub-steps)
├── phase-09-settlement.md    # Settlement (MongoDB aggregation)
└── phase-10-auth.md          # Auth (JWT, Redis, 멀티 숙소 권한)
```
