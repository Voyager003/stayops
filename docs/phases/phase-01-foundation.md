# Phase 1: Foundation

공통 도메인 모델, 인프라 설정, 글로벌 예외 핸들러, Security 스켈레톤 구축.

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
