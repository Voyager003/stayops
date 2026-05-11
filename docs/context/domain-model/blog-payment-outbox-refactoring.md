# 결제 승인 트랜잭션에서 Outbox를 도입한 이유

처음 구현은 단순했다.

```kotlin
@Transactional
fun confirmPayment(
    memberId: String,
    reservationId: String,
    paymentKey: String,
    orderId: String,
    amount: BigDecimal
): CustomerReservationResult {
    val reservation = reservationRepository.findById(reservationId)
        ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
    val payment = paymentRepository.findByReservationId(reservationId)
        ?: throw NotFoundException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: $reservationId")

    val confirmResult = paymentGateway.confirm(paymentKey, orderId, amount)

    val approvedPayment = paymentRepository.save(
        payment.approve(
            paymentKey = confirmResult.paymentKey,
            method = confirmResult.method ?: "unknown",
            approvedAt = confirmResult.approvedAt ?: clock.instant()
        )
    )
    val confirmedReservation = reservationRepository.save(reservation.confirm())

    return CustomerReservationResult(confirmedReservation, approvedPayment)
}
```

코드만 보면 읽기 쉽다. 예약을 찾고, 결제를 찾고, PG 승인을 호출하고, 결제와 예약 상태를 바꾼다.

하지만 이 흐름은 결제라는 도메인에서는 위험하다. `@Transactional`이 보호하는 것은 MongoDB에 저장하는 내부 상태뿐이다. Toss Payments 같은 외부 PG에 이미 나간 요청은 우리 DB 트랜잭션이 rollback된다고 함께 취소되지 않는다.

## 문제는 외부 API가 트랜잭션 밖에 있다는 점이었다

기존 구조의 가장 큰 문제는 `CustomerReservationApplication.confirmPayment()`가 다음 두 일을 한 트랜잭션 안에서 같이 수행했다는 점이다.

```text
1. 내부 상태 변경
   - Payment.APPROVED
   - Reservation.CONFIRMED

2. 외부 상태 변경
   - paymentGateway.confirm(...)
```

내부 상태 변경은 MongoDB transaction의 대상이다. 외부 PG 호출은 아니다.

그래서 실패 지점이 생긴다.

```text
Case 1. PG 승인 성공 -> DB 저장 실패
외부 결제는 성공했지만 우리 DB에는 Payment.PENDING, Reservation.PENDING이 남을 수 있다.

Case 2. DB 상태 일부 저장 -> 중간 예외
Payment와 Reservation 중 일부만 기대와 다르게 저장될 가능성을 계속 신경 써야 한다.

Case 3. PG 호출 timeout
실제로 승인됐는지, 승인되지 않았는지 응답만으로는 모른다.

Case 4. 서버 다운
PG 호출 직전 또는 직후에 프로세스가 죽으면 재시도해야 할 작업 자체가 메모리에서 사라질 수 있다.
```

기존 구현은 일부 문제를 알고 있었다. `AlreadyProcessed`가 오면 결제 조회를 하고, `DONE`이면 내부 상태를 복구했다. `ProviderError`가 오면 `Payment.PENDING`을 유지했다.

하지만 그 방식은 충분하지 않았다.

조회는 “이미 외부에 요청이 도달한 뒤 응답을 못 받은 경우”를 복구하는 데 도움이 된다. 반면 “외부 요청을 보내야 한다는 사실 자체가 서버 다운으로 사라지는 경우”는 해결하지 못한다. 결제 승인이라는 명령이 영속화되어 있지 않았기 때문이다.

## 처음 후보는 세 가지였다

결제 안정성을 높이는 선택지는 크게 세 가지로 보였다.

```text
1. PG 요청에 멱등성 키를 보낸다.
2. PG 상태 조회로 외부 상태를 복구한다.
3. Outbox를 둬서 외부 호출 명령을 영속화한다.
```

셋 중 하나만 고르면 되는 문제는 아니었다. 각각 해결하는 문제가 다르다.

멱등성 키는 같은 요청이 여러 번 나가는 문제를 줄인다. Toss Payments 문서도 `Idempotency-Key` 헤더를 사용하면 같은 요청이 반복될 때 첫 요청과 같은 응답을 돌려줄 수 있다고 설명한다. 하지만 멱등성 키만으로는 “우리 서버가 PG 요청을 보내기 전에 죽어서 요청 자체가 유실되는 문제”를 해결할 수 없다.

