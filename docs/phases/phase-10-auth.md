# Phase 10: Auth (인증)

JWT 인증, 멀티 숙소 접근 권한, Redis Refresh Token 관리.

---

## 기능적 요구사항

- **회원가입·로그인**: 이메일/비밀번호 기반 회원가입 및 로그인을 지원해야 한다
- **토큰 기반 인증**: Access Token(15분)과 Refresh Token(7일)으로 무상태 인증을 구현해야 한다
- **멀티 숙소 접근 제어**: 사용자별로 접근 가능한 숙소 목록과 역할(OWNER/MANAGER/FRONT_DESK)을 관리해야 한다
- **역할별 권한**: ADMIN은 전체 시스템, OWNER는 소유 숙소, MANAGER/FRONT_DESK는 배정된 숙소에만 접근 가능해야 한다
- **로그아웃 즉시 무효화**: 로그아웃 시 Access Token을 즉시 무효화하고 Refresh Token을 삭제해야 한다

## 기술적 도전 과제

| 과제 | 설명 | 해결 전략 |
|------|------|----------|
| Stateless 토큰 즉시 무효화 | JWT는 서버에 상태가 없어 발급 후 무효화가 어려움 | Redis 블랙리스트 — 로그아웃 시 Access Token의 jti를 남은 TTL만큼 Redis에 저장 |
| Refresh Token 탈취 방지 | Refresh Token이 탈취되면 7일간 악용 가능 | Redis에 저장하고 로그아웃/갱신 시 즉시 삭제, Refresh Rotation 적용 |
| 기존 API 전체 인가 적용 | Phase 1~9의 모든 엔드포인트에 인증·인가를 소급 적용해야 함 | `JwtAuthenticationFilter`를 Security 필터 체인에 등록, 각 Controller에 `hasAccessTo(propertyId)` 검사 추가 |
| Secret Key 관리 | JWT 서명 키가 256비트 이상이어야 하며 코드에 노출되면 안 됨 | `application-secret.yml` (gitignored) 또는 환경 변수에서 로드 |

---

## Sub-steps

### Phase 10-1: User 도메인 모델 + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/auth/domain/model/
├── User.kt
├── UserRole.kt            # enum: ADMIN, OWNER, MANAGER, FRONT_DESK
├── UserStatus.kt          # enum: ACTIVE, INACTIVE
└── PropertyAccess.kt      # VO: propertyId + PropertyRole

src/test/kotlin/com/stayops/auth/domain/model/
└── UserTest.kt
```

**User 도메인 모델:**
```kotlin
data class User(
    val id: String,
    val email: String,
    val passwordHash: String,
    val name: String,
    val role: UserRole,
    val propertyAccess: List<PropertyAccess> = emptyList(),
    val status: UserStatus = UserStatus.ACTIVE,
    val lastLoginAt: Instant? = null,
    val version: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class PropertyAccess(
    val propertyId: String,
    val role: PropertyRole
)
```

**역할:**
- ADMIN: 시스템 전체 관리, 모든 숙소 접근
- OWNER: 소유 숙소 전체 권한
- MANAGER: 배정된 숙소 관리
- FRONT_DESK: 배정된 숙소 운영 (예약/체크인/체크아웃)

**비즈니스 규칙:**
- `hasAccessTo(propertyId)`: ADMIN은 항상 true, 나머지는 propertyAccess 확인
- `grantAccess(propertyId, role)`: propertyAccess 추가
- `revokeAccess(propertyId)`: propertyAccess 제거

**TDD 순서:**
1. RED: User 생성/접근 권한 테스트
2. GREEN: User 구현
3. RED: hasAccessTo 테스트 (ADMIN, 일반 사용자)
4. GREEN: 접근 검증 구현
5. REFACTOR

---

### Phase 10-2: JwtTokenProvider + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/auth/infrastructure/
├── JwtTokenProvider.kt
└── JwtProperties.kt

src/test/kotlin/com/stayops/auth/infrastructure/
└── JwtTokenProviderTest.kt
```

**JwtTokenProvider:**
```kotlin
class JwtTokenProvider(private val jwtProperties: JwtProperties) {
    fun generateAccessToken(user: User): String
    fun generateRefreshToken(user: User): String
    fun validateToken(token: String): Boolean
    fun extractUserId(token: String): String
    fun extractJti(token: String): String
}
```

**JWT 설정:**
- Access Token: 15분, HS256
- Refresh Token: 7일, UUID jti
- Secret Key: 256비트 이상

---

### Phase 10-3: Redis Refresh Token / Blacklist + 통합 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/auth/infrastructure/
├── RedisRefreshTokenStore.kt
└── RedisTokenBlacklist.kt

src/test/kotlin/com/stayops/auth/infrastructure/
├── RedisRefreshTokenStoreTest.kt
└── RedisTokenBlacklistTest.kt
```

**Redis 키 패턴:**
- Refresh Token: `refresh_token:{tokenId}` → userId, TTL 7일
- Blacklist: `blacklist:{jti}` → "blacklisted", TTL 15분

---

### Phase 10-4: AuthService + JwtAuthenticationFilter + 통합 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/auth/application/service/
└── AuthService.kt

src/main/kotlin/com/stayops/auth/infrastructure/
└── JwtAuthenticationFilter.kt

src/main/kotlin/com/stayops/auth/domain/repository/
└── UserRepository.kt

src/main/kotlin/com/stayops/auth/infrastructure/persistence/
├── UserDocument.kt
└── MongoUserRepository.kt

src/main/kotlin/com/stayops/auth/api/
├── AuthController.kt
└── dto/
    ├── SignupRequest.kt
    ├── LoginRequest.kt
    ├── RefreshRequest.kt
    ├── AuthResponse.kt
    └── TokenResponse.kt

src/test/kotlin/com/stayops/auth/application/service/
└── AuthServiceTest.kt

src/test/kotlin/com/stayops/auth/api/
└── AuthControllerTest.kt
```

**API 엔드포인트:**
```
POST   /api/v1/auth/signup    — 회원가입
POST   /api/v1/auth/login     — 로그인 (access + refresh token 반환)
POST   /api/v1/auth/refresh   — 토큰 갱신 (refresh → new access + new refresh)
POST   /api/v1/auth/logout    — 로그아웃 (access token blacklist + refresh token 삭제)
```

**JwtAuthenticationFilter:**
- Authorization: Bearer {accessToken} 헤더에서 토큰 추출
- 토큰 검증 + 블랙리스트 확인
- SecurityContext에 인증 정보 설정

---

### Phase 10-5: 기존 엔드포인트에 권한 체크 추가

**수정할 파일:**
```
src/main/kotlin/com/stayops/shared/config/SecurityConfig.kt
각 Controller 파일들 (propertyId 접근 검증)
```

**권한 규칙:**
- Auth 엔드포인트 (`/api/v1/auth/**`): permit-all
- 나머지 모든 엔드포인트: 인증 필수
- propertyId 포함 엔드포인트: `user.hasAccessTo(propertyId)` 검증

---

## 의존성 추가 (build.gradle.kts)

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
testImplementation("org.springframework.security:spring-security-test")
```

---

## 검증 기준

- [ ] User 도메인 단위 테스트 통과
- [ ] JWT 발급/검증 단위 테스트 통과
- [ ] Redis Refresh Token 통합 테스트 통과
- [ ] 로그인/로그아웃/토큰 갱신 E2E 테스트 통과
- [ ] propertyId 접근 권한 검증 테스트 통과
- [ ] `./gradlew test` 전체 통과
