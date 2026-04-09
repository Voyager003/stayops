# RoomInventoryCache 도메인 포트 설계

## 배경

Phase 4(Inventory) 구현 시 `RoomInventory` 조회 성능 향상을 위해 Redis 기반 캐시를 도입했다. 그러나 구현 과정에서 `RoomInventoryApplication`이 `infrastructure.cache.RedisRoomInventoryCache`를 **구체 타입으로 직접 주입**받는 구조가 만들어졌다.

**리팩터링 전 `RoomInventoryApplication`**:
```kotlin
import com.stayops.inventory.infrastructure.cache.RedisRoomInventoryCache  // application → infrastructure

@Service
class RoomInventoryApplication(
    private val inventoryRepository: RoomInventoryRepository,
    private val cache: RedisRoomInventoryCache,  // 구체 타입 직접 주입
    // ...
)
```

### 이것이 왜 문제인가

CLAUDE.md가 정의한 Layered Architecture 의존 방향:

```
api → application → domain
              ↑
      infrastructure
```

Infrastructure는 **domain으로 향하는 화살표**이며, application이 infrastructure를 import하는 것은 금지된다. 위 코드는 이 규칙을 위반하여 다음 문제를 발생시킨다:

1. **의존 방향 역전**: Layered Architecture의 단방향 규칙 파괴
2. **기술 교체 시 파급**: Redis를 Caffeine(로컬 캐시) / Hazelcast / Memcached 등으로 교체하려면 `RoomInventoryApplication`과 테스트를 함께 수정해야 함
3. **테스트가 인프라에 결합**: 유닛 테스트 mock 대상이 "Redis 구현"이라는 착각을 유발
4. **도메인 레이어에서 캐시 개념 누락**: 캐시 동작 계약(get/put/evict)이 도메인에 선언되어 있지 않아, 다른 개발자가 "캐시는 infrastructure의 구현 디테일"로 오해할 수 있음

## 결정

**도메인 레이어에 `RoomInventoryCache` 포트(인터페이스)를 신설**하고, Infrastructure의 `RedisRoomInventoryCache`가 이를 구현하도록 한다. Application은 포트에만 의존한다.

### 도메인 포트 정의

```kotlin
// inventory/domain/repository/RoomInventoryCache.kt
package com.stayops.inventory.domain.repository

import com.stayops.inventory.domain.model.RoomInventory
import java.time.LocalDate

/**
 * 재고 조회 성능 향상을 위한 캐시 포트.
 *
 * Application 레이어가 특정 캐시 구현(Redis 등)에 의존하지 않도록 도메인 레이어에서
 * 인터페이스를 정의한다. 캐시 일관성은 write-through/evict-on-write 전략으로
 * Application이 책임진다.
 */
interface RoomInventoryCache {
    fun get(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory?
    fun put(inventory: RoomInventory)
    fun evict(propertyId: String, roomTypeId: String, date: LocalDate)
}
```

### Infrastructure 구현체

```kotlin
// inventory/infrastructure/cache/RedisRoomInventoryCache.kt
@Service
class RedisRoomInventoryCache(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : RoomInventoryCache {  // 포트 구현 선언

    override fun get(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory? { ... }
    override fun put(inventory: RoomInventory) { ... }
    override fun evict(propertyId: String, roomTypeId: String, date: LocalDate) { ... }
}
```

### Application 주입 타입 교체

```kotlin
@Service
class RoomInventoryApplication(
    private val inventoryRepository: RoomInventoryRepository,
    private val cache: RoomInventoryCache,  // 포트 타입
    // ...
)
```

## 포트 위치 결정 근거

`inventory/domain/repository/`를 선택한 이유:

| 후보 위치 | 판단 |
|---|---|
| `domain/repository/` ✅ | `RoomInventoryRepository`와 **대등한 영속/조회 포트** 성격. 기존 패키지 재사용. |
| `domain/service/` | `ChannelAdapterProvider`처럼 "전략 선택" 서비스가 아님. 부적합. |
| `domain/port/` | 프로젝트에 `port/` 서브디렉토리 컨벤션이 없음. 새 디렉토리 추가는 과잉. |

캐시는 "조회 성능 향상을 위한 second-level storage" 성격이므로 repository 계열이 가장 자연스럽다.

## 네이밍 결정 근거

`RoomInventoryCache`(접미사 없음)를 선택한 이유:

- 프로젝트 기존 인터페이스 명명: `Repository`, `Provider`, `Adapter`, `Resolver`, `Verifier` 등이 있으나 **`Port` 접미사는 사용처 없음**
- "캐시"라는 개념 자체가 이미 도메인 언어로 명확
- 구현체 이름(`RedisRoomInventoryCache`)이 저장소 기술을 명시하므로 인터페이스와 충돌 없음