PG 상태 조회는 timeout이나 `ALREADY_PROCESSED` 같은 애매한 상태를 복구하는 데 필요하다. 하지만 조회도 결국 “무엇을 조회해야 하는지”가 남아 있어야 한다. 영속화된 작업이 없으면 재시도할 기준도 없다.

Outbox는 다른 문제를 해결한다. 내부 상태 변경과 외부로 내보낼 작업 기록을 같은 DB transaction에 저장한다. AWS의 Transactional Outbox 설명처럼, DB write와 메시지/이벤트 전달이 서로 다른 시스템에 걸쳐 있을 때 dual write 문제가 생기고, Outbox는 그 작업을 같은 transaction에 먼저 저장해서 불일치를 줄이는 방식이다.

## Redis Queue를 단독 Outbox로 쓰지 않은 이유

Redis Queue도 후보로 볼 수 있었다.

하지만 현재 업무 데이터는 MongoDB에 있다. `Payment`, `Reservation`도 MongoDB에 있고, 이번에 저장해야 할 결제 승인 작업도 이 상태들과 함께 원자적으로 남아야 한다.

Redis에 queue push를 하고 MongoDB에 상태를 저장하면 다시 dual write가 된다.

```text
MongoDB transaction commit 성공 + Redis push 실패
-> 결제 승인 요청 상태는 남았지만 처리할 queue 메시지가 없다.

Redis push 성공 + MongoDB transaction rollback
-> queue worker는 존재하지 않는 결제 상태를 처리하려고 할 수 있다.
```

Redis Streams는 consumer group과 pending entry를 이용할 수 있으므로 전달 채널로는 고려할 수 있다. 하지만 현재 단계에서 필요한 것은 전달 채널보다 “업무 상태와 같은 transaction에 저장되는 작업 원장”이다.

그래서 Redis는 단독 Outbox로 선정하지 않았다. MongoDB에 Outbox를 두고, 나중에 처리량이나 분산 worker 요구가 생기면 Redis Streams나 메시지 브로커를 보조 전달 채널로 붙이는 순서가 더 안전하다고 판단했다.

## 선택한 개선: MongoDB 기반 PaymentOutboxMessage

개선 후 `confirmPayment()`의 책임은 바뀌었다.

```kotlin
@Transactional
fun confirmPayment(...): CustomerReservationResult {
    // 1. Reservation/Payment 조회
    // 2. 소유자, 만료, orderId, amount 검증
    // 3. Payment.CONFIRM_REQUESTED 저장
    // 4. PaymentOutboxMessage 저장
    // 5. 202 Accepted로 요청 접수 상태 반환
}
```

이제 이 메서드는 PG를 직접 호출하지 않는다.

```kotlin
val requestedPayment = payment.requestConfirm(paymentKey)
val savedPayment = paymentRepository.save(requestedPayment)

paymentOutboxRepository.save(
    PaymentOutboxMessage.createConfirm(
        id = idGenerator.generate(),
        paymentId = savedPayment.id,
        reservationId = reservation.id,
        memberId = memberId,
        paymentKey = paymentKey,
        orderId = savedPayment.orderId,
        amount = savedPayment.amount,
        now = clock.instant()
    )
)

return CustomerReservationResult(reservation, savedPayment)
```

응답 의미도 바뀌었다.

```text
변경 전
200 OK
Reservation.CONFIRMED
Payment.APPROVED

변경 후
202 Accepted
Reservation.PENDING
Payment.CONFIRM_REQUESTED
```

이 변경이 중요하다. 고객의 결제 승인 요청을 접수한 것과 PG 승인이 끝나서 예약이 확정된 것은 다른 사건이다. 이전 코드는 둘을 같은 응답 의미로 묶고 있었다.

## PaymentOutboxMessage가 가지는 정보

새로운 Outbox 메시지는 결제 승인 worker가 재시도할 수 있는 최소 정보를 가진다.

```kotlin
data class PaymentOutboxMessage(
    val id: String,
    val paymentId: String,
    val reservationId: String,
    val memberId: String,
    val type: PaymentOutboxType,
    val paymentKey: String,
    val orderId: String,
    val amount: Money,
    val idempotencyKey: String,
    val status: PaymentOutboxStatus,
    val retryCount: Int,
    val maxRetries: Int,
    val nextRetryAt: Instant?,
    val lockedBy: String?,
    val lockedUntil: Instant?,
    val lastError: String?
)
```

