package com.stayops.reservation.domain.model

import com.stayops.shared.domain.DateRange
import java.time.Instant

class ReservationIntent private constructor(
    val id: String,
    val memberId: String,
    val propertyId: String,
    val roomTypeId: String,
    val guestInfo: GuestInfo,
    val dateRange: DateRange,
    val nightCount: Int,
    val numberOfGuests: Int,
    val channel: ReservationChannel,
    val pricing: ReservationPricing,
    val paymentId: String,
    val holdId: String,
    val reservationId: String?,
    val status: ReservationIntentStatus,
    val expiresAt: Instant,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    fun requestPaymentConfirmation(now: Instant = Instant.now()): ReservationIntent {
        check(status == ReservationIntentStatus.PAYMENT_WAITING) {
            "PAYMENT_WAITING 상태에서만 결제 승인 요청을 시작할 수 있습니다: $status"
        }
        check(!now.isAfter(expiresAt)) {
            "만료된 예약 intent는 결제 승인 요청을 시작할 수 없습니다: expiresAt=$expiresAt"
        }
        return copyState(status = ReservationIntentStatus.CONFIRM_REQUESTED, updatedAt = now)
    }

    fun markReserved(reservationId: String, now: Instant = Instant.now()): ReservationIntent {
        require(reservationId.isNotBlank()) { "reservationId는 필수입니다" }
        check(status == ReservationIntentStatus.CONFIRM_REQUESTED) {
            "CONFIRM_REQUESTED 상태에서만 최종 예약을 연결할 수 있습니다: $status"
        }
        return copyState(
            status = ReservationIntentStatus.RESERVED,
            reservationId = reservationId,
            updatedAt = now
        )
    }

    fun expire(now: Instant = Instant.now()): ReservationIntent {
        check(status == ReservationIntentStatus.PAYMENT_WAITING) {
            "PAYMENT_WAITING 상태에서만 만료 처리할 수 있습니다: $status"
        }
        check(now.isAfter(expiresAt)) {
            "만료 시간이 지나지 않은 예약 intent는 만료 처리할 수 없습니다: expiresAt=$expiresAt"
        }
        return copyState(status = ReservationIntentStatus.EXPIRED, updatedAt = now)
    }

    fun failPayment(reason: String, now: Instant = Instant.now()): ReservationIntent {
        require(reason.isNotBlank()) { "실패 사유는 필수입니다" }
        check(status == ReservationIntentStatus.PAYMENT_WAITING || status == ReservationIntentStatus.CONFIRM_REQUESTED) {
            "결제 대기 또는 승인 요청 상태에서만 결제 실패 처리할 수 있습니다: $status"
        }
        return copyState(status = ReservationIntentStatus.PAYMENT_FAILED, updatedAt = now)
    }

    fun requestCancel(now: Instant = Instant.now()): ReservationIntent {
        check(status == ReservationIntentStatus.RESERVED) {
            "RESERVED 상태에서만 취소 요청할 수 있습니다: $status"
        }
        return copyState(status = ReservationIntentStatus.CANCEL_REQUESTED, updatedAt = now)
    }

    fun cancel(now: Instant = Instant.now()): ReservationIntent {
        check(status == ReservationIntentStatus.CANCEL_REQUESTED || status == ReservationIntentStatus.RESERVED) {
            "RESERVED 또는 CANCEL_REQUESTED 상태에서만 취소 완료할 수 있습니다: $status"
        }
        return copyState(status = ReservationIntentStatus.CANCELLED, updatedAt = now)
    }

    private fun copyState(
        reservationId: String? = this.reservationId,
        status: ReservationIntentStatus = this.status,
        updatedAt: Instant = Instant.now()
    ): ReservationIntent = ReservationIntent(
        id = id,
        memberId = memberId,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        guestInfo = guestInfo,
        dateRange = dateRange,
        nightCount = nightCount,
        numberOfGuests = numberOfGuests,
        channel = channel,
        pricing = pricing,
        paymentId = paymentId,
        holdId = holdId,
        reservationId = reservationId,
        status = status,
        expiresAt = expiresAt,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReservationIntent

        return id == other.id
    }

    override fun hashCode(): Int = 31 * javaClass.hashCode() + id.hashCode()

    override fun toString(): String = "ReservationIntent(id=$id, status=$status)"

    companion object {
        fun create(
            id: String,
            memberId: String,
            propertyId: String,
            roomTypeId: String,
            guestInfo: GuestInfo,
            dateRange: DateRange,
            numberOfGuests: Int,
            channel: ReservationChannel,
            pricing: ReservationPricing,
            paymentId: String,
            holdId: String,
            expiresAt: Instant,
            now: Instant = Instant.now()
        ): ReservationIntent {
            require(id.isNotBlank()) { "id는 필수입니다" }
            require(memberId.isNotBlank()) { "memberId는 필수입니다" }
            require(propertyId.isNotBlank()) { "propertyId는 필수입니다" }
            require(roomTypeId.isNotBlank()) { "roomTypeId는 필수입니다" }
            require(numberOfGuests >= 1) { "투숙 인원은 1명 이상이어야 합니다: $numberOfGuests" }
            require(paymentId.isNotBlank()) { "paymentId는 필수입니다" }
            require(holdId.isNotBlank()) { "holdId는 필수입니다" }
            require(expiresAt.isAfter(now)) { "만료 시간은 생성 시간 이후여야 합니다: expiresAt=$expiresAt" }

            return ReservationIntent(
                id = id,
                memberId = memberId,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                guestInfo = guestInfo,
                dateRange = dateRange,
                nightCount = dateRange.nights().toInt(),
                numberOfGuests = numberOfGuests,
                channel = channel,
                pricing = pricing,
                paymentId = paymentId,
                holdId = holdId,
                reservationId = null,
                status = ReservationIntentStatus.PAYMENT_WAITING,
                expiresAt = expiresAt,
                version = 0L,
                createdAt = now,
                updatedAt = now
            )
        }

        fun reconstitute(
            id: String,
            memberId: String,
            propertyId: String,
            roomTypeId: String,
            guestInfo: GuestInfo,
            dateRange: DateRange,
            nightCount: Int,
            numberOfGuests: Int,
            channel: ReservationChannel,
            pricing: ReservationPricing,
            paymentId: String,
            holdId: String,
            reservationId: String?,
            status: ReservationIntentStatus,
            expiresAt: Instant,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): ReservationIntent = ReservationIntent(
            id = id,
            memberId = memberId,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            guestInfo = guestInfo,
            dateRange = dateRange,
            nightCount = nightCount,
            numberOfGuests = numberOfGuests,
            channel = channel,
            pricing = pricing,
            paymentId = paymentId,
            holdId = holdId,
            reservationId = reservationId,
            status = status,
            expiresAt = expiresAt,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
