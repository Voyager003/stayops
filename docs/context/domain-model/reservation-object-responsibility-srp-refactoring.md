# Application 책임 분리 / SRP 리팩터링 의사결정

이 문서는 reservation, channel, inventory application이 비대해지는 문제를 SRP 관점에서 검토하고, 실제 리팩터링 과정에서 내린 의사결정과 문답 결론을 시간 순서대로 기록한다.

## 문제 인식

초기 구조에서는 몇몇 application class가 여러 유스케이스를 함께 처리했다.

- `ReservationApplication`: PMS 예약 생성, 취소, 투숙 상태 변경, 조회 흐름이 함께 존재
- `CustomerReservationApplication`: 고객 예약 생성, 내 예약 조회, 결제 승인 요청, 예약 취소가 함께 존재
- `ChannelApplication`: 채널 관리, OTA 재고 비교, Mock OTA 예약 시뮬레이션이 함께 존재
- `RoomInventoryApplication`: 재고 동기화, 조회, 운영자 차단/해제, 예약 점유/복원, 캐시 접근이 함께 존재

SRP를 "기능이 하나여야 한다"로만 보면 대부분 분리 대상처럼 보이지만, 실제 기준은 "변경을 요구하는 actor가 같은가"였다. 따라서 무조건 클래스를 쪼개기보다 같은 actor의 요구를 완성하는 흐름은 병합하고, 변경 주체와 정책이 다른 흐름만 분리하기로 했다.

## 의사결정 흐름

### 1. SRP 기준을 기능 수가 아니라 변경 actor로 잡음

첫 기준은 "클래스가 가진 기능이 몇 개인가"가 아니라 "변경을 요구하는 주체가 몇 개인가"였다.

예를 들어 하나의 예약 생성 유스케이스 안에 숙소 검증, 객실 타입 검증, 요금 계산, Guest 생성, 결제 생성이 함께 있어도 이것이 모두 "고객 직접 예약 생성"이라는 actor의 요구를 완성하기 위한 흐름이면 즉시 분리하지 않는다.

반대로 취소 정책, 결제 환불 요청, 재고 복원은 모두 고객 예약 취소 안에 있지만 각각 결제 정책, 재고 정책, 예약 생명주기 정책의 변경 영향을 받기 때문에 분리 후보로 보았다.

### 2. Reservation 취소 정책을 먼저 분리함

`ReservationApplication`에서는 취소 시 재고 복원 여부 판단이 주요 분리 대상이었다.

취소 정책을 잘못 수정하면 고객 예약 취소뿐 아니라 PMS 예약 취소의 재고 복원에도 영향을 줄 수 있었다. 이에 따라 취소 시 재고를 복원할지 판단하는 정책을 별도 객체로 분리했다.

결과적으로 예약 취소 흐름은 그대로 유지하되, "언제 재고를 복원해야 하는가"라는 정책 판단이 이름 있는 단위로 드러났다.

### 3. Stay 운영 유스케이스를 분리함

`ReservationApplication` 안에는 예약 생성/취소뿐 아니라 체크인, 체크아웃, 노쇼 같은 투숙 운영 흐름도 함께 있었다.

이 흐름은 예약 생성 actor보다 프론트데스크 또는 숙소 운영자의 변경 요구에 가깝다. 체크인 가능 조건, 체크아웃 처리, 노쇼 처리 정책은 예약 생성 정책과 함께 바뀌지 않는다.

이에 따라 투숙 운영 흐름은 `StayOperationApplication`으로 분리했다.

### 4. Reservation 조회 유스케이스를 분리함

예약 조회는 command 흐름과 변경 이유가 다르다.

조회는 화면 요구, 필터 조건, pagination, 정렬, 응답 조립 방식에 의해 바뀐다. 반면 예약 생성/취소는 도메인 상태 전이와 transaction 정책에 의해 바뀐다.

이에 따라 조회 흐름은 `ReservationQueryApplication`으로 분리했다.

