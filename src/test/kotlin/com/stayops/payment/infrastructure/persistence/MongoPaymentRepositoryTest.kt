package com.stayops.payment.infrastructure.persistence

import com.stayops.IntegrationTestSupport
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class MongoPaymentRepositoryTest @Autowired constructor(
    private val paymentRepository: PaymentRepository,
    private val mongoDataRepository: PaymentMongoDataRepository
) : IntegrationTestSupport() {

    @BeforeEach
    fun setUp() {
        mongoDataRepository.deleteAll()
    }

    private fun newPayment(
        id: String = "pay-1",
        reservationId: String = "rsv-1",
        memberId: String = "member-1",
        amount: Long = 200_000
    ) = Payment.create(
        id = id,
        reservationId = reservationId,
        memberId = memberId,
        amount = Money.won(amount)
    )

    @Nested
    inner class `save_및_findById` {

        @Test
        fun `저장 후 모든 필드가 보존된 Payment를 조회한다`() {
            val payment = newPayment()
            paymentRepository.save(payment)

            val found = paymentRepository.findById("pay-1")

            assertThat(found).isNotNull
            assertThat(found!!.reservationId).isEqualTo("rsv-1")
            assertThat(found.memberId).isEqualTo("member-1")
            assertThat(found.amount).isEqualTo(Money.won(200_000))
            assertThat(found.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(found.orderId).startsWith("STAYOPS-rsv-1-")
        }

        @Test
        fun `APPROVED 상태로 변경 후 저장하면 상태가 보존된다`() {
            val saved = paymentRepository.save(newPayment())

            val approved = saved.approve(
                paymentKey = "toss_pk_123",
                method = "카드",
                approvedAt = Instant.parse("2026-04-01T12:00:00Z")
            )
            paymentRepository.save(approved)

            val found = paymentRepository.findById("pay-1")
            assertThat(found!!.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(found.paymentKey).isEqualTo("toss_pk_123")
            assertThat(found.method).isEqualTo("카드")
        }
    }

    @Nested
    inner class `findByReservationId` {

        @Test
        fun `reservationId로 Payment를 조회한다`() {
            paymentRepository.save(newPayment(id = "pay-1", reservationId = "rsv-1"))
            paymentRepository.save(newPayment(id = "pay-2", reservationId = "rsv-2"))

            val found = paymentRepository.findByReservationId("rsv-1")

            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo("pay-1")
        }
    }

    @Nested
    inner class `findByMemberId` {

        @Test
        fun `memberId로 Payment 목록을 조회한다`() {
            paymentRepository.save(newPayment(id = "pay-1", memberId = "member-1"))
            paymentRepository.save(newPayment(id = "pay-2", memberId = "member-1", reservationId = "rsv-2"))
            paymentRepository.save(newPayment(id = "pay-3", memberId = "member-2", reservationId = "rsv-3"))

            val result = paymentRepository.findByMemberId("member-1")

            assertThat(result).hasSize(2)
        }
    }

    @Nested
    inner class `findByOrderId` {

        @Test
        fun `orderId로 Payment를 조회한다`() {
            val payment = newPayment()
            paymentRepository.save(payment)

            val found = paymentRepository.findByOrderId(payment.orderId)

            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo("pay-1")
        }
    }
}
