# Phase 1: Foundation

공통 도메인 모델, 인프라 설정, 글로벌 예외 핸들러, Security 스켈레톤 구축.

---

## 기능적 요구사항

- **공통 Value Object**: 모든 BC에서 금액(Money)과 숙박 기간(DateRange)을 일관되게 표현해야 한다
- **인프라 기반**: 예약·재고·채널 등 핵심 기능이 MongoDB 트랜잭션과 Redis 캐시 위에서 동작해야 한다
- **통일된 에러 응답**: 클라이언트가 에러 코드 기반으로 분기 처리할 수 있는 표준 에러 포맷이 필요하다
- **보안 스켈레톤**: Phase 10에서 JWT 인증을 적용하기 전까지 API 개발이 가능해야 한다

## 기술적 도전 과제

| 과제 | 설명 | 해결 전략 |
|------|------|----------|
| MongoDB 트랜잭션 | 단일 노드에서는 트랜잭션 불가 — Replica Set 필수 | Docker Compose로 단일 노드 Replica Set 구성 |
| Value Object 불변성 | `data class`의 `copy()`가 팩토리 메서드를 우회할 수 있음 | `@ConsistentCopyVisibility` + `private constructor` |
| 에러 응답 일관성 | 예외 유형별 HTTP 상태 코드 매핑 필요 | `@RestControllerAdvice` + sealed class 예외 계층 |

---

## Sub-steps

### Phase 1-1: 공통 도메인 모델 + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/shared/domain/
├── Money.kt
└── DateRange.kt

src/test/kotlin/com/stayops/shared/domain/
├── MoneyTest.kt
└── DateRangeTest.kt
```

**Money:**
- `amount: BigDecimal`, `currency: String = "KRW"`
- 불변식: `amount >= 0`
- 연산: `add()`, `subtract()`, `multiply()`, `percentage()`
- 팩토리: `Money.of(amount)`, `Money.won(amount)`, `Money.ZERO`

**DateRange:**
- `checkIn: LocalDate`, `checkOut: LocalDate`
- 불변식: `checkOut > checkIn`
- 계산: `nights()`, `allDates()`, `overlaps(other)`
- 팩토리: `DateRange.of(checkIn, checkOut)`

**TDD 순서:**
1. RED: Money 불변식 위반 테스트 (음수 금액 → 예외)
2. GREEN: Money 구현
3. RED: DateRange 불변식 위반 테스트 (checkOut <= checkIn → 예외)
4. GREEN: DateRange 구현
5. REFACTOR

---

### Phase 1-2: MongoDB 설정

**생성/수정할 파일:**
```
src/main/kotlin/com/stayops/shared/config/MongoConfig.kt
src/main/resources/application.yml
docker-compose.yml (MongoDB replica set)
```

**설정 내용:**
- MongoDB replica set (단일 노드) — 트랜잭션 지원
- `MongoTransactionManager` 빈 등록
- `application.yml`에 MongoDB 연결 설정

**Docker Compose:**
```yaml
services:
  mongodb:
    image: mongo:8
    ports:
      - "27017:27017"
    command: ["--replSet", "rs0"]

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

---

### Phase 1-3: Redis 설정

**생성할 파일:**
```
src/main/kotlin/com/stayops/shared/config/RedisConfig.kt
```

**설정 내용:**
- `RedisTemplate<String, String>` 빈 (JSON 직렬화)
- `application.yml`에 Redis 연결 설정

---

### Phase 1-4: 글로벌 예외 핸들러

**생성할 파일:**
```
src/main/kotlin/com/stayops/shared/exception/
├── BusinessException.kt        # 400 Bad Request
├── NotFoundException.kt         # 404 Not Found
├── ConflictException.kt         # 409 Conflict (낙관적 락 충돌)
└── GlobalExceptionHandler.kt    # @RestControllerAdvice
```

**에러 응답 포맷:**
```json
{
  "code": "INVENTORY_NOT_AVAILABLE",
  "message": "요청한 날짜의 객실 재고가 부족합니다.",
  "timestamp": "2026-03-11T10:00:00Z"
}
```

---

### Phase 1-5: Security 설정 스켈레톤 (permit-all)

**생성할 파일:**
```
src/main/kotlin/com/stayops/shared/config/SecurityConfig.kt
```

**설정 내용:**
- 모든 엔드포인트 permit-all (Phase 9에서 실제 인증 적용)
- CSRF 비활성화
- CORS 설정 (localhost:5173 허용)

---

## 의존성 추가 (build.gradle.kts)

```kotlin
// Security
implementation("org.springframework.boot:spring-boot-starter-security")

// Testing
testImplementation("io.mockk:mockk:1.14.2")
testImplementation("org.springframework.security:spring-security-test")
```

---

## 검증 기준

- [ ] `./gradlew test` 통과
- [ ] Money, DateRange 단위 테스트 통과
- [ ] Docker Compose로 MongoDB + Redis 기동 확인
- [ ] 애플리케이션 정상 기동 (`./gradlew bootRun`)
