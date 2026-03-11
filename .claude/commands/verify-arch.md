# DDD 아키텍처 검증

## 지시사항

`src/main/kotlin/com/stayops/` 하위의 모든 Kotlin 소스 파일을 대상으로 아래 6가지 규칙을 검사하라.

검사할 소스 파일이 없으면 **"검사할 소스 파일이 없습니다. Phase 1부터 시작하세요."** 를 출력하고 종료하라.

---

### 규칙 1: Domain Purity

`**/domain/**/*.kt` 파일에서 다음 패턴이 존재하면 위반:
- `import org.springframework` (Spring 어노테이션/의존성)
- `import org.springframework.data.mongodb` (MongoDB 어노테이션)
- `@Document`, `@Field`, `@Id` (MongoDB 어노테이션)
- `@Entity`, `@Column`, `@Table` (JPA 어노테이션)
- `@Component`, `@Service`, `@Repository`, `@Bean` (Spring 컴포넌트)

**예외**: `domain/service/` 내에서 인터페이스 정의만 하는 경우는 허용하되, Spring 어노테이션은 여전히 금지.

### 규칙 2: Dependency Direction

의존성 방향 검사:
- `domain/` → `application/`, `api/`, `infrastructure/` import 금지
- `application/` → `api/` import 금지
- `infrastructure/` → `api/` import 금지

**허용**: `infrastructure/` → `domain/` (인터페이스 구현을 위해)

### 규칙 3: Business Logic Placement

`**/api/controller/**/*.kt` 파일에서 다음 패턴이 존재하면 위반:
- `if`/`when` 분기가 도메인 상태를 직접 검사하는 로직
- Repository 직접 호출
- 도메인 객체 생성/변경 로직

`**/infrastructure/persistence/**/*.kt` 파일에서:
- 비즈니스 규칙 판단 로직 (단순 CRUD 외의 조건문)

### 규칙 4: Package Structure

각 Bounded Context (shared 제외)가 다음 4개 패키지를 갖추었는지 확인:
- `domain/` (model, repository 하위 패키지 포함)
- `application/`
- `infrastructure/`
- `api/`

누락된 패키지가 있으면 경고.

### 규칙 5: BC Isolation

서로 다른 Bounded Context 간 직접 모델 import 검사:
- `com.stayops.reservation.domain.model`을 `com.stayops.property.domain`에서 import하면 위반
- ID(String) 참조만 허용

### 규칙 6: Immutability

`**/domain/model/**/*.kt` 파일에서:
- `var` 프로퍼티 사용 시 경고 (`val` 사용 권장)
- `data class`가 아닌 일반 `class`에서 mutable 상태 경고
- 상태 변경 메서드가 새 인스턴스를 반환하는지 확인 (`copy()` 또는 팩토리 메서드 사용)

---

## 출력 형식

```
## DDD 아키텍처 검증 결과

| # | 규칙 | 결과 | 위반 사항 |
|---|------|------|----------|
| 1 | Domain Purity | PASS / FAIL | 위반 파일:줄번호 |
| 2 | Dependency Direction | PASS / FAIL | ... |
| 3 | Business Logic Placement | PASS / FAIL | ... |
| 4 | Package Structure | PASS / FAIL | ... |
| 5 | BC Isolation | PASS / FAIL | ... |
| 6 | Immutability | PASS / WARN / FAIL | ... |

총 위반: N건 / 경고: M건
```

위반이 있으면 각 위반에 대해 **파일 경로, 줄 번호, 위반 내용, 수정 제안**을 함께 제시하라.

## 주의사항

- 이 커맨드는 검증 전용이다. 코드를 수정하지 마라.
- `shared/` 패키지는 공통 모듈이므로 BC Isolation 검사에서 제외하라.
- 위반이 0건이면 "모든 규칙을 준수합니다" 메시지를 출력하라.
