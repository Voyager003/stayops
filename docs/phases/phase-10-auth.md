# Phase 10: Auth (인증)

세션 기반 인증, 멀티 숙소 접근 권한 관리.

---

## 설계 결정: JWT → 세션 전환

| 기준 | JWT | 세션 | StayOps 판단 |
|------|-----|------|-------------|
| 아키텍처 | MSA/서버리스에 적합 | 모놀리스에 적합 | 모놀리식 단일 서버 → **세션** |
| 클라이언트 | 모바일+웹 다중 | 브라우저 SPA | 브라우저 단일 → **세션** |
| 즉시 로그아웃 | Redis 블랙리스트 필요 | 세션 삭제 한 줄 | **세션** |
| 복잡도 | Access/Refresh/Blacklist | 쿠키 자동 관리 | **세션** |

JWT + Redis 블랙리스트 = 결국 stateful이지만 세션보다 구현이 복잡. 세션 방식이 동일한 효과를 더 단순하게 달성.

---

## 기능적 요구사항

- **회원가입·로그인**: 이메일/비밀번호 기반 회원가입 및 로그인을 지원해야 한다
- **세션 기반 인증**: 로그인 시 서버가 세션을 생성하고 HttpOnly 쿠키로 세션 ID를 전달해야 한다
- **멀티 숙소 접근 제어**: 멤버별로 접근 가능한 숙소 목록과 역할(OWNER/MANAGER)을 관리해야 한다
- **역할별 권한**: ADMIN은 전체 시스템, OWNER는 소유 숙소, MANAGER는 배정된 숙소에만 접근 가능해야 한다
- **로그아웃 즉시 무효화**: 로그아웃 시 세션을 삭제하여 즉시 무효화해야 한다

## 기술적 도전 과제

| 과제 | 설명 | 해결 전략 |
|------|------|----------|
| 기존 API 전체 인가 적용 | Phase 1~9의 모든 엔드포인트에 인증·인가를 소급 적용해야 함 | SecurityConfig에서 세션 기반 인증 설정, 각 Api에 `hasAccessTo(propertyId)` 검사 추가 |
| Webhook API 인증 예외 | ChannelWebhookApi는 OTA에서 호출하므로 세션 인증 불가 | 서명 검증으로 인증 대체, SecurityConfig에서 permitAll |
| 패스워드 보안 | 비밀번호를 안전하게 저장해야 함 | BCrypt 해싱 (Spring Security PasswordEncoder) |

---

## 인증 흐름

```
1. 로그인
   POST /api/v1/auth/login {email, password}
   → 서버: 비밀번호 검증 → 세션 생성 → JSESSIONID 쿠키 응답 (HttpOnly)

2. 이후 요청
   브라우저가 자동으로 JSESSIONID 쿠키 전송
   → Spring Security: 세션 조회 → SecurityContext 복원 → 인증 완료

3. 로그아웃
   POST /api/v1/auth/logout
   → 세션 무효화 → 즉시 효과
```

---

## Sub-steps

### Phase 10-1: Member 도메인 모델 + 단위 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/auth/domain/model/
├── Member.kt
├── MemberRole.kt          # enum: ADMIN, OWNER, MANAGER
├── MemberStatus.kt        # enum: ACTIVE, INACTIVE
└── PropertyAccess.kt      # VO: propertyId + PropertyRole