## 시그니처 유지 근거

기존 `get`/`put`/`evict` 3개 메서드를 **그대로 인터페이스화**한 이유:

- 기존 시그니처가 이미 도메인 친화적임 (프로덕션 인자, 도메인 객체만 사용)
- TTL, 직렬화 포맷 같은 인프라 세부사항이 **시그니처에 노출되지 않음**
- 시그니처 변경 시 파급 효과가 큼 → 최소 침습 원칙 위배

## 캐시 일관성 전략

Application이 cache-aside + evict-on-write 패턴으로 일관성을 책임진다:

```kotlin
// 조회: cache-aside
private fun getOrThrow(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory =
    cache.get(propertyId, roomTypeId, date)
        ?: inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate(propertyId, roomTypeId, date)
            ?.also { cache.put(it) }
        ?: throw NotFoundException(...)

// 쓰기: write-through + evict
private fun saveAndEvict(inventory: RoomInventory): RoomInventory =
    inventoryRepository.save(inventory).also {
        cache.evict(inventory.propertyId, inventory.roomTypeId, inventory.date)
    }
```

DB가 진실의 원천(source of truth)이며, 캐시는 성능 최적화 계층으로만 사용한다. 캐시 장애가 발생해도 조회는 DB fallback으로 정상 동작한다.

## 반영 결과

**영향 파일**
- 신규: `inventory/domain/repository/RoomInventoryCache.kt`
- 수정: `inventory/infrastructure/cache/RedisRoomInventoryCache.kt` — 포트 구현 선언 + `override` 수정자
- 수정: `inventory/application/service/RoomInventoryApplication.kt` — import 및 주입 타입 변경
- 수정: `inventory/application/service/RoomInventoryApplicationTest.kt` — mockk 타입 변경

**검증**
- `./gradlew test` 전체 통과
- Grep: `application/` 하위에 `infrastructure.cache.RedisRoomInventoryCache` import 잔존 0건
- `RedisRoomInventoryCache` 참조는 infrastructure 구현체 선언부 1곳만 남음(의도된 상태)

## 트레이드오프

### 장점
- **DIP 준수**: Application이 추상화(포트)에 의존, Infrastructure도 추상화에 의존
- **교체 가능성**: Redis → Caffeine/Hazelcast/Memcached 전환 시 새 구현체만 추가하고 application 코드 무변경
- **테스트 단순화**: 도메인 포트 mock은 "비즈니스 계약"을 표현. 인프라 구현 세부사항에 대한 가정 없음
- **도메인 명시성**: "재고에는 캐시 개념이 있다"가 도메인 레이어에 명시됨

### 단점
- 파일 하나(포트 인터페이스) 추가
- 다만 이는 아키텍처 일관성 대비 매우 작은 비용

### 채택하지 않은 대안

- **기존 구체 타입 주입 유지**: Layered Architecture 위반 유지되므로 거부
- **별도 `CachedRoomInventoryService` 래퍼 도입**: 과잉 설계. `RoomInventoryApplication`이 이미 캐시 전략(cache-aside + evict)을 올바르게 구현하고 있음
- **Spring `@Cacheable` 어노테이션 사용**: TTL/evict 전략을 세밀하게 제어하기 어려움. 현재 `saveAndEvict` 같은 명시적 흐름이 디버깅에 더 유리

## 가이드라인 (향후 작업 시)

1. **Application은 절대로 `infrastructure/cache/*`를 import하지 않는다.** 캐시 필요 시 도메인 포트를 정의하거나 기존 포트를 확장한다.
2. **다른 도메인에서 캐시가 필요한 경우**(예: `Rate`, `Channel`) 같은 패턴을 따른다:
   - `{module}/domain/repository/{Entity}Cache.kt` 포트 정의
   - `{module}/infrastructure/cache/Redis{Entity}Cache.kt` 구현체
3. **포트 시그니처는 도메인 친화적으로 유지**한다. TTL, 직렬화 포맷, 키 네이밍 등의 인프라 세부사항을 노출하지 않는다.
4. **캐시 일관성은 Application이 책임진다.** 포트 구현체는 단순히 저장/조회만 수행한다.

## 참고

- 관련 커밋: `refactor: RoomInventoryCache 도메인 포트 도입 (DIP)` (R-3)
- 연관 ADR: `docs/context/infra/exception-boundary.md` — 동일한 DIP 원칙을 persistence 예외 변환에 적용한 사례
- Phase 4 원문: `docs/phases/phase-04-inventory.md` — Redis 캐시 도입 배경