멱등성 키는 고정 규칙으로 만들었다.

```text
payment-confirm:{paymentId}:{orderId}
```

같은 결제 승인 작업을 worker가 다시 처리해도 PG에는 같은 `Idempotency-Key`가 전달된다.

## Worker는 외부 호출과 복구를 담당한다

`PaymentOutboxProcessor`는 처리 가능한 Outbox를 조회하고 lease를 잡는다.

```text
PENDING
  -> IN_PROGRESS
  -> COMPLETED

PENDING
  -> IN_PROGRESS
  -> PENDING(nextRetryAt)

PENDING
  -> IN_PROGRESS
  -> SKIPPED

PENDING
  -> IN_PROGRESS
  -> FAILED
```

worker가 서버 다운으로 중간에 죽으면 `lockedUntil`이 지난 뒤 다른 worker가 다시 가져갈 수 있다. 이때도 PG 요청에는 같은 멱등성 키가 들어간다.

성공 흐름은 단순하다.

```text
1. paymentGateway.confirm(..., idempotencyKey)
2. Payment.APPROVED 저장
3. Reservation.CONFIRMED 저장
4. Outbox.COMPLETED 저장
```

복구 흐름은 조금 다르다.

```text
AlreadyProcessed
  -> paymentGateway.inquire(paymentKey)
  -> DONE이면 Payment.APPROVED + Reservation.CONFIRMED 복구

ProviderError
  -> paymentGateway.inquire(paymentKey)
  -> DONE이면 복구
  -> DONE이 아니거나 조회 실패면 Outbox를 재시도 상태로 되돌림

PaymentDeclined
  -> Payment.FAILED
  -> Outbox.COMPLETED

Reservation.CANCELLED 또는 Payment.FAILED
  -> PG 호출하지 않음
  -> Outbox.SKIPPED
```

여기서 `ProviderError` 뒤 조회 결과가 `DONE`이 아닐 때 바로 `Payment.FAILED`로 확정하지 않았다. PG 장애 직후의 외부 상태는 아직 불명확할 수 있기 때문이다. 안정성이 더 중요한 결제 흐름에서는 조기 실패 확정보다 Outbox 재시도가 더 보수적인 선택이라고 판단했다.

## 변경 후 얻은 결과

가장 큰 변화는 `CustomerReservationApplication`의 트랜잭션 안에서 외부 API 호출이 사라졌다는 점이다.

```text
변경 전 트랜잭션
Reservation 조회
Payment 조회
PG 승인 호출
Payment.APPROVED 저장
Reservation.CONFIRMED 저장

변경 후 트랜잭션
Reservation 조회
Payment 조회
Payment.CONFIRM_REQUESTED 저장
PaymentOutboxMessage 저장
```

이제 트랜잭션은 MongoDB에 저장되는 상태만 다룬다. 외부 PG 호출은 Outbox worker가 담당한다.

두 번째 변화는 API 의미가 정직해졌다는 점이다.

결제 승인 요청을 받았다고 해서 즉시 예약이 확정되는 것은 아니다. 그래서 HTTP 응답도 `202 Accepted`가 더 맞다. 클라이언트는 이후 상태 조회나 별도 완료 알림을 통해 `CONFIRMED/APPROVED`를 확인해야 한다.

세 번째 변화는 장애 복구 기준이 생겼다는 점이다.

서버가 죽어도 MongoDB에 `PaymentOutboxMessage`가 남는다. worker는 이 메시지를 기준으로 다시 PG 승인, 조회, 재시도를 수행할 수 있다.

## 테스트에서 다룬 엣지 케이스

이번 변경은 테스트를 기준으로 진행했다.

### 단위 테스트

`PaymentTest`에서는 `Payment` 상태 전이를 검증했다.

```text
PENDING -> CONFIRM_REQUESTED
CONFIRM_REQUESTED -> APPROVED
CONFIRM_REQUESTED -> FAILED
CONFIRM_REQUESTED 상태에서 같은 paymentKey 재요청
CONFIRM_REQUESTED 상태에서 다른 paymentKey 재요청 거부
```

`PaymentOutboxMessageTest`에서는 Outbox 자체의 도메인 규칙을 검증했다.