### 5. Channel 유스케이스를 분리함

`ChannelApplication`은 채널 생성/수정/상태 변경뿐 아니라 OTA 재고 비교와 Mock OTA 예약 시뮬레이션도 함께 처리했다.

이 세 흐름은 변경 actor가 달랐다.

- 채널 관리: PMS 운영자 또는 채널 설정 정책
- 재고 비교: 운영자가 PMS와 OTA의 재고 불일치를 확인하는 진단 흐름
- Mock OTA 시뮬레이션: 개발/테스트용 외부 채널 예약 유입 검증 흐름

이에 따라 채널 관리는 `ChannelApplication`에 남기고, 재고 비교는 `ChannelInventoryComparisonApplication`, Mock OTA 시뮬레이션은 `MockOtaSimulationApplication`으로 분리했다.

### 6. CustomerReservationApplication은 facade로 남기고 내부 유스케이스를 분리함

`CustomerReservationApplication`은 API와 E2E에서 직접 사용되는 진입점이었다. 이름을 없애거나 public method를 바꾸면 controller와 테스트 계약이 크게 흔들릴 수 있었다.

따라서 기존 public contract는 유지하고, 내부 구현만 다음 유스케이스로 분리했다.

- `CustomerReservationCreationApplication`: 고객 직접 예약 생성
- `CustomerReservationQueryApplication`: 내 예약 목록/상세 조회
- `CustomerReservationPaymentApplication`: 결제 승인 요청
- `CustomerReservationCancellationApplication`: 고객 예약 취소

이 결정의 핵심은 facade를 완전히 제거하는 것이 아니라, facade를 얇게 만들고 실제 변경 이유를 가진 흐름을 내부 유스케이스로 나누는 것이었다.

### 7. RoomInventoryApplication도 facade로 남기고 내부 유스케이스를 분리함

`RoomInventoryApplication`도 API, E2E, scheduler에서 직접 사용되고 있었다. 따라서 기존 진입점은 유지하고 내부 책임만 분리했다.

초기 분리 결과는 다음과 같았다.

- `RoomInventorySyncApplication`: 객실 수 기준 재고 생성/보정
- `RoomInventoryQueryApplication`: 가용 재고 조회
- `RoomInventoryManagementApplication`: 운영자 재고 차단/차단 해제
- `RoomInventoryReservationApplication`: 예약 도메인의 재고 점유/복원
- `RoomInventoryAccessApplication`: 캐시 fallback, DB 조회, 저장 후 캐시 무효화

이후 `RoomInventoryQueryApplication`은 다시 병합했다. 이유는 단순 repository 위임만 수행하고 별도 정책, transaction, 캐시, 응답 조립 책임이 없었기 때문이다.

최종적으로 `RoomInventoryApplication`은 facade로 남되, 조회는 직접 처리하고 나머지 변경 이유가 뚜렷한 흐름만 내부 application으로 위임한다.

## 문답 정리

### Q. SRP는 기능이 하나여야 한다는 뜻인가?

아니다. SRP는 "하나의 클래스는 하나의 변경 이유만 가져야 한다"는 기준에 가깝다.

따라서 메서드가 여러 개 있어도 같은 actor의 요구에 의해 함께 바뀐다면 같은 class에 둘 수 있다. 반대로 메서드가 하나여도 서로 다른 정책 변화를 한꺼번에 떠안고 있다면 분리 대상이 될 수 있다.

### Q. "actor"와 "변경 이유"는 같은 의미로 봐도 되는가?

실무 판단에서는 거의 같은 기준으로 사용할 수 있다.

actor는 변경을 요구하는 주체다. 예를 들어 고객, 숙소 운영자, 채널 운영자, 결제 시스템, 예약 운영 정책은 서로 다른 actor가 될 수 있다. 변경 이유는 이 actor의 요구가 코드에 반영되는 지점이다.

