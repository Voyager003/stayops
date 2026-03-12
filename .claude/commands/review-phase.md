# Phase 완료 리뷰

Phase 번호: $ARGUMENTS

## 지시사항

`$ARGUMENTS`로 전달된 Phase 번호에 대해 완료 리뷰를 수행하라. Phase 문서(`docs/phases/phase-{N}-*.md`)를 읽고, 아래 5가지 항목을 검증하라.

---

### 1. 파일 존재 확인

Phase 문서의 모든 sub-step에서 "생성할 파일" 목록을 추출하고, 실제 파일 시스템에 존재하는지 확인하라.

```
### 파일 존재 확인

| 파일 경로 | 상태 |
|----------|------|
| src/main/kotlin/.../Money.kt | EXISTS / MISSING |
| src/test/kotlin/.../MoneyTest.kt | EXISTS / MISSING |
```

### 2. 도메인 모델 품질

해당 Phase에서 생성된 도메인 모델 파일(`**/domain/model/**/*.kt`)을 검사:

- **Aggregate 식별**: Aggregate Root 역할의 클래스가 `id`, `version`, `createdAt`, `updatedAt` 필드를 갖는지
- **팩토리 메서드**: `companion object`에 `of()`, `create()`, `from()` 등 팩토리 메서드가 있는지
- **불변식 검증**: `init` 블록 또는 팩토리 메서드에서 `require()`/`check()`로 불변식을 검증하는지
- **불변성**: `val` 프로퍼티만 사용하고, 상태 변경 시 `copy()` 또는 새 인스턴스를 반환하는지

### 3. Repository 계약

- `domain/repository/` 인터페이스와 `infrastructure/persistence/` 구현체가 1:1 매칭되는지
- 구현체가 인터페이스의 모든 메서드를 구현하는지

### 4. 테스트 커버리지

Phase 문서의 "TDD 순서"와 대비하여:
- 각 TDD 단계에 대응하는 테스트가 존재하는지
- 테스트가 Given/When/Then 구조(BehaviorSpec)를 따르는지
- 해피 패스 + 예외 케이스 모두 테스트되는지
- `./gradlew test`가 통과하는지 (실제 실행)

### 5. 검증 기준 체크리스트

Phase 문서 하단의 "검증 기준" 섹션의 각 항목을 하나씩 확인하라.

```
### 검증 기준

| # | 기준 | 결과 |
|---|------|------|
| 1 | ./gradlew test 통과 | PASS / FAIL |
| 2 | Money, DateRange 단위 테스트 통과 | PASS / FAIL |
| ... | ... | ... |
```

---

## 최종 판정

모든 항목을 종합하여 최종 판정을 내려라:

- **PASS**: 모든 검증 기준 충족 → "Phase {N} 완료. Phase {N+1}을 시작할 수 있습니다."
- **PARTIAL**: 일부 미충족 → 미충족 항목 목록과 수정 가이드 제시
- **FAIL**: 핵심 항목 미충족 → 반드시 수정해야 할 항목 우선순위 제시

## 주의사항

- Phase 번호가 한 자릿수면 앞에 0을 붙여라 (e.g., `1` → `01`)
- 문서가 존재하지 않으면 에러 메시지를 출력하라
- 이 커맨드는 리뷰 전용이다. 코드를 수정하지 마라.
- 검증 기준 중 `./gradlew test`는 실제로 실행하여 결과를 확인하라.
