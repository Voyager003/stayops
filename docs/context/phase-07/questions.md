# Phase 7 학습 질문

## Q1: ChannelConfig의 null 안티패턴

**Q:** ChannelConfig에서 webhookSecret: String? 로 null을 노출하는 것은 안티패턴 아닌가요?

**A:** 맞다. null이 "DIRECT 채널이라 필요 없음"이라는 비즈니스 의미를 암묵적으로 담고 있다.
해결: ChannelConfig를 sealed interface ChannelPolicy로 리팩토링하여 DirectPolicy(webhookSecret 필드 없음)와 OtaPolicy(webhookSecret: String, non-null)로 분리.

## Q2: sealed interface란?

**Q:** sealed interface가 무엇이고 언제 사용하는 것인가요?

**A:** 같은 타입이지만 변형(variant)이 제한적일 때 사용한다. sealed를 붙이면 같은 파일(또는 모듈) 안에서만 구현체를 정의할 수 있다.
- 컴파일러가 모든 하위 타입을 알고 있으므로 when에서 else 없이 완전한 분기 가능
- enum과의 차이: enum은 각 값이 동일한 구조, sealed는 각 하위 타입이 서로 다른 필드를 가질 수 있음
- 사용 시점: 변형이 고정적이고, 각 변형의 구조가 다르고, when 분기에서 빠뜨리면 안 될 때

## Q3: sealed의 "컴파일러가 모든 하위 타입을 안다"는 의미

**Q:** 컴파일러가 모든 하위 타입을 알고 있다는 것이 무슨 의미인가요? 제네릭과 타입소거와 관련이 있나요?

**A:** 제네릭/타입 소거와는 관계없다. 훨씬 단순한 개념이다.
- sealed를 붙이면 같은 파일/모듈 안에서만 구현체를 만들 수 있다는 제약이 생김
- 컴파일러가 "구현체는 딱 N개뿐"이라고 확정할 수 있으므로 when에서 else 불필요
- 일반 interface는 누구든 어디서든 구현할 수 있으므로 when에서 else 필수
- 타입 소거는 런타임에 제네릭 정보가 사라지는 JVM 제한으로, sealed와는 전혀 다른 영역

## Q4: Config라는 명명과 null 안티패턴

**Q:** 1. config라는 단어가 VO객체라는 것에 혼동을 줍니다. 2. direct()에서 null을 반환하는 것은 안티패턴입니다.

**A:** 두 가지 모두 타당한 지적이다:
1. Config → Policy로 이름 변경 (수수료율/webhook은 채널의 "정책"에 가까움)
2. sealed interface ChannelPolicy로 리팩토링: DirectPolicy(webhookSecret 필드 없음) + OtaPolicy(webhookSecret: String non-null)
