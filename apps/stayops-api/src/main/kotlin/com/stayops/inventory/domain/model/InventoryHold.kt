package com.stayops.inventory.domain.model

import java.time.Instant
import java.time.LocalDate

class InventoryHold private constructor(
    val id: String,
    val reservationIntentId: String,
    val propertyId: String,
    val roomTypeId: String,
    val dates: List<LocalDate>,
    val quantity: Int,
    val status: InventoryHoldStatus,
    val expiresAt: Instant,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    fun startPaymentProcessing(now: Instant = Instant.now()): InventoryHold {
        check(status == InventoryHoldStatus.HELD) {
            "HELD 상태에서만 결제 처리 상태로 전환할 수 있습니다: $status"
        }
        check(!now.isAfter(expiresAt)) {
            "만료된 hold는 결제 처리 상태로 전환할 수 없습니다: expiresAt=$expiresAt"
        }
        return copyState(status = InventoryHoldStatus.PAYMENT_PROCESSING, updatedAt = now)
    }

    fun consume(now: Instant = Instant.now()): InventoryHold {
        check(status == InventoryHoldStatus.PAYMENT_PROCESSING) {
            "PAYMENT_PROCESSING 상태에서만 hold를 소비할 수 있습니다: $status"
        }
        return copyState(status = InventoryHoldStatus.CONSUMED, updatedAt = now)
    }

    fun release(now: Instant = Instant.now()): InventoryHold {
        check(status == InventoryHoldStatus.HELD || status == InventoryHoldStatus.PAYMENT_PROCESSING) {
            "HELD 또는 PAYMENT_PROCESSING 상태에서만 hold를 해제할 수 있습니다: $status"
        }
        return copyState(status = InventoryHoldStatus.RELEASED, updatedAt = now)
    }

    fun expire(now: Instant = Instant.now()): InventoryHold {
        check(status == InventoryHoldStatus.HELD) {
            "HELD 상태에서만 hold를 만료 처리할 수 있습니다: $status"
        }
        check(now.isAfter(expiresAt)) {
            "만료 시간이 지나지 않은 hold는 만료 처리할 수 없습니다: expiresAt=$expiresAt"
        }
        return copyState(status = InventoryHoldStatus.EXPIRED, updatedAt = now)
    }

    private fun copyState(
        status: InventoryHoldStatus = this.status,
        updatedAt: Instant = Instant.now()
    ): InventoryHold = InventoryHold(
        id = id,
        reservationIntentId = reservationIntentId,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        dates = dates,
        quantity = quantity,
        status = status,
        expiresAt = expiresAt,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InventoryHold

        return id == other.id
    }

    override fun hashCode(): Int = 31 * javaClass.hashCode() + id.hashCode()

    override fun toString(): String = "InventoryHold(id=$id, status=$status)"

    companion object {
        fun create(
            id: String,
            reservationIntentId: String,
            propertyId: String,
            roomTypeId: String,
            dates: List<LocalDate>,
            quantity: Int,
            expiresAt: Instant,
            now: Instant = Instant.now()
        ): InventoryHold {
            require(id.isNotBlank()) { "id는 필수입니다" }
            require(reservationIntentId.isNotBlank()) { "reservationIntentId는 필수입니다" }
            require(propertyId.isNotBlank()) { "propertyId는 필수입니다" }
            require(roomTypeId.isNotBlank()) { "roomTypeId는 필수입니다" }
            require(dates.isNotEmpty()) { "hold 날짜는 1개 이상이어야 합니다" }
            require(dates.distinct().size == dates.size) { "hold 날짜는 중복될 수 없습니다" }
            require(quantity >= 1) { "hold 수량은 1 이상이어야 합니다: $quantity" }
            require(expiresAt.isAfter(now)) { "만료 시간은 생성 시간 이후여야 합니다: expiresAt=$expiresAt" }

            return InventoryHold(
                id = id,
                reservationIntentId = reservationIntentId,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                dates = dates.sorted(),
                quantity = quantity,
                status = InventoryHoldStatus.HELD,
                expiresAt = expiresAt,
                version = 0L,
                createdAt = now,
                updatedAt = now
            )
        }

        fun reconstitute(
            id: String,
            reservationIntentId: String,
            propertyId: String,
            roomTypeId: String,
            dates: List<LocalDate>,
            quantity: Int,
            status: InventoryHoldStatus,
            expiresAt: Instant,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): InventoryHold = InventoryHold(
            id = id,
            reservationIntentId = reservationIntentId,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            dates = dates.sorted(),
            quantity = quantity,
            status = status,
            expiresAt = expiresAt,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
