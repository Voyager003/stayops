package com.stayops.payment.api

import com.stayops.payment.application.service.PaymentStatusChangedWebhookCommand
import com.stayops.payment.application.service.PaymentWebhookApplication
import com.stayops.shared.exception.GlobalExceptionHandler
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal

class PaymentWebhookApiTest {

    private val paymentWebhookApplication = mockk<PaymentWebhookApplication>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(PaymentWebhookApi(paymentWebhookApplication))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    @Test
    fun `Toss 결제 상태 변경 웹훅이면 200을 반환하고 Application으로 위임한다`() {
        val command = slot<PaymentStatusChangedWebhookCommand>()
        every { paymentWebhookApplication.handleTossPaymentStatusChanged(capture(command)) } returns Unit

        mockMvc.post("/api/v1/payments/toss/webhooks") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "eventType": "PAYMENT_STATUS_CHANGED",
                    "createdAt": "2026-04-14T10:00:00.000000",
                    "data": {
                        "paymentKey": "toss_pk_123",
                        "orderId": "STAYOPS-rsv-1-123",
                        "status": "IN_PROGRESS",
                        "totalAmount": 200000
                    }
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
        }

        command.captured.eventType shouldBe "PAYMENT_STATUS_CHANGED"
        command.captured.paymentKey shouldBe "toss_pk_123"
        command.captured.orderId shouldBe "STAYOPS-rsv-1-123"
        command.captured.status shouldBe "IN_PROGRESS"
        command.captured.totalAmount shouldBe BigDecimal(200_000)
        verify(exactly = 1) { paymentWebhookApplication.handleTossPaymentStatusChanged(any()) }
    }
}
