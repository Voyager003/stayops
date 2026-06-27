package com.stayops.inventory.application.service

import com.stayops.inventory.application.provided.InventoryHoldService
import com.stayops.inventory.application.provided.InventoryHoldSnapshot
import com.stayops.inventory.domain.model.InventoryHold
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class RoomInventoryHoldApplication(
    private val inventoryAccess: RoomInventoryAccessApplication,
    private val inventoryHoldRepository: InventoryHoldRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) : InventoryHoldService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun hold(
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
                id = idGenerator.generate(),
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
