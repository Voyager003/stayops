# 테스트 실행

인자: $ARGUMENTS

## 지시사항

### 실행 모드 결정

`$ARGUMENTS` 값에 따라 실행 모드를 결정하라:

- **인자 없음 (빈 문자열)**: `./gradlew test` 전체 실행
- **BC명** (e.g., `reservation`, `property`, `room`, `inventory`, `guest`, `rate`, `channel`, `settlement`, `auth`): 해당 Bounded Context의 테스트만 실행
  - `./gradlew test --tests "com.stayops.{BC명}.*"`
- **`shared`**: 공통 도메인 테스트만 실행
  - `./gradlew test --tests "com.stayops.shared.*"`
- **`domain`**: 모든 BC의 도메인 레이어 테스트만 실행
  - `./gradlew test --tests "*.domain.*"`
- **`integration`**: 통합 테스트만 실행
  - `./gradlew test --tests "*.integration.*"` 또는 `*IntegrationTest*` 패턴

### 실행 후 결과 분석

1. **테스트 실행**: 해당 Gradle 명령을 실행하라.

2. **결과 파싱**: 테스트 결과를 분석하여 아래 형식으로 출력하라:

```
## 테스트 결과

실행: N개 | 성공: N개 | 실패: N개 | 스킵: N개

### BC × Layer 매트릭스

| BC | Domain | Application | Infrastructure | API | 합계 |
|----|--------|-------------|----------------|-----|------|
| shared | 5/5 | - | - | - | 5/5 |
| property | 3/3 | 2/2 | 1/1 | 1/1 | 7/7 |
| ... | ... | ... | ... | ... | ... |
```

3. **실패 분석**: 실패한 테스트가 있으면 각각에 대해:
   - 테스트 클래스명과 테스트 메서드명
   - Given/When/Then 중 어느 단계에서 실패했는지
   - 기대값 vs 실제값
   - 원인 분석 및 수정 제안

### 결과 파일 참조

Gradle 테스트 리포트 위치: `build/reports/tests/test/index.html`
테스트 결과 XML: `build/test-results/test/`

## 주의사항

- Docker가 실행 중이어야 Testcontainers 기반 테스트가 동작한다. Docker가 꺼져 있으면 알려줘라.
- 테스트 실행 전 `./gradlew compileTestKotlin`이 성공하는지 먼저 확인하라. 컴파일 에러가 있으면 테스트 실행 대신 컴파일 에러를 먼저 보고하라.
- 이 커맨드는 테스트 실행 및 결과 보고 전용이다. 코드를 수정하지 마라.
