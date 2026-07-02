package com.stayops.inventory.application.service

import com.stayops.inventory.application.provided.InventoryHoldService
import com.stayops.inventory.application.provided.InventoryHoldSnapshot
import com.stayops.inventory.domain.model.InventoryHold
import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class RoomInventoryHoldApplication(
    private val inventoryAccess: RoomInventoryAccessApplication,
    private val inventoryHoldRepository: InventoryHoldRepository,
    private val clock: Clock
) : InventoryHoldService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun hold(
        holdId: String,
        reservationIntentId: String,
        propertyId: String,
        roomTypeId: String,
        dateRange: DateRange,
        quantity: Int,
        expiresAt: Instant
    ): InventoryHoldSnapshot {
        require(quantity >= 1) { "hold 수량은 1 이상이어야 합니다: $quantity" }

        val dates = dateRange.allDates()
        dates.forEach { date ->
            val held = (1..quantity).fold(inventoryAccess.getOrThrow(propertyId, roomTypeId, date)) { inventory, _ ->
                inventory.hold()
            }
            inventoryAccess.saveAndEvict(held)
        }

        val hold = inventoryHoldRepository.save(
            InventoryHold.create(
                id = holdId,
                reservationIntentId = reservationIntentId,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                dates = dates,
                quantity = quantity,
                expiresAt = expiresAt,
                now = clock.instant()
            )
        )

        log.info(
            "재고 hold 생성: holdId={}, reservationIntentId={}, propertyId={}, roomTypeId={}, quantity={}",
            hold.id,
            reservationIntentId,
            propertyId,
            roomTypeId,
            quantity
        )
        return hold.toSnapshot()
    }

    @Transactional
    override fun consume(reservationIntentId: String) {
        val hold = inventoryHoldRepository.findByReservationIntentId(reservationIntentId)
            ?: throw NotFoundException("INVENTORY_HOLD_NOT_FOUND", "재고 hold를 찾을 수 없습니다: $reservationIntentId")

        if (hold.status == InventoryHoldStatus.CONSUMED) {
            return
        }

        val now = clock.instant()
        val processing = if (hold.status == InventoryHoldStatus.PAYMENT_PROCESSING) {
            hold
        } else {
            inventoryHoldRepository.save(hold.startPaymentProcessing(now))
        }

        processing.dates.forEach { date ->
            val consumed = (1..processing.quantity).fold(
                inventoryAccess.getOrThrow(processing.propertyId, processing.roomTypeId, date)
            ) { inventory, _ ->
                inventory.consumeHold()
            }
            inventoryAccess.saveAndEvict(consumed)
        }

        inventoryHoldRepository.save(processing.consume(now))
        log.info(
            "재고 hold 소비: holdId={}, reservationIntentId={}",
            processing.id,
            reservationIntentId
        )
    }

    @Transactional
    override fun release(reservationIntentId: String) {
        val hold = inventoryHoldRepository.findByReservationIntentId(reservationIntentId)
            ?: throw NotFoundException("INVENTORY_HOLD_NOT_FOUND", "재고 hold를 찾을 수 없습니다: $reservationIntentId")

        if (hold.status == InventoryHoldStatus.RELEASED || hold.status == InventoryHoldStatus.EXPIRED) {
            return
        }
        check(hold.status == InventoryHoldStatus.HELD) {
            "HELD 상태의 hold만 해제할 수 있습니다: ${hold.status}"
        }

        hold.dates.forEach { date ->
            val released = (1..hold.quantity).fold(
                inventoryAccess.getOrThrow(hold.propertyId, hold.roomTypeId, date)
            ) { inventory, _ ->
                inventory.releaseHold()
            }
            inventoryAccess.saveAndEvict(released)
        }

        inventoryHoldRepository.save(hold.release(clock.instant()))
        log.info(
            "재고 hold 해제: holdId={}, reservationIntentId={}",
            hold.id,
            reservationIntentId
        )
    }

    private fun InventoryHold.toSnapshot(): InventoryHoldSnapshot =
        InventoryHoldSnapshot(
            id = id,
            reservationIntentId = reservationIntentId,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            dates = dates,
            quantity = quantity,
            expiresAt = expiresAt
        )
}
