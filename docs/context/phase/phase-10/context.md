# Phase 10 의사결정 컨텍스트

## JWT → 세션 전환

- **결정**: JWT 인증 대신 세션 기반 인증 채택
- **이유**:
  - StayOps는 모놀리식 단일 서버 + 브라우저 SPA 클라이언트
  - JWT의 장점(MSA 간 무상태 인증 전파, 모바일 앱 지원)이 필요 없는 구조
  - JWT + Redis 블랙리스트 = 결국 stateful인데 세션보다 구현만 복잡
  - 세션은 로그아웃 즉시 무효화가 자연스러움 (세션 삭제 한 줄)
- **세션 스토어**:
  - 초기 결정: 서버 메모리 (Tomcat 기본값)
  - 2026-03-30 전환: Redis 기반 Spring Session (`spring-boot-starter-session-data-redis`)

## UUID ID 문제 인식

- **현상**: 전체 모듈이 `UUID.randomUUID().toString()`으로 ID 생성
- **문제**:
  - 저장 전 ID 확정이 필요한 기능이 없으므로 UUID가 필수인 상황이 아님
  - MongoDB ObjectId 대비 크기 3배 (36B vs 12B), 랜덤 삽입으로 B-Tree 인덱스 비효율
  - ObjectId는 시간순 정렬이라 인덱스 효율적이고 MongoDB가 기본 제공
- **결정**: Phase 10에서는 기존 패턴 유지. 완료 후 별도 리팩토링으로 UUID → ObjectId 자동 생성 전환 검토

## FRONT_DESK 역할 제거

- **결정**: MemberRole에서 FRONT_DESK 제거, ADMIN / OWNER / MANAGER 3개로 운영
- **이유**: MANAGER와 FRONT_DESK의 권한 차이가 현재 구현에서 없음. YAGNI 원칙 적용

## 중복 이메일 예외 타입

- **결정**: `BusinessException`(400) 대신 `ConflictException`(409) 사용
- **이유**: 이메일 중복은 리소스 충돌이므로 HTTP 409 Conflict가 의미적으로 적합

## 코드 리뷰 반영

- **C1 해결**: PropertyAccessChecker — 인증 없으면 silent return → ForbiddenException 발생으로 변경
- **C2 해결**: 로그인 실패 시 이메일 없음/비밀번호 틀림을 동일한 응답으로 통합 (이메일 열거 공격 방지)

## 코드 리뷰 미해결 이슈 (추후 수정)

### C3: Member Serializable 미구현 [해결됨]
- **기존 현상**: Member가 세션 principal로 저장되지만 `java.io.Serializable` 미구현
- **문제**: Redis 세션 전환 시 principal 직렬화가 필요하므로 `NotSerializableException` 위험
- **해결**: `Member`, `PropertyAccess`에 `Serializable` 적용
- **검증**: Redis-backed 세션 통합 테스트에서 로그인 후 `spring:session:sessions:{id}` 키 생성 확인

### H1: 세션 고정 공격 미방어
- 로그인 시 기존 세션 무효화 없이 SecurityContext만 설정
- 대응: 로그인 시 `httpRequest.getSession(false)?.invalidate()` 후 새 세션 생성

### H5: passwordHash가 data class toString()에 노출
- Member가 로그에 출력되면 해시 노출 가능
- 대응: `toString()` 오버라이드하여 passwordHash 제외

## Redis 세션 저장소 전환 메모

- **자동설정 기준**: Spring Boot 4 서블릿 앱은 `spring-boot-starter-session-data-redis`를 사용해야 Redis 세션 저장소가 자동설정된다
- **설정**: `spring.session.redis.repository-type=default`, `spring.session.timeout=30m`
- **테스트 방식**:
  - `@SpringBootTest(RANDOM_PORT)` + Testcontainers MongoDB/Redis
  - `/api/v1/auth/login` 호출 후 `Set-Cookie`의 `SESSION` 쿠키 확인
  - 쿠키 값은 Base64 인코딩된 세션 ID이므로 디코딩 후 Redis 키 `spring:session:sessions:{decodedId}` 존재 여부 확인
  - 같은 쿠키로 보호 API 접근 가능, 로그아웃 후 동일 쿠키 재사용 불가 확인
