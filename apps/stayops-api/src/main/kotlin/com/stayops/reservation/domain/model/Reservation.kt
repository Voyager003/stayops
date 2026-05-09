package com.stayops.reservation.domain.model

import com.stayops.shared.domain.DateRange
import java.time.Instant

@ConsistentCopyVisibility
data class Reservation private constructor(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val roomId: String?,
    val guestId: String,
    val guestInfo: GuestInfo,
    val dateRange: DateRange,
    val nightCount: Int,
    val numberOfGuests: Int,
    val status: ReservationStatus,
    val channel: ReservationChannel,
    val pricing: ReservationPricing,
    val memberId: String?,
    val expiresAt: Instant?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    fun confirm(): Reservation {
        check(status == ReservationStatus.PENDING) {
            "PENDING 상태에서만 확정할 수 있습니다: $status"
        }
        return copy(status = ReservationStatus.CONFIRMED, updatedAt = Instant.now())
    }

    fun checkIn(roomId: String): Reservation {
        check(status == ReservationStatus.CONFIRMED) {
            "CONFIRMED 상태에서만 체크인할 수 있습니다: $status"
        }
        return copy(
            status = ReservationStatus.CHECKED_IN,
            roomId = roomId,
            updatedAt = Instant.now()
        )
    }

    fun checkOut(): Reservation {
        check(status == ReservationStatus.CHECKED_IN) {
            "CHECKED_IN 상태에서만 체크아웃할 수 있습니다: $status"
        }
        return copy(status = ReservationStatus.CHECKED_OUT, updatedAt = Instant.now())
    }

    fun cancel(): Reservation {
        check(status == ReservationStatus.CONFIRMED) {
            "CONFIRMED 상태에서만 취소할 수 있습니다: $status"
        }
        return copy(status = ReservationStatus.CANCELLED, updatedAt = Instant.now())
    }

    fun cancelPending(): Reservation {
        check(status == ReservationStatus.PENDING) {
            "PENDING 상태에서만 취소할 수 있습니다: $status"
        }
        return copy(status = ReservationStatus.CANCELLED, updatedAt = Instant.now())
    }

    fun noShow(): Reservation {
        check(status == ReservationStatus.CONFIRMED) {
            "CONFIRMED 상태에서만 노쇼 처리할 수 있습니다: $status"
        }
        return copy(status = ReservationStatus.NO_SHOW, updatedAt = Instant.now())
    }

    companion object {
        fun create(
            id: String,
            propertyId: String,
            roomTypeId: String,
            guestId: String,
            guestInfo: GuestInfo,
            dateRange: DateRange,
            numberOfGuests: Int,
            channel: ReservationChannel,
            pricing: ReservationPricing,
            memberId: String? = null,
            expiresAt: Instant? = null
        ): Reservation {
            require(numberOfGuests >= 1) { "투숙 인원은 1명 이상이어야 합니다: $numberOfGuests" }

            val now = Instant.now()
            return Reservation(
                id = id,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                roomId = null,
                guestId = guestId,
                guestInfo = guestInfo,
                dateRange = dateRange,
                nightCount = dateRange.nights().toInt(),
                numberOfGuests = numberOfGuests,
                status = ReservationStatus.PENDING,
                channel = channel,
                pricing = pricing,
                memberId = memberId,
                expiresAt = expiresAt,
                version = 0L,
                createdAt = now,
                updatedAt = now
            )
        }

        fun reconstitute(
            id: String,
            propertyId: String,
            roomTypeId: String,
            roomId: String?,
            guestId: String,
            guestInfo: GuestInfo,
            dateRange: DateRange,
            nightCount: Int,
            numberOfGuests: Int,
            status: ReservationStatus,
            channel: ReservationChannel,
            pricing: ReservationPricing,
            memberId: String? = null,
            expiresAt: Instant? = null,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): Reservation = Reservation(
            id = id,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            roomId = roomId,
            guestId = guestId,
            guestInfo = guestInfo,
            dateRange = dateRange,
            nightCount = nightCount,
            numberOfGuests = numberOfGuests,
            status = status,
            channel = channel,
            pricing = pricing,
            memberId = memberId,
            expiresAt = expiresAt,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
