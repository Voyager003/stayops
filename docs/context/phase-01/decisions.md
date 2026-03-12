# Phase 1 설계 결정 및 Q&A

## 1. AggregateRoot 추상 클래스 제거 (YAGNI)

- **질문**: AggregateRoot를 추상화한 이유가 무엇인가? 판단 근거는?
- **결론**: 제거
- **근거**: 공통 행위 없이 프로퍼티 4개(id, version, createdAt, updatedAt)의 계약만 존재. 도메인 이벤트 수집 등 공통 행위가 실제로 필요한 시점에 도입해야 함. 현재는 YAGNI 위반.
- **영향**: 전체 Phase 문서(2~10)에서 `: AggregateRoot()` 상속 및 `override val` 제거 반영 완료

## 2. DomainEvent 추상 클래스 제거 (YAGNI)

- **질문**: DomainEvent도 같은 이유로 불필요한가?
- **결론**: 제거
- **근거**: AggregateRoot와 동일. 프로퍼티 3개(eventId, occurredAt, aggregateId)의 계약만 존재. Phase 7 Outbox에서 사용 가능하지만, 그때 구체적 요구사항에 맞춰 설계하는 것이 적절.

## 3. @ConsistentCopyVisibility

- **질문**: 이 어노테이션의 의미는?
- **설명**: data class의 `copy()` 가시성을 생성자와 일치시킴. `private constructor`이면 `copy()`도 `private`이 되어, 반드시 팩토리 메서드(Money.of(), Money.won())를 거치게 강제. Kotlin 2.5부터 이 어노테이션 없이도 에러가 됨.

## 4. Money / DateRange 공통 사용처

- **질문**: 각각 어느 도메인에서 공통으로 쓰이는가?
- **Money**: Room(basePrice), Rate(price), Guest(totalSpend), Reservation(pricing) — 4개 BC
- **DateRange**: Rate(dateRange), Reservation(dateRange) — 2개 BC + Inventory 간접 사용

## 5. Property 하위 테넌트 격리 이유

- **질문**: 왜 Property 단위로 격리하는가?
- **근거**: 각 Property(숙소)가 물리적으로 독립된 사업체. Owner가 아닌 Property가 가장 자연스러운 데이터 경계. 같은 Owner의 호텔 A와 펜션 B도 운영·정산·채널이 완전히 별개.

## 6. CSRF 비활성화 이유

- **질문**: 왜 CSRF를 비활성화했는가?
- **근거**: REST API + JWT Bearer Token 인증. 쿠키 기반 세션을 사용하지 않으므로 CSRF 공격이 성립하지 않음. Spring Security 공식 문서에서도 non-browser client만 사용하는 API는 CSRF 비활성화 권장.

## 교훈

- **조기 추상화 경계**: 공통 행위가 없는 추상 클래스/인터페이스는 도입하지 않는다. 실제 사용처와 공통 행위가 확인된 후 도입.
- **Phase sub-step 검토 규칙**: 인프라 설정이라도 예외 없이 sub-step 단위로 멈추고 사용자 검토 필수.
- **완료 보고 형식**: 작업 내역 + 판단 근거를 반드시 포함.
