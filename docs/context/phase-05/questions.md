# Phase 5 학습 질문 모음

## Q1. GuestTier는 모든 숙소에 적용되는가

GuestTier는 FineStay(채널)와 무관한 PMS 내부 고객 등급이다. 모든 숙소가 등급 제도를 운영하지 않을 수 있으므로, `tier: GuestTier?`로 nullable 처리하여 등급 미사용 숙소를 표현할 수 있다. 현재 Phase 범위에서는 Property 도메인 수정 없이 nullable로 대응한다.

---

## Q2. VisitSummary를 별도 VO로 분리한 장단점

### 장점
- **응집도**: 방문 이력 4개 필드가 항상 함께 변경되므로 캡슐화에 적합
- **테스트 용이**: Guest 없이 VisitSummary.recordVisit()을 독립 검증 가능
- **재사용**: 다른 컨텍스트(통계 API 응답 등)에서 독립 전달 가능

### 단점
- **간접 참조**: `guest.visitSummary.totalVisits` — 필드 4개뿐인데 한 단계 더 타고 들어감
- **MongoDB 저장 복잡도**: VO → Document 변환 시 평탄화 또는 서브도큐먼트 선택 필요
- **과도한 추상화 가능성**: 소비자가 Guest.recordVisit() 하나뿐이라면 YAGNI 위반 소지

---

## Q3. @PostConstruct로 인덱스를 생성하는 이유

Spring Data MongoDB의 `auto-index-creation` 기본값은 `false`이다. 이 설정이 꺼져 있으면 `@CompoundIndex` 어노테이션이 있어도 실제 인덱스가 생성되지 않는다. `@PostConstruct`는 설정과 무관하게 항상 인덱스를 생성하므로 환경에 따른 누락을 방지한다. MongoDB의 `createIndex`는 멱등 연산이므로 중복 생성 문제는 없다. `@CompoundIndex`는 문서화 목적으로 남겨둔다.

---

## Q4. 전화번호 인덱스의 적합성

### 기술적 효율성 — 문제없음
전화번호는 카디널리티(고유값 수)가 높아 인덱스 선택성(selectivity)이 좋다. B-Tree 탐색 범위가 좁아 조회 성능이 우수하다.

### 비즈니스 적합성 — 주의 필요
- **번호 변경**: 고객이 번호를 바꾸면 동일인이 새 고객으로 등록됨
- **형식 불일치**: `010-1234-5678`, `01012345678`, `+821012345678` — 같은 번호인데 다른 값
- **공유 번호**: 가족/비서 대리 예약 시 같은 번호로 다른 사람이 등록될 수 있음

### 개선 방향
- 전화번호 정규화 로직을 `Guest.create()` 시점에 적용 (하이픈 제거, 국가 코드 통일)
- 향후 이메일·CI(연계정보) 등 복합 식별 기준으로 확장 고려
