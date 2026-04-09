# Infrastructure 예외 경계 설계

## 배경

Phase 1~10 구현 과정에서 Spring Data의 persistence 예외(`OptimisticLockingFailureException`, `DuplicateKeyException`)가 **Application 레이어에 직접 노출**되는 구조가 누적되었다.

**리팩터링 전 상태**:
- `com.stayops.inventory.application.service.RoomInventoryApplication`이 `org.springframework.dao.OptimisticLockingFailureException`을 import하고 `blockInventory/unblockInventory/reserve/release` 4개 메서드에서 각각 try/catch로 잡아 `ConflictException`으로 변환
- `com.stayops.channel.application.service.ChannelSyncApplication`이 동일한 예외를 `processPendingTasks`, `processTasksImmediately`에서 catch
- `com.stayops.channel.application.service.WebhookApplication`이 `org.springframework.dao.DuplicateKeyException`을 catch하여 Webhook 중복 처리 로직 구현

### 이것이 왜 문제인가

1. **Layered Architecture 위반**: Application 레이어가 `org.springframework.dao` 즉 Spring Data 구현 세부사항을 알고 있어 DIP(Dependency Inversion Principle) 원칙에 어긋남.
2. **기술 용어 vs 도메인 용어 혼재**: `DuplicateKeyException`(MongoDB unique index 위반)은 기술 개념이지 도메인 개념이 아니다. 코드를 읽는 사람이 "왜 갑자기 Spring 예외를 처리하지?"라고 의문을 갖게 됨.
3. **테스트가 구현에 종속**: Application 유닛 테스트에서 mock repository가 Spring 예외를 throw해야 했음. 이는 테스트가 persistence 구현을 가정하게 만듦.
4. **예외 변환 중복**: 같은 `ConflictException("INVENTORY_CONFLICT", ...)` 변환 코드가 Application의 4개 메서드에 반복 작성됨.

## 결정

**예외 경계를 Infrastructure 레이어로 이동한다.** Repository 구현체가 Spring Data 예외를 흡수하여 도메인 개념 예외(`ConflictException`) 또는 도메인 언어 결과 타입(`Boolean`)으로 변환한다.

### 적용 규칙

| Spring Data 예외 | 변환 방식 | 위치 |
|---|---|---|
| `OptimisticLockingFailureException` | `ConflictException(code, message)`로 재발행 | Repository 구현체의 `save()` |
| `DuplicateKeyException` (멱등 저장 목적) | `Boolean` 반환값으로 표현 | Repository 구현체의 멱등 저장 메서드 |
| 기타 persistence 예외 | 필요 시점에 개별 검토 | — |

### ConflictException code 컨벤션

Repository에서 변환할 때는 **컨텍스트를 식별할 수 있는 code**를 부여한다:

```kotlin
// MongoRoomInventoryRepository
throw ConflictException(
    code = "INVENTORY_CONFLICT",
    message = "재고 변경 충돌이 발생했습니다. 다시 시도해주세요."
)

// MongoSyncTaskRepository
throw ConflictException(
    code = "SYNC_TASK_CONFLICT",
    message = "SyncTask 버전 충돌이 발생했습니다: ${task.id}"
)
```

### 멱등 저장(saveIfAbsent) 패턴

Webhook 중복 감지처럼 "이미 있으면 저장 안 함"이 도메인 요구사항인 경우, Repository interface에 **멱등 저장 메서드**를 정의하고 구현체가 `DuplicateKeyException`을 흡수해 `Boolean`으로 반환한다.

```kotlin
// domain/repository/ProcessedWebhookEventRepository.kt
interface ProcessedWebhookEventRepository {
    /**
     * 멱등 저장: 동일한 eventId가 이미 존재하면 false를 반환하고 저장하지 않는다.
     */
    fun saveIfAbsent(event: ProcessedWebhookEvent): Boolean
}

// infrastructure/persistence/MongoProcessedWebhookEventRepository.kt
override fun saveIfAbsent(event: ProcessedWebhookEvent): Boolean =
    try {
        mongo.save(ProcessedWebhookEventDocument.from(event))
        true
    } catch (e: DuplicateKeyException) {
        false
    }
```

이 패턴의 장점:
- MongoDB unique index가 원자성을 DB 수준에서 보장 → race condition 없음
- Application은 `if (!saveIfAbsent(...)) return`처럼 **선언적으로** 작성 가능
- 도메인 언어(`saveIfAbsent`)로 의도가 명확히 드러남

## 반영 결과

### Application 레이어 정화 (2026-04-08)

