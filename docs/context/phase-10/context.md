# Phase 10 의사결정 컨텍스트

## JWT → 세션 전환

- **결정**: JWT 인증 대신 세션 기반 인증 채택
- **이유**:
  - StayOps는 모놀리식 단일 서버 + 브라우저 SPA 클라이언트
  - JWT의 장점(MSA 간 무상태 인증 전파, 모바일 앱 지원)이 필요 없는 구조
  - JWT + Redis 블랙리스트 = 결국 stateful인데 세션보다 구현만 복잡
  - 세션은 로그아웃 즉시 무효화가 자연스러움 (세션 삭제 한 줄)
- **세션 스토어**: 서버 메모리 (Tomcat 기본값). 필요 시 `spring-session-data-redis`로 전환 가능

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
