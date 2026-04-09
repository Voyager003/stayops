# BookingApplication / Inventory Port 리팩터링 후보 (추후 작업)

> 이 문서는 **결정 사항**이 아니라 **추후 리팩터링 후보**의 기록이다.
> R-12 SOLID 검증 중 Q&A 로 도출되었으며, 현 시점에는 진행하지 않는다.

## 배경

R-10-b 완료 직후, R-12 SOLID 검증 대상으로 `BookingApplication.kt` (298 줄, 6 개 후보 중 최대) 를 검토하던 중 **더 우선순위가 높은 구조 문제** 가 발견되었다.

`BookingApplication` 의 import 31 개 중 30 개는 `.domain.*` 또는 프레임워크인데, **오직 한 곳**이 다른 모듈의 Application 레이어를 직접 참조한다.

```kotlin
// src/main/kotlin/com/stayops/booking/application/service/BookingApplication.kt:6
import com.stayops.inventory.application.service.RoomInventoryApplication
```

실제 사용처:
- `BookingApplication.kt:42` — 생성자 파라미터
- `BookingApplication.kt:102` — `inventoryApplication.reserve(...)` in `createBooking()`
- `BookingApplication.kt:287` — `inventoryApplication.release(...)` in `cancelBooking()`

**호출하는 메서드는 단 2 개** (`reserve`, `release`) 인데, Booking 은 `RoomInventoryApplication` 전체(관리자 bulkBlock, blockInventory, 캐시 전략, ChannelSyncApplication 트리거 등) 에 결합되어 있다.

## 이것이 왜 문제인가 — SRP "변경 이유는 하나" 관점

### 1. Inventory 팀의 구현 변경이 Booking 을 깬다

`RoomInventoryApplication.reserve()` 는 단순한 재고 차감이 아니다:

```
reserve()
  ├─ getOrThrow()   → cache.get → repo.find → cache.put
  └─ saveAndEvict() → repo.save → cache.evict
```

그리고 같은 클래스의 다른 메서드들은 `channelSyncApplication.processTasksImmediately(...)` 같은 OTA 동기화 트리거 까지 한다. 즉 `RoomInventoryApplication` 전체가 Booking 의 잠재적 영향권.

**시나리오**: Inventory 팀이 캐시 전략을 Redis → Caffeine 로 변경. 이는 Inventory 팀의 내부 최적화이지만 `BookingApplicationTest` 의 mock stub 가정이 흔들리면서 Booking 팀이 테스트를 고치는 상황 발생.

**SRP 위반**: Booking 의 변경 이유에 "Inventory 모듈의 내부 최적화" 라는 엉뚱한 축이 추가된 상태.

### 2. 테스트에서 Inventory 의 전체 그래프를 흉내내야 한다

```kotlin
val inventoryApplication = mockk<RoomInventoryApplication>()
every { inventoryApplication.reserve(any(), any(), any()) } returns ...
```

겉보기에 단위 테스트이지만 **실제로는 Inventory Application 이라는 묵직한 의존성**을 mock 처리하고 있다. `RoomInventoryApplication` 생성자가 바뀔 때(Clock 추가, IdGenerator 추가 등) Booking Test 는 컴파일 오류는 없지만 **가정이 흔들린다**.

### 3. 트랜잭션 경계 모호

```kotlin
@Transactional
fun createBooking(...) {
    // ...
    dateRange.allDates().forEach { date ->
        inventoryApplication.reserve(propertyId, roomTypeId, date)  // 전파 전략에 암묵 종속
    }
    reservationRepository.save(...)
    paymentRepository.save(...)
}
```

Inventory 팀이 `reserve()` 의 `@Transactional` 을 `REQUIRES_NEW` 로 바꾸거나 비동기 이벤트로 전환하는 순간, Booking 의 "예약 생성 + 재고 차감이 원자적" 이라는 불변식이 조용히 깨질 수 있다. **계약이 코드에 명시되지 않은 암묵 합의.**

### 4. 순환 의존 가능성 개방

현재는 `Booking → Inventory` 단방향이지만, 미래에 Inventory 쪽에서 "재고 소진 시 Booking 취소" 같은 요구가 추가되면 `Inventory → Booking` 호출이 생기고 순환 의존 발생. Application 레이어끼리의 교차 호출은 이를 막을 구조적 장벽 부재.

### 5. 재사용성 저하

`RoomInventoryApplication` 의 존재 이유는 "관리자 유스케이스(BLOCK/UNBLOCK) 를 조율하는 오케스트레이터". Booking 이 필요한 `reserve()`/`release()` 는 그 일부일 뿐인데, 둘이 같은 클래스에 묶여 있어 Booking 이 관리자 유스케이스의 변경 축까지 함께 끌어안는다.

## 해결 접근법 — 도메인 Port 추출 (접근 A)

**원칙**: Application 은 Application 을 부르지 않는다. Application 은 Domain Port 에 의존한다.

### 목표 구조

```
┌──────────────────────────────────────────────────────┐
│ booking/application/service                         │
│   BookingApplication                                 │
│     ctor(inventoryReservationPort: ...)  ──────────┐ │
└──────────────────────────────────────────────────────┘
                                                     │
┌──────────────────────────────────────────────────────┐
│ inventory/domain/service                            │
│   interface InventoryReservationPort { ◄───────────┘
│       fun reserve(propertyId, roomTypeId, date)     │
│       fun release(propertyId, roomTypeId, date)     │
│   }                                                  │
└──────────────────────────────────────────────────────┘
                             ▲ implements
                             │
┌──────────────────────────────────────────────────────┐
│ inventory/application/service                       │
│   RoomInventoryApplication : InventoryReservationPort│
│     override fun reserve(...) { /* 기존 로직 유지 */ }│
│     override fun release(...) { /* 기존 로직 유지 */ }│
│     fun bulkBlock(...)        ← 관리자 유스케이스    │
│     fun blockInventory(...)   ← 관리자 유스케이스    │
└──────────────────────────────────────────────────────┘
```

