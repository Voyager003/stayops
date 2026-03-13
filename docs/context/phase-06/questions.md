# Phase 6 학습 질문 모음

## Q1. RateResolver에 @Service를 사용하지 않는 이유

`RateResolver`는 `domain/service/`에 위치한 순수 도메인 서비스다. Repository나 외부 시스템에 의존하지 않고, 인자로 받은 `List<RatePlan>`과 `Money`만으로 요금을 계산한다. CLAUDE.md의 레이어 규칙 — "domain/은 pure Java only, Spring 어노테이션 금지"에 따라 `@Service`를 붙이지 않는다. Application 레이어에서 직접 인스턴스화(`private val rateResolver = RateResolver()`)하여 사용한다.

---

## Q2. persistence와 repository의 차이점

레이어가 다르다.

- `domain/repository/` — **인터페이스** (도메인 레이어). "무엇이 필요한가"를 선언. DB 종류를 모른다.
- `infrastructure/persistence/` — **구현체** (인프라 레이어). "어떻게 구현하는가"를 담당. Document 변환, Spring Data, 인덱스 등 DB 종속 코드.

DIP(의존 역전 원칙)에 따라 분리한다. Application 서비스는 인터페이스에만 의존하고 구현체를 모른다. DB를 교체해도 인터페이스 계약을 지키는 새 구현체만 만들면 된다.

---

## Q3. dateRange null 처리 패턴의 문제점

### 현재 코드
```kotlin
val dateRange = if (request.dateRangeStart != null && request.dateRangeEnd != null) {
    DateRange.of(request.dateRangeStart, request.dateRangeEnd)
} else null
```

### 문제 1: 한쪽만 입력 시 조용히 무시됨
`dateRangeStart`만 보내고 `dateRangeEnd`를 빠뜨리면 에러 없이 `null`(상시 적용)이 된다. 사용자의 입력 실수를 감지할 수 없다.

### 문제 2: null에 비즈니스 의미 부여
`null`이 "값이 없다"가 아닌 "상시 적용"이라는 비즈니스 규칙을 암묵적으로 담고 있다.

### 개선 방향
Request DTO에 검증 추가:
```kotlin
init {
    require(
        (dateRangeStart == null && dateRangeEnd == null) ||
        (dateRangeStart != null && dateRangeEnd != null)
    ) { "dateRangeStart와 dateRangeEnd는 둘 다 있거나 둘 다 없어야 합니다." }
}
```