`application/` 하위 코드에서 `org.springframework.dao.*` import가 **완전히 제거**됨:

```bash
$ grep -r "org.springframework.dao" src/main/kotlin/com/stayops/*/application/
(no results)
```

### 영향 파일

**Infrastructure (예외 흡수 및 변환)**
- `inventory/infrastructure/persistence/MongoRoomInventoryRepository.kt` — `save()`가 OLFE → `ConflictException("INVENTORY_CONFLICT")` 변환
- `channel/infrastructure/persistence/MongoSyncTaskRepository.kt` — `save()`가 OLFE → `ConflictException("SYNC_TASK_CONFLICT")` 변환
- `channel/infrastructure/persistence/MongoProcessedWebhookEventRepository.kt` — `saveIfAbsent()` 구현, `DuplicateKeyException` 흡수

**Domain (도메인 언어 표현)**
- `channel/domain/repository/ProcessedWebhookEventRepository.kt` — `save()`, `existsByEventId()` 제거 후 `saveIfAbsent(): Boolean` 단일 메서드로 정리

**Application (예외 import 및 try/catch 제거)**
- `inventory/application/service/RoomInventoryApplication.kt` — 4개 메서드의 try/catch 제거, `bulkBlock`의 catch는 `ConflictException` skip으로 전환
- `channel/application/service/ChannelSyncApplication.kt` — 2개 메서드의 OLFE catch → `ConflictException` catch
- `channel/application/service/WebhookApplication.kt` — try/catch를 `if (!saveIfAbsent(...)) return`으로 대체

**Test (새 계약 검증)**
- `RoomInventoryApplicationTest`, `ChannelSyncApplicationTest`, `WebhookApplicationTest` — mock이 `ConflictException` / `Boolean` 기반으로 전환
- `MongoRoomInventoryRepositoryTest` — 통합 테스트는 새 Repository 계약(`ConflictException` + `code == "INVENTORY_CONFLICT"`)을 검증

## 트레이드오프

### 장점
- Application 레이어가 framework-agnostic. Persistence 기술 교체 시 Application 수정 최소화.
- 예외 변환 로직이 **한 곳**(Repository 구현체)에 집중되어 중복 제거.
- Repository 인터페이스가 **도메인 언어**로 표현됨.
- Application 코드가 비즈니스 흐름만 드러내어 가독성 향상.

### 단점
- Repository 구현체가 `try/catch`로 약간 복잡해짐. 다만 이는 정당한 경계 책임.
- Stack trace가 Repository 경계에서 한 번 끊김. 단, `ConflictException`의 `code` 필드로 원인 식별 가능.

### 채택하지 않은 대안

- **Option B: 신규 `ConcurrencyConflictException` 도입**
  기존 `ConflictException` 외에 별도 예외 클래스 추가. 분류는 더 명확하지만 `GlobalExceptionHandler`가 이미 `ConflictException`을 HTTP 409로 매핑하고 있어 추가 이득이 적음.

- **Option C: AOP / `@ControllerAdvice`로 예외 변환**
  Repository는 순수하게 유지하고 예외 변환을 cross-cutting으로 처리. 하지만 `RoomInventoryApplication.bulkBlock`처럼 **충돌 시 해당 날짜만 skip하고 계속 진행** 같은 세밀한 제어가 불가능해짐.

- **Option D: 기존 구조 유지**
  가장 안전하지만 Layered Architecture 위반이 누적되어 향후 기술 교체 시 파급 효과 증가.

## 가이드라인 (향후 작업 시)

1. **Repository 구현체는 절대로 Spring Data 예외를 상위로 전파하지 않는다.** 필요 시 `ConflictException` 또는 도메인 언어 결과 타입으로 변환.
2. **멱등 저장이 필요한 경우** `saveIfAbsent()` 같은 도메인 메서드를 추가한다. Application에서 check-then-save 패턴은 race condition 위험이 있으므로 금지.
3. **Application 레이어 코드 리뷰 시** `import org.springframework.dao.*`가 추가되면 거부한다.
4. **Repository 통합 테스트**는 새 계약(`ConflictException`)을 검증한다. 원시 Spring 예외를 검증하지 않는다.

## 참고

- 관련 커밋:
  - R-2-a: `refactor: OptimisticLockingFailureException을 Infrastructure 경계에서 흡수`
  - R-2-b: `refactor: Webhook 멱등 저장을 도메인 메서드로 표현`
- 공통 예외 계층: `com.stayops.shared.exception.{BusinessException, ConflictException, NotFoundException, ForbiddenException}`
- 전역 예외 처리: `com.stayops.shared.exception.GlobalExceptionHandler`
