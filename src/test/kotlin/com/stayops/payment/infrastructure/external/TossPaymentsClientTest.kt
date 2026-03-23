package com.stayops.payment.infrastructure.external

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.web.client.RestClient
import java.math.BigDecimal

class TossPaymentsClientTest : BehaviorSpec({

    val restClient = mockk<RestClient>()
    val requestBodyUriSpec = mockk<RestClient.RequestBodyUriSpec>()
    val requestBodySpec = mockk<RestClient.RequestBodySpec>(relaxed = true)
    val responseSpec = mockk<RestClient.ResponseSpec>()

    val client = TossPaymentsClient(restClient)

    given("결제 승인 시") {

        `when`("Toss가 성공 응답을 반환하면") {
            val tossResponse = TossConfirmResponse(
                paymentKey = "toss_pk_123",
                orderId = "STAYOPS-rsv-1-123",
                status = "DONE",
                method = "카드",
                totalAmount = BigDecimal(200_000),
                approvedAt = "2026-04-01T12:00:00+09:00"
            )
            every { restClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri("/confirm") } returns requestBodySpec
            every { requestBodySpec.body(any<TossConfirmRequest>()) } returns requestBodySpec
            every { requestBodySpec.retrieve() } returns responseSpec
            every { responseSpec.body(TossConfirmResponse::class.java) } returns tossResponse

            val result = client.confirm("toss_pk_123", "STAYOPS-rsv-1-123", BigDecimal(200_000))

            then("PaymentConfirmResult로 변환하여 반환한다") {
                result.paymentKey shouldBe "toss_pk_123"
                result.method shouldBe "카드"
                result.approvedAt shouldNotBe null
            }
        }
    }

    given("결제 취소 시") {

        `when`("Toss가 성공 응답을 반환하면") {
            val tossResponse = TossCancelResponse(
                paymentKey = "toss_pk_123",
                status = "CANCELED"
            )
            every { restClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri("/toss_pk_123/cancel") } returns requestBodySpec
            every { requestBodySpec.body(any<TossCancelRequest>()) } returns requestBodySpec
            every { requestBodySpec.retrieve() } returns responseSpec
            every { responseSpec.body(TossCancelResponse::class.java) } returns tossResponse

            val result = client.cancel("toss_pk_123", "고객 요청")

            then("PaymentCancelResult로 변환하여 반환한다") {
                result.paymentKey shouldBe "toss_pk_123"
            }
        }
    }
})