### Q. DDD에서 application layer가 repository와 domain 기능을 조립하는 것이 맞는가?

Yes. application layer는 domain object를 직접 대신하지 않고, repository 조회, transaction 경계, domain method 호출, port 호출, 결과 조립을 담당한다.

중요한 것은 application layer가 정책 조건을 모두 흡수해 domain을 빈 객체로 만들지 않는 것이다. 핵심 도메인 규칙은 domain model이나 policy object에 있어야 한다.

### Q. 계층형 구조와 DDD 구조에서 application service는 어떻게 다른가?

계층형 구조에서는 service가 business logic을 직접 많이 품는 경우가 흔하다. DDD에서는 application service가 use case orchestration을 담당하고, 핵심 규칙은 domain model, domain service, policy 객체로 이동시키는 방향을 선호한다.

이번 리팩터링도 application service를 없애는 작업이 아니라, application service가 어떤 use case를 조율하는지 명확히 나누는 작업이었다.

### Q. application class에 private field가 많으면 분리 신호인가?

분리 신호일 수 있다. 다만 field 수 자체가 기준은 아니다.

특정 dependency가 특정 method에서만 쓰이고, 그 method가 다른 actor의 변경 요구를 받는다면 분리 근거가 강하다. 반대로 여러 dependency가 하나의 transaction/use case를 완성하기 위해 자연스럽게 함께 쓰인다면 단순히 많다는 이유만으로 분리하지 않는다.

### Q. facade 역할을 하는 application layer가 생기는 것이 일반적인가?

일반적이다. API나 외부 호출부가 내부 유스케이스 여러 개를 직접 알지 않도록 단순한 진입점을 제공할 수 있다.

다만 facade가 모든 비즈니스 정책을 직접 품으면 god object가 된다. 현재 방향은 facade를 얇게 유지하고 내부 use case로 위임하는 것이다.

### Q. facade를 유지하는 이유는 무엇인가?

기존 API, E2E, scheduler, 다른 bounded context의 호출 계약을 안정적으로 유지하기 위해서다.

`CustomerReservationApplication`과 `RoomInventoryApplication`은 이미 외부 진입점으로 사용되고 있었다. 이를 바로 제거하면 리팩터링 범위가 API 계약 변경으로 커진다. 따라서 public method는 유지하고 내부 구현만 분리했다.

### Q. service를 너무 많이 나누면 어떤 문제가 생기는가?

관리 포인트가 늘어난다.

- 어떤 class를 봐야 하는지 탐색 비용 증가
- DI bean 증가
- 테스트 fixture와 mock 구성 증가
- transaction 경계가 분산될 위험
- 이름 피로도 증가
- 단순 위임 class 증가

따라서 분리 후에도 단순 위임만 남은 class는 다시 병합할 수 있어야 한다.

### Q. 실제로 다시 병합한 사례가 있는가?

있다. `RoomInventoryQueryApplication`은 분리 후 다시 병합했다.

이 class는 단순히 repository 조회만 위임했고, 별도 정책이나 응답 조립, 캐시, transaction 판단이 없었다. 그래서 독립 class로 유지하는 비용이 이점보다 크다고 판단했다.

### Q. 어떤 분리는 유지할 만한가?

다음 분리는 유지 근거가 있다.

- `RoomInventorySyncApplication`: 객실 수 변경, rolling horizon, 채널 availability sync 정책 변화
- `RoomInventoryManagementApplication`: 운영자 수동 재고 차단/해제 정책 변화
- `RoomInventoryReservationApplication`: 예약/취소 흐름에서 사용하는 재고 점유/복원 port 구현
- `RoomInventoryAccessApplication`: 캐시 장애 fallback과 저장 후 캐시 무효화 정책 중앙화

각각 변경 actor가 다르거나, 여러 유스케이스에서 재사용되는 정책 경계가 있다.

### Q. ReservationPaymentPort를 둔 이유는 무엇인가?

