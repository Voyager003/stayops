package com.stayops.payment.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.PAYMENTS
import com.stayops.jooq.generated.tables.records.PaymentsRecord
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.shared.config.RdbPersistence
import com.stayops.shared.domain.Money
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbPaymentRepository(
    private val dsl: DSLContext
) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        dsl.insertInto(PAYMENTS)
            .set(PAYMENTS.ID, payment.id)
            .set(PAYMENTS.RESERVATION_ID, payment.reservationId)
            .set(PAYMENTS.RESERVATION_INTENT_ID, payment.reservationIntentId)
            .set(PAYMENTS.MEMBER_ID, payment.memberId)
            .set(PAYMENTS.ORDER_ID, payment.orderId)
            .set(PAYMENTS.AMOUNT, payment.amount.amount)
            .set(PAYMENTS.CURRENCY, payment.amount.currency)
            .set(PAYMENTS.STATUS, payment.status.name)
            .set(PAYMENTS.PAYMENT_KEY, payment.paymentKey)
            .set(PAYMENTS.METHOD, payment.method)
            .set(PAYMENTS.FAIL_REASON, payment.failReason)
            .set(PAYMENTS.APPROVED_AT, payment.approvedAt?.toOffsetDateTime())
            .set(PAYMENTS.VERSION, payment.version)
            .set(PAYMENTS.CREATED_AT, payment.createdAt.toOffsetDateTime())
            .set(PAYMENTS.UPDATED_AT, payment.updatedAt.toOffsetDateTime())
            .onConflict(PAYMENTS.ID)
            .doUpdate()
            .set(PAYMENTS.RESERVATION_ID, payment.reservationId)
            .set(PAYMENTS.RESERVATION_INTENT_ID, payment.reservationIntentId)
            .set(PAYMENTS.MEMBER_ID, payment.memberId)
            .set(PAYMENTS.ORDER_ID, payment.orderId)
            .set(PAYMENTS.AMOUNT, payment.amount.amount)
            .set(PAYMENTS.CURRENCY, payment.amount.currency)
            .set(PAYMENTS.STATUS, payment.status.name)
            .set(PAYMENTS.PAYMENT_KEY, payment.paymentKey)
            .set(PAYMENTS.METHOD, payment.method)
            .set(PAYMENTS.FAIL_REASON, payment.failReason)
            .set(PAYMENTS.APPROVED_AT, payment.approvedAt?.toOffsetDateTime())
            .set(PAYMENTS.VERSION, payment.version)
            .set(PAYMENTS.CREATED_AT, payment.createdAt.toOffsetDateTime())
            .set(PAYMENTS.UPDATED_AT, payment.updatedAt.toOffsetDateTime())
            .execute()

        return findById(payment.id) ?: payment
    }

    override fun findById(id: String): Payment? =
        dsl.selectFrom(PAYMENTS)
            .where(PAYMENTS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    override fun findByReservationId(reservationId: String): Payment? =
        dsl.selectFrom(PAYMENTS)
            .where(PAYMENTS.RESERVATION_ID.eq(reservationId))
            .fetchOne()
            ?.toDomain()

    override fun findByReservationIds(reservationIds: List<String>): List<Payment> =
        if (reservationIds.isEmpty()) {
            emptyList()
        } else {
            findAll(PAYMENTS.RESERVATION_ID.`in`(reservationIds))
        }

    override fun findByMemberId(memberId: String): List<Payment> =
        findAll(PAYMENTS.MEMBER_ID.eq(memberId))

    override fun findByOrderId(orderId: String): Payment? =
        dsl.selectFrom(PAYMENTS)
            .where(PAYMENTS.ORDER_ID.eq(orderId))
            .fetchOne()
            ?.toDomain()

    private fun findAll(condition: Condition): List<Payment> =
        dsl.selectFrom(PAYMENTS)
            .where(condition)
            .orderBy(PAYMENTS.CREATED_AT.desc(), PAYMENTS.ID.asc())
            .fetch { record -> record.toDomain() }

    private fun PaymentsRecord.toDomain(): Payment =
        Payment.reconstitute(
            id = get(PAYMENTS.ID),
            reservationId = get(PAYMENTS.RESERVATION_ID),
            reservationIntentId = get(PAYMENTS.RESERVATION_INTENT_ID),
            memberId = get(PAYMENTS.MEMBER_ID),
            orderId = get(PAYMENTS.ORDER_ID),
            amount = Money.of(get(PAYMENTS.AMOUNT), get(PAYMENTS.CURRENCY)),
            status = PaymentStatus.valueOf(get(PAYMENTS.STATUS)),
            paymentKey = get(PAYMENTS.PAYMENT_KEY),
            method = get(PAYMENTS.METHOD),
            failReason = get(PAYMENTS.FAIL_REASON),
            approvedAt = get(PAYMENTS.APPROVED_AT)?.toInstant(),
            version = get(PAYMENTS.VERSION),
            createdAt = get(PAYMENTS.CREATED_AT).toInstant(),
            updatedAt = get(PAYMENTS.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