```text
결제 승인 Outbox 생성 시 PENDING 상태와 고정 idempotencyKey를 가진다.
paymentKey가 공백이면 생성할 수 없다.
PENDING 메시지는 IN_PROGRESS로 처리 시작할 수 있다.
IN_PROGRESS 메시지는 COMPLETED로 완료할 수 있다.
처리 실패 시 retryCount가 증가하고 PENDING으로 돌아간다.
최대 재시도 횟수에 도달하면 FAILED가 된다.
worker가 죽어서 lease가 만료되면 다른 worker가 다시 처리할 수 있다.
```

`PaymentOutboxProcessorTest`에서는 외부 PG 호출 주변의 엣지 케이스를 다뤘다.

```text
PG 승인 성공
  -> Payment.APPROVED
  -> Reservation.CONFIRMED
  -> Outbox.COMPLETED

PG가 이미 처리됐다고 응답하고 조회 결과 DONE
  -> 내부 Payment/Reservation 상태 복구
  -> Outbox.COMPLETED

PG가 재시도 가능한 오류를 반환하고 조회도 실패
  -> Payment/Reservation 유지
  -> Outbox.PENDING + retryCount 증가

PG가 재시도 가능한 오류를 반환하고 조회 결과가 DONE이 아님
  -> Payment.FAILED로 조기 확정하지 않음
  -> Outbox.PENDING + retryCount 증가

예약이 이미 취소된 상태
  -> PG 호출하지 않음
  -> Outbox.SKIPPED
```

`CustomerReservationApplicationTest`에서는 애플리케이션 유스케이스가 더 이상 PG를 직접 호출하지 않는지 검증했다.

```text
정상 결제 승인 요청
  -> Payment.CONFIRM_REQUESTED
  -> Reservation.PENDING
  -> Outbox.PENDING 생성
  -> paymentGateway.confirm 호출 없음

이미 Outbox가 있는 중복 요청
  -> Outbox 중복 생성 없음
  -> paymentGateway.confirm 호출 없음

만료, 금액 불일치, orderId 불일치
  -> Outbox 생성 없음
  -> paymentGateway.confirm 호출 없음
```

`CustomerReservationApiTest`에서는 HTTP 응답 의미를 검증했다.

```text
POST /api/v1/customer/reservations/{reservationId}/confirm-payment
  -> 202 Accepted
  -> reservationStatus = PENDING
  -> paymentStatus = CONFIRM_REQUESTED
```

`TossPaymentsClientTest`에서는 외부 요청 헤더를 검증했다.

```text
confirm 요청 시 Idempotency-Key 헤더가 전달된다.
```

### E2E 테스트

`CustomerReservationE2ETest`에서는 실제 애플리케이션 흐름을 MongoDB/Testcontainers 기반으로 확인했다.

```text
예약 생성
  -> Reservation.PENDING
  -> Payment.PENDING

결제 승인 요청
  -> Reservation.PENDING
  -> Payment.CONFIRM_REQUESTED
  -> PaymentOutboxMessage 저장

PaymentOutboxProcessor 실행
  -> PG confirm 호출
  -> Payment.APPROVED
  -> Reservation.CONFIRMED

예약 취소 요청
  -> Reservation.CANCELLED
  -> Payment.CANCEL_REQUESTED

PaymentOutboxProcessor 실행
  -> PG cancel 호출
  -> Payment.CANCELLED
```

멱등성 흐름도 확인했다.

```text
첫 번째 confirmPayment 호출
  -> 승인 요청 접수

worker 처리
  -> 결제 승인 및 예약 확정

두 번째 confirmPayment 호출
  -> 이미 확정된 예약/결제를 반환
  -> PG 중복 호출 없음
```

재고 정합성도 worker 처리 이후 기준으로 확인했다.

```text
예약 생성 시 재고 차감
결제 승인 worker 처리 후 예약 확정
예약 취소 요청 시 재고 복원
결제 취소 worker 처리 후 Payment.CANCELLED
```

## 취소/환불에도 같은 문제를 적용했다

결제 승인 Outbox를 적용한 뒤 남은 질문은 자연스럽게 취소/환불이었다.

기존 취소 흐름은 승인 흐름과 같은 문제를 갖고 있었다.

```kotlin
@Transactional
fun cancelReservation(memberId: String, reservationId: String): CustomerReservationResult {
    val reservation = reservationRepository.findById(reservationId)
        ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
    val payment = paymentRepository.findByReservationId(reservationId)
        ?: throw NotFoundException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: $reservationId")

    val cancelledReservation = reservationRepository.save(reservation.cancel())
    val cancelledPayment = try {
        paymentGateway.cancel(payment.paymentKey!!, "고객 요청에 의한 취소")
        paymentRepository.save(payment.cancel())
    } catch (e: PaymentGatewayException) {
        paymentRepository.save(payment.failCancel("환불 실패: ${e.message}"))
    }

    return CustomerReservationResult(cancelledReservation, cancelledPayment)
}
```