예약 도메인이 payment 내부 모델, repository, outbox 구현을 직접 알지 않게 하기 위해서다.

고객 예약 흐름은 결제 생성, 승인 요청, 취소 요청이 필요하지만 payment 내부 저장 구조나 outbox 타입까지 알아야 할 이유는 없다. 따라서 reservation은 `ReservationPaymentPort`에만 의존하고, payment 쪽 adapter가 내부 구현을 감춘다.

### Q. payment 도메인에 reservation adapter를 둔 이유는 무엇인가?

결제는 payment bounded context 내부 책임이고, reservation은 결제에 필요한 협력만 port로 요청한다.

adapter는 reservation이 요구하는 계약을 payment 구현으로 연결하는 역할을 한다. 이렇게 하면 reservation application은 결제 내부 모델 변경에 덜 흔들리고, payment는 자체 outbox/PG 연동 정책을 내부에서 관리할 수 있다.

### Q. Outbox나 SyncTask는 Background Job으로 봐도 되는가?

운영 관점에서는 Background Job으로 봐도 무방하다.

다만 도메인 의미는 단순 비동기 작업보다 구체적이다. Outbox는 DB transaction과 외부 시스템 호출 사이의 이벤트 유실을 줄이기 위한 패턴이고, SyncTask는 OTA/채널 동기화 재시도 단위다.

### Q. exactly-once는 무엇인가?

이상적으로는 이벤트가 정확히 한 번만 처리되는 보장을 뜻한다.

하지만 실제 분산 시스템에서는 네트워크 재시도, consumer 중복 실행, DB commit과 외부 호출 사이의 불일치 때문에 exactly-once를 완전히 보장하기 어렵다. 보통은 at-least-once 처리와 idempotency key, 중복 처리 방지 테이블, 상태 전이를 조합해 "결과적으로 한 번 처리된 것처럼" 만든다.

### Q. 노쇼 처리 시 재고 복원을 하지 않는 것이 정상인가?

정상 흐름에 가깝다.

노쇼는 체크인 시간이 지나고 실제 숙박일이 소진된 뒤 처리되는 상태다. 이미 해당 날짜의 재고 판매 기회가 지나갔기 때문에 일반적인 취소처럼 재고를 복원하면 오히려 잘못된 재판매 가능성을 만들 수 있다.

### Q. OTA 예약 취소 시 재고 동기화가 필요한가?

필요하다.

OTA 취소는 외부 채널에서 확정되었던 예약이 취소되는 것이므로 PMS 재고를 복원하고, 변경된 availability를 다른 채널로 다시 동기화해야 한다. 이 흐름은 webhook 취소 처리와 channel sync 이벤트 발행으로 다룬다.

### Q. DTO는 data class로 적합한가?

대체로 적합하다.

DTO는 상태와 데이터를 전달하는 것이 주 목적이고, 값 기반 equals/hashCode가 문제가 되기보다 테스트와 직렬화에서 장점이 되는 경우가 많다. 반면 aggregate나 entity처럼 식별자 기반 동일성이 중요한 domain object는 data class를 신중하게 써야 한다.

### Q. domain object를 data class에서 일반 class로 전환한 이유는 무엇인가?

entity나 aggregate는 모든 필드가 같아야 같은 객체인 것이 아니라, 같은 identity를 가지면 같은 객체로 보는 경우가 많다.

data class의 자동 equals/hashCode는 모든 주 생성자 property를 비교하기 때문에, 도메인 동일성 기준을 흐릴 수 있다고 판단했다. 그래서 `Property`, `RoomInventory`처럼 identity가 중요한 객체는 일반 class로 전환하고 명시적 equals/hashCode를 두었다.

### Q. copyState를 둔 이유는 무엇인가?

불변에 가까운 domain object 갱신 방식을 유지하기 위해서다.