src/test/kotlin/com/stayops/auth/domain/model/
└── MemberTest.kt
```

**Member 도메인 모델:**
```kotlin
data class Member(
    val id: String,
    val email: String,
    val passwordHash: String,
    val name: String,
    val role: MemberRole,
    val propertyAccess: List<PropertyAccess> = emptyList(),
    val status: MemberStatus = MemberStatus.ACTIVE,
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
- MANAGER: 배정된 숙소 관리 및 운영 (예약/체크인/체크아웃)

**비즈니스 규칙:**
- `hasAccessTo(propertyId)`: ADMIN은 항상 true, 나머지는 propertyAccess 확인
- `grantAccess(propertyId, role)`: propertyAccess 추가
- `revokeAccess(propertyId)`: propertyAccess 제거

**TDD 순서:**
1. RED: Member 생성/접근 권한 테스트
2. GREEN: Member 구현
3. RED: hasAccessTo 테스트 (ADMIN, 일반 멤버)
4. GREEN: 접근 검증 구현
5. REFACTOR

---

### Phase 10-2: Member 저장소 + AuthService + SecurityConfig

**생성할 파일:**
```
src/main/kotlin/com/stayops/auth/domain/repository/
└── MemberRepository.kt

src/main/kotlin/com/stayops/auth/infrastructure/persistence/
├── MemberDocument.kt
└── MongoMemberRepository.kt

src/main/kotlin/com/stayops/auth/application/service/
└── AuthService.kt

src/test/kotlin/com/stayops/auth/infrastructure/persistence/
└── MongoMemberRepositoryTest.kt

src/test/kotlin/com/stayops/auth/application/service/
└── AuthServiceTest.kt
```

**AuthService:**
```kotlin
@Service
class AuthService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun signup(email: String, password: String, name: String): Member
    fun login(email: String, password: String): Member   // 검증 후 Member 반환
    fun logout(session: HttpSession)                      // 세션 무효화
}
```

**SecurityConfig 변경:**
- BCryptPasswordEncoder 빈 등록
- 세션 관리 활성화
- `/api/v1/auth/**` → permitAll
- 나머지 → authenticated
- ChannelWebhookApi 경로 → permitAll (서명 검증으로 인증 대체)

---

### Phase 10-3: Auth API + 테스트

**생성할 파일:**
```
src/main/kotlin/com/stayops/auth/api/
├── AuthApi.kt
└── dto/
    ├── SignupRequest.kt
    ├── LoginRequest.kt
    └── AuthResponse.kt

src/test/kotlin/com/stayops/auth/api/
└── AuthApiTest.kt
```

**API 엔드포인트:**
```
POST   /api/v1/auth/signup    — 회원가입
POST   /api/v1/auth/login     — 로그인 (세션 생성, JSESSIONID 쿠키 응답)
POST   /api/v1/auth/logout    — 로그아웃 (세션 무효화)
```

---

### Phase 10-4: 기존 엔드포인트에 권한 체크 추가

**수정할 파일:**
```
src/main/kotlin/com/stayops/shared/config/SecurityConfig.kt
각 Api 파일들 (propertyId 접근 검증)
```

**권한 규칙:**
- Auth 엔드포인트 (`/api/v1/auth/**`): permitAll
- Webhook 엔드포인트 (`/api/v1/properties/*/channels/webhook`): permitAll
- 나머지 모든 엔드포인트: 인증 필수
- propertyId 포함 엔드포인트: `member.hasAccessTo(propertyId)` 검증

**대상 컨트롤러 (11개):**
PropertyApi, RoomApi, RoomTypeApi, RoomInventoryApi, GuestApi, RatePlanApi, ReservationApi, ChannelApi, SyncDashboardApi, ChannelWebhookApi, SettlementApi

---

## 의존성 변경 (build.gradle.kts)

```kotlin
# 이미 존재 — 추가 불필요
implementation("org.springframework.boot:spring-boot-starter-security")
testImplementation("org.springframework.security:spring-security-test")

# JWT 관련 — 추가하지 않음 (세션 방식이므로 불필요)
# io.jsonwebtoken:jjwt-api
# io.jsonwebtoken:jjwt-impl
# io.jsonwebtoken:jjwt-jackson
```

---

## 검증 기준

- [ ] Member 도메인 단위 테스트 통과
- [ ] Member 저장소 통합 테스트 통과
- [ ] AuthService 단위 테스트 통과
- [ ] Auth API 단위 테스트 통과
- [ ] 로그인/로그아웃 동작 확인
- [ ] propertyId 접근 권한 검증 테스트 통과
- [ ] `./gradlew test` 전체 통과