예약 취소도 내부 DB 상태와 외부 PG 상태가 함께 움직인다.

```text
내부 상태 변경
  -> Reservation.CANCELLED
  -> Payment.CANCELLED 또는 CANCEL_FAILED

외부 상태 변경
  -> paymentGateway.cancel(...)
```

여기서도 dual write 문제가 생긴다.

```text
Reservation.CANCELLED 저장 성공 + PG cancel 실패
  -> 고객 예약은 취소됐지만 결제는 환불되지 않을 수 있다.

PG cancel 성공 + Payment.CANCELLED 저장 실패
  -> 실제 환불은 됐지만 내부 결제 상태가 APPROVED 또는 CANCEL_REQUESTED로 남을 수 있다.

PG cancel timeout
  -> 실제 취소됐는지 응답만으로 판단하기 어렵다.

서버 다운
  -> 취소해야 한다는 작업 자체가 메모리에서 사라질 수 있다.
```

그래서 취소/환불도 `PaymentOutboxType.CANCEL_PAYMENT`로 분리했다.

## 취소/환불 개선 후 흐름

변경 후 `cancelReservation()`은 PG를 직접 호출하지 않는다.

```kotlin
val cancelledReservation = reservationRepository.save(reservation.cancel())

val requestedPayment = payment.requestCancel()
val cancelledPayment = paymentRepository.save(requestedPayment)

paymentOutboxRepository.save(
    PaymentOutboxMessage.createCancel(
        id = idGenerator.generate(),
        paymentId = cancelledPayment.id,
        reservationId = reservation.id,
        memberId = memberId,
        paymentKey = cancelledPayment.paymentKey!!,
        orderId = cancelledPayment.orderId,
        amount = cancelledPayment.amount,
        cancelReason = "고객 요청에 의한 취소",
        now = clock.instant()
    )
)
```

응답 의미도 바뀐다.

```text
변경 전
Reservation.CANCELLED
Payment.CANCELLED

변경 후
Reservation.CANCELLED
Payment.CANCEL_REQUESTED
```

예약 취소와 결제 취소를 같은 완료 시점으로 보지 않기로 했다. 숙박 예약 취소는 먼저 반영하되, 외부 PG 취소/환불은 Outbox worker가 멱등하게 처리한다.

이 선택은 현재 정책과 맞다. 기존 코드도 PG 환불이 실패하더라도 예약은 이미 취소된 상태로 남기고 `Payment.CANCEL_FAILED`를 기록했다. 즉 “환불 실패가 예약 취소를 rollback한다”는 정책이 아니었다. 따라서 `Reservation.CANCELLATION_REQUESTED` 같은 중간 예약 상태를 새로 만들기보다, `Payment.CANCEL_REQUESTED`를 두고 결제 취소 작업만 비동기로 분리하는 편이 더 작다.

`PaymentOutboxMessage.createCancel()`은 다음 값을 저장한다.

```text
type = CANCEL_PAYMENT
cancelReason = 고객 요청에 의한 취소
idempotencyKey = payment-cancel:{paymentId}:{orderId}
```

`PaymentOutboxProcessor`는 `CANCEL_PAYMENT`를 만나면 다음처럼 처리한다.

```text
PG cancel 성공
  -> Payment.CANCELLED
  -> Outbox.COMPLETED

PG가 이미 취소됐다고 응답
  -> Payment.CANCELLED
  -> Outbox.COMPLETED

PG 장애 또는 알 수 없는 오류
  -> Payment.CANCEL_REQUESTED 유지
  -> Outbox.PENDING + retryCount 증가

최대 재시도 도달
  -> Payment.CANCEL_FAILED
  -> Outbox.FAILED

Payment가 이미 CANCELLED
  -> PG 호출하지 않음
  -> Outbox.COMPLETED
```

Toss Payments cancel 요청에도 confirm과 동일하게 `Idempotency-Key`를 전달하도록 변경했다.

## 취소/환불 테스트에서 추가로 다룬 엣지 케이스

`PaymentTest`에는 취소 요청 상태 전이를 추가했다.

