package com.stayops.e2e.payment

import com.ninjasquad.springmockk.MockkBean
import com.stayops.TestcontainersConfiguration
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.application.required.PaymentGateway
import com.stayops.payment.application.required.PaymentInquiryResult
import com.stayops.shared.domain.Money
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PaymentWebhookSecurityE2ETest @Autowired constructor(
    private val context: WebApplicationContext,
    private val paymentRepository: PaymentRepository,
    private val paymentOutboxRepository: PaymentOutboxRepository,
    private val mongoTemplate: MongoTemplate,
    @MockkBean private val paymentGateway: PaymentGateway
) {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mongoTemplate.collectionNames.forEach { name ->
            mongoTemplate.getCollection(name).deleteMany(org.bson.Document())
        }
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @Test
    fun `Toss 웹훅은 인증 없이 수신되고 승인 Outbox를 생성한다`() {
        val payment = paymentRepository.save(
            Payment.create(
                id = "pay-webhook-security",
                reservationId = "rsv-webhook-security",
                memberId = "member-webhook-security",
                amount = Money.won(200_000)
            )
        )
        every { paymentGateway.inquire("toss_pk_security") } returns PaymentInquiryResult(
            paymentKey = "toss_pk_security",
            orderId = payment.orderId,
            status = "IN_PROGRESS",
            totalAmount = BigDecimal(200_000)
        )

        mockMvc.post("/api/v1/payments/toss/webhooks") {
            contentType = MediaType.APPLICATION_JSON
            header("tosspayments-webhook-transmission-id", "tx-security-1")
            content = """
                {
                    "eventType": "PAYMENT_STATUS_CHANGED",
                    "createdAt": "2026-04-14T10:00:00.000000",
                    "data": {
                        "paymentKey": "toss_pk_security",
                        "orderId": "${payment.orderId}",
                        "status": "IN_PROGRESS",
                        "totalAmount": 200000
                    }
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
        }

        val updatedPayment = paymentRepository.findById(payment.id)!!
        val outbox = paymentOutboxRepository.findByPaymentIdAndType(payment.id, PaymentOutboxType.CONFIRM_PAYMENT)

        assertThat(updatedPayment.status).isEqualTo(PaymentStatus.CONFIRM_REQUESTED)
        assertThat(outbox).isNotNull
        assertThat(outbox!!.status).isEqualTo(PaymentOutboxStatus.PENDING)

        mockMvc.post("/api/v1/payments/toss/webhooks") {
            contentType = MediaType.APPLICATION_JSON
            header("tosspayments-webhook-transmission-id", "tx-security-1")
            content = """
                {
                    "eventType": "PAYMENT_STATUS_CHANGED",
                    "createdAt": "2026-04-14T10:00:00.000000",
                    "data": {
                        "paymentKey": "toss_pk_security",
                        "orderId": "${payment.orderId}",
                        "status": "IN_PROGRESS",
                        "totalAmount": 200000
                    }
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
        }

        val processedEvents = mongoTemplate.find(
            org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("transmissionId").`is`("tx-security-1")
            ),
            org.bson.Document::class.java,
            "processed_payment_webhook_events"
        )
        assertThat(processedEvents).hasSize(1)
        verify(exactly = 1) { paymentGateway.inquire("toss_pk_security") }
    }

    @Test
    fun `고객 예약 API는 인증 없이 접근할 수 없다`() {
        mockMvc.get("/api/v1/customer/reservations")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