### 핵심 속성

- **Port 는 Inventory 도메인이 소유**. Booking 은 `.domain.service.InventoryReservationPort` 에만 의존 → import 일관성 회복 (모든 import 가 다시 `.domain.*`).
- **구현체는 RoomInventoryApplication 그대로 유지**. `: InventoryReservationPort` 만 추가하면 기존 호출자 영향 0.
- Spring 은 유일 구현체를 자동으로 주입. `@Primary` / `@Qualifier` 불필요.

### 대안 접근

- **접근 B (도메인 서비스로 완전 분리)**: `InventoryReservationService` 를 순수 도메인 서비스로 만들고 `RoomInventoryApplication` 과 Booking 이 공유. 장점: 가장 순수한 DDD. 단점: 도메인 레이어에 Spring Component 가 생겨 CLAUDE.md "도메인은 pure" 원칙과 긴장. 변경 범위 큼.
- **접근 C (도메인 이벤트 디커플링)**: `InventoryReservationRequested` 이벤트 발행 후 listener 가 처리. 장점: 완전 디커플링. 단점: 강한 트랜잭션 불변식이 있는 예약 도메인에 eventual consistency 는 위험. 현재 단계에서는 과잉 설계.

## 권장 경로

**접근 A** 를 권장한다.

1. **최소 변경**: `RoomInventoryApplication` 내부 코드 무변경. `implements` 한 줄만 추가.
2. **Booking SRP 즉시 회복**: import 가 `.domain.*` 일관성 회복.
3. **향후 접근 B 이주 용이**: 나중에 더 완전한 분리가 필요하면 Port 는 그대로 두고 구현체만 교체 → Booking 은 수정 불필요.
4. **BookingApplication 분할과 시너지**: `createBooking` / `confirmPayment` / `cancelBooking` 3 분할 시, Port 단위로 의존성을 자연스럽게 재분배 가능.

## 구현 순서 (실행 시)

```
Step 1: inventory/domain/service/InventoryReservationPort.kt 신규 (reserve/release 2개 메서드)
Step 2: RoomInventoryApplication 에 : InventoryReservationPort 추가, override 키워드
Step 3: BookingApplication 생성자 파라미터 타입 RoomInventoryApplication → InventoryReservationPort
        import com.stayops.inventory.application.service.RoomInventoryApplication
          → import com.stayops.inventory.domain.service.InventoryReservationPort
Step 4: BookingApplicationTest 의 mockk<RoomInventoryApplication>() → mockk<InventoryReservationPort>()
Step 5: ./gradlew test 회귀 검증
Step 6: 커밋
```

각 단계가 독립 롤백 가능한 작은 변경.

## 후속 리팩터링 연쇄

Port 추출이 완료되면 다음 단계로:

1. **BookingApplication SRP 분할** (298 줄 → 3개 서비스)
   - `BookingCreationService` (createBooking, 약 100 줄)
     - 의존성: property, roomType, guest, channel, ratePlan, reservation, payment, inventoryReservationPort, rateResolver, clock, idGenerator
   - `BookingPaymentConfirmationService` (confirmPayment, 약 80 줄)
     - 의존성: reservation, payment, paymentGateway, clock
   - `BookingCancellationService` (cancelBooking + getMyReservations + getMyReservation, 약 60 줄)
     - 의존성: reservation, payment, paymentGateway, inventoryReservationPort
2. **ReservationApplication 도 동일 패턴 검토** (228 줄, 생성/조회/취소/체크인/체크아웃/노쇼 혼재)
3. **다른 크로스 모듈 Application 참조 전수 검사**

### 크로스 모듈 Application → Application 참조 전수 (기록 시점)

향후 실행 시 grep 재확인 필수. 현 기록 시점 기준 아는 케이스:
- `BookingApplication → RoomInventoryApplication` (본 문서 대상)
- `RoomInventoryApplication → ChannelSyncApplication` (재고 변경 시 OTA 동기화 트리거)
- `RoomApplication → RoomInventoryApplication` (객실 생성 시 재고 자동 생성)
- `ChannelApplication → ChannelSyncApplication` (OTA 채널 생성 시 초기 동기화)

이들 모두 같은 패턴의 Port 추출 대상. 단, 일괄 리팩터링은 리스크가 크므로 **Booking → Inventory 를 먼저 파일럿** 으로 진행 후 결과를 보고 나머지 확산 여부 결정.

## 검증 기준 (실행 시)

- `./gradlew test` 전체 통과
- `grep -rn "application.service" src/main/kotlin/com/stayops/*/application/service/*.kt` 결과 감소 확인
- BookingApplication 의 모든 import 가 `.domain.*` 또는 프레임워크로 수렴되는지 확인

## 참고 문서

- `docs/context/infra/exception-boundary.md` — Application 레이어의 Spring Data 예외 import 제거 사례 (R-1 ~ R-8)
- `docs/context/infra/cache-port-design.md` — Port 추출 DIP 패턴의 선행 사례 (R-3 RoomInventoryCache)
- `CLAUDE.md` — Layered Architecture 의존 방향 규칙
- Q&A 원본 대화는 R-10-b 완료 시점 (2026-04-09) 대화 로그에 있음

## 현재 상태

**보류**. R-10-b 완료 후 R-12 로 넘어가는 중이며, 사용자 지시에 따라 본 작업은 **추후 리팩터링 후보** 로 기록만 하고 진행하지 않는다. 다른 R-12 항목 또는 별도 주제를 먼저 처리한다.