상태 변경 메서드가 기존 객체를 직접 변경하지 않고 새 객체를 반환하면, 도메인 상태 전이가 명확해지고 테스트도 쉬워진다. 다만 Kotlin data class의 public copy를 그대로 노출하면 외부에서 불변식 검증 없이 상태를 만들 수 있으므로, 내부 private copyState로 제한했다.

### Q. ZonedDateTime을 지금 도입해야 하는가?

현재 대부분의 시스템 시각은 `Instant`로 유지하는 것이 적절하다고 판단했다.

숙박일, 재고일처럼 "날짜 자체"가 중요한 값은 `LocalDate`가 맞고, 결제 승인 시각이나 이벤트 처리 시각처럼 절대 시점이 중요한 값은 `Instant`가 맞다. `ZonedDateTime`은 현지 시간 규칙 자체가 도메인 로직에 필요할 때 도입하는 것이 적절하다.

### Q. Asia/Seoul 하드코딩은 어떻게 다뤘는가?

전역 하드코딩을 제거하고 timezone 정책을 중앙화했다.

현재는 글로벌 서비스를 가정하더라도 모든 시간을 `ZonedDateTime`으로 바꾸는 것보다, timezone이 필요한 지점에서 property timezone 또는 중앙 정책을 통해 변환하는 방향이 더 안전하다고 판단했다.

## 현재 구조 판단

현재 구조는 다음 방향으로 정리되었다.

- facade는 외부 호출 계약 안정화를 위해 유지
- 내부 유스케이스는 변경 actor가 다를 때 분리
- 단순 repository 위임만 하는 class는 분리하지 않음
- port는 bounded context 간 협력 지점에만 둠
- application service는 orchestration을 맡고, 도메인 정책은 이름 있는 domain/policy 객체로 이동

## 남은 검토 후보

### WebhookApplication

아직 분리 신호가 강한 후보다.

현재 webhook 수신 경계, 서명 검증, 중복 이벤트 처리, OTA 예약 생성, OTA 취소 처리, 결제 기록, 재고 반영, 이벤트 발행이 한 class에 있다.

변경 actor가 다음처럼 섞일 수 있다.

- OTA webhook 계약
- 보안/서명 검증 정책
- 중복 이벤트 멱등성 정책
- OTA 예약 생성 정책
- OTA 취소 및 재고 복원 정책
- 내부 이벤트 발행 정책

따라서 다음 리팩터링 후보는 `WebhookApplication`이다.

## 유지할 설계 원칙

- Controller는 facade 또는 use case만 호출한다.
- Application facade는 얇게 유지한다.
- 같은 actor의 요구를 완성하는 흐름은 과도하게 쪼개지 않는다.
- 변경 actor가 다르면 유스케이스 또는 policy로 분리한다.
- Reservation은 payment 내부 모델, repository, outbox 타입을 직접 알지 않는다.
- Payment 협업은 `ReservationPaymentPort`를 통해 수행한다.
- Inventory 예약 점유/복원은 `InventoryReservationPort`를 통해 수행한다.
- 외부 PG 호출과 OTA 동기화는 재시도 가능한 background 처리로 분리한다.
- 단순 repository 위임 class는 독립 application으로 만들지 않는다.

## 참고 기준

- Robert C. Martin, Single Responsibility Principle: 같은 actor/reason to change 기준으로 책임을 나눈다.
- Martin Fowler, Service Layer: application boundary와 operation orchestration을 담당한다.
- Martin Fowler, Transaction Script: 단순한 요청 단위 절차화는 실용적일 수 있다.
- Martin Fowler, Anemic Domain Model: 핵심 비즈니스 규칙이 service에만 몰리는 구조는 장기 유지보수 비용을 만든다.
- Martin Fowler, CQRS: command/query 분리는 복잡한 일부 bounded context에 제한적으로 적용한다.
- Chris Richardson, Transactional Outbox / Saga: 외부 시스템 호출과 DB transaction을 함께 다룰 때 outbox와 보상 흐름을 명시한다.