```text
APPROVED -> CANCEL_REQUESTED
CANCEL_REQUESTED -> CANCELLED
CANCEL_REQUESTED -> CANCEL_FAILED
CANCEL_REQUESTED 상태에서 requestCancel() 재호출
```

`PaymentOutboxMessageTest`에는 취소 Outbox 생성 규칙을 추가했다.

```text
createCancel()
  -> type = CANCEL_PAYMENT
  -> status = PENDING
  -> idempotencyKey = payment-cancel:{paymentId}:{orderId}
  -> cancelReason 필수
```

`PaymentOutboxProcessorTest`에는 취소 worker edge case를 추가했다.

```text
PG 취소 성공
  -> Payment.CANCELLED
  -> Outbox.COMPLETED

PG가 이미 취소됐다고 응답
  -> Payment.CANCELLED
  -> Outbox.COMPLETED

PG 취소가 재시도 가능한 오류를 반환
  -> Payment.CANCEL_REQUESTED 유지
  -> Payment.CANCEL_FAILED로 조기 확정하지 않음
  -> Outbox.PENDING + retryCount 증가

Payment가 이미 CANCELLED
  -> PG 호출 없음
  -> Outbox.COMPLETED
```

`CustomerReservationApplicationTest`에는 예약 취소 유스케이스의 책임 변경을 추가했다.

```text
PENDING 예약 취소
  -> Toss 환불 없음
  -> Payment.FAILED
  -> Reservation.CANCELLED

CONFIRMED 예약 취소
  -> Reservation.CANCELLED
  -> Payment.CANCEL_REQUESTED
  -> Cancel Outbox 생성
  -> paymentGateway.cancel 직접 호출 없음

Cancel Outbox가 이미 있음
  -> Outbox 중복 생성 없음
  -> paymentGateway.cancel 직접 호출 없음
```

`TossPaymentsClientTest`에는 cancel 요청의 멱등성 헤더 검증을 추가했다.

```text
cancel 요청 시 Idempotency-Key 헤더가 전달된다.
```

`CustomerReservationE2ETest`는 취소 흐름을 worker 기준으로 바꿨다.

```text
예약 생성
결제 승인 요청
결제 승인 worker 처리
예약 취소 요청
  -> Reservation.CANCELLED
  -> Payment.CANCEL_REQUESTED
결제 취소 worker 처리
  -> Payment.CANCELLED
재고 복원 확인
```

변경 전 기준선으로 `./gradlew test`를 실행했고 통과했다. 변경 후에는 RED 확인을 거쳐 `./gradlew :test`, `./gradlew :test --tests '*CustomerReservationE2ETest'`, `./gradlew test`를 실행했고 모두 통과했다.

## 웹훅 처리 이력과 인덱스에 대해 다시 확인한 것

Outbox와 웹훅 처리를 추가한 뒤, `TTL Index`, `Unique Index`, 웹훅 처리 이력, 메시지 큐의 역할을 다시 짚었다. 처음에는 인덱스를 단순한 성능 최적화로만 이해하기 쉬웠다. 하지만 현재 결제 흐름에서 인덱스는 성능보다 정합성 보조 장치에 가깝다.

결제 웹훅 처리 이력은 MongoDB의 `processed_payment_webhook_events` 컬렉션에 저장한다.

```kotlin
@Document("processed_payment_webhook_events")
data class ProcessedPaymentWebhookEventDocument(
    @Id val id: String,
    val transmissionId: String,
    val eventType: String,
    val paymentKey: String,
    val orderId: String,
    val processedAt: Instant
)
```

이 이력은 "이 외부 이벤트를 이미 처리했다"는 기록이다. PG 웹훅은 네트워크 문제, 응답 지연, 서버 장애, 외부 시스템의 재시도 정책 때문에 같은 이벤트가 다시 들어올 수 있다. 같은 웹훅을 매번 처리하면 결제 승인 Outbox가 중복 생성되거나, 같은 상태 변경이 반복될 수 있다. 그래서 `transmissionId`를 기준으로 처리 이력을 저장하고, 같은 `transmissionId`가 다시 들어오면 중복 이벤트로 판단한다.

```kotlin
if (
    command.transmissionId != null &&
    processedWebhookEventRepository.existsByTransmissionId(command.transmissionId)
) {
    return
}
```

단순히 애플리케이션에서 먼저 조회하는 것만으로는 충분하지 않다. 동시에 같은 웹훅 요청이 두 개 들어오면 둘 다 "아직 없다"고 판단할 수 있다. 그래서 MongoDB에 `transmissionId` unique index를 둔다.

```kotlin
Index().on("transmissionId", Sort.Direction.ASC).unique()
```

이 unique index의 역할은 성능 최적화만이 아니다. 같은 `transmissionId`가 동시에 저장되는 상황에서 DB가 마지막으로 중복 저장을 막는다. 즉, 결제 웹훅 처리에서는 unique index를 "동시성 상황에서 중복 처리를 막는 DB 레벨의 안전장치"로 본다.

TTL index는 다른 역할이다.

```kotlin
Index().on("processedAt", Sort.Direction.ASC).expire(Duration.ofDays(7))
```

TTL index는 오래된 웹훅 처리 이력을 자동 삭제하기 위한 장치다. 처리 이력을 영구 보관할 필요는 없지만, 외부 PG의 재전송 기간과 장애 조사 시간을 고려하면 일정 기간은 남겨야 한다. 토스페이먼츠 웹훅은 200 응답을 받지 못하면 최대 7회, 최초 전송 기준 약 3일 19시간 후까지 재전송될 수 있다. 따라서 7일 TTL은 재전송 기간만 보면 다소 보수적이지만, 장애 조사와 수동 확인 여유까지 고려하면 결제 흐름에서는 납득 가능한 값으로 판단했다.

다만 인덱스 전체를 같은 기준으로 보면 안 된다. 조회 성능용 인덱스는 데이터가 적을 때 체감이 낮을 수 있다. 반면 unique index는 데이터 수와 무관하게 중복 저장 방지를 위한 정합성 장치다. TTL index도 성능 최적화라기보다 운영 자동화 장치다.

정리하면 다음과 같다.

```text
Unique Index
  -> 중복 저장 방지
  -> 동시 요청에서 DB 레벨의 마지막 방어선

TTL Index
  -> 오래된 처리 이력 자동 삭제
  -> 별도 삭제 스케줄러 없이 운영 데이터 정리

일반 조회 인덱스
  -> worker가 반복 조회하는 조건의 성능 보조
  -> 데이터가 적으면 체감은 낮을 수 있음
```

메시지 큐와 Outbox의 역할도 다시 구분했다. 프로덕션에서 메시지 큐가 Outbox를 완전히 대신한다고 보기는 어렵다. Outbox의 핵심은 비즈니스 상태 변경과 외부로 내보낼 작업 기록을 같은 저장소의 같은 트랜잭션에 남기는 것이다. 메시지 큐는 그 이후 메시지를 전달하고 소비자를 분리하는 계층이다.

```text
비즈니스 상태 변경
  + Outbox 저장
  -> 같은 DB transaction

Outbox worker 또는 CDC
  -> 메시지 브로커 발행 또는 외부 API 호출

Kafka / SQS / RabbitMQ / Service Bus
  -> 전달, 부하 분산, 소비자 분리
```

따라서 현재 구조에서 `payment_outbox_messages` 컬렉션은 Outbox 역할을 한다. 나중에 처리량이나 시스템 분리가 필요해지면 MongoDB Outbox 뒤에 Kafka, SQS 같은 메시지 브로커 또는 Debezium CDC를 붙이는 방향으로 확장할 수 있다. 하지만 지금 단계에서 메시지 큐를 먼저 두고 MongoDB 상태 변경과 큐 발행을 따로 수행하면 다시 dual write 문제가 생길 수 있으므로, MongoDB Outbox를 먼저 두는 판단을 유지한다.

## 참고한 문서

- AWS Prescriptive Guidance, Transactional outbox pattern: https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html
- 토스페이먼츠 개발자센터, 인증 및 기타 헤더 설정: https://docs.tosspayments.com/reference/using-api/authorization
- 토스페이먼츠 개발자센터, 멱등성이 뭔가요?: https://docs.tosspayments.com/blog/what-is-idempotency
- 토스페이먼츠 개발자센터, 웹훅 연결하기: https://docs.tosspayments.com/guides/v2/webhook
- MongoDB Manual, TTL Indexes: https://www.mongodb.com/docs/manual/core/index-ttl/
- MongoDB Manual, Transactions: https://www.mongodb.com/docs/manual/core/transactions/
- Spring Data MongoDB, Client Sessions & Transactions: https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html
- Testcontainers for Java, MongoDB Module: https://java.testcontainers.org/modules/databases/mongodb/
