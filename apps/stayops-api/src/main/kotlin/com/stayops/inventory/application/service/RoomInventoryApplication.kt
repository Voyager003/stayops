package com.stayops.inventory.application.service

import com.stayops.inventory.application.port.AvailabilitySyncPort
import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.inventory.application.port.RoomInventorySyncPort
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryCache
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate

@Service
class RoomInventoryApplication(
    private val inventoryRepository: RoomInventoryRepository,
    private val cache: RoomInventoryCache,
    private val roomRepository: RoomRepository,
    private val availabilitySyncPort: AvailabilitySyncPort,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) : InventoryReservationPort, RoomInventorySyncPort {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val INVENTORY_HORIZON_DAYS = 90L
    }

    override fun syncInventoryForRoomType(propertyId: String, roomTypeId: String) {
        val roomCount = roomRepository.findByRoomTypeId(roomTypeId)
            .count { it.propertyId == propertyId }
        if (roomCount < 1) return

        val today = LocalDate.now(clock)
        val endDate = today.plusDays(INVENTORY_HORIZON_DAYS)
        val existing = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
            propertyId, roomTypeId, today, endDate
        ).associateBy { it.date }

        var date = today
        while (!date.isAfter(endDate)) {
            val inv = existing[date]
            if (inv == null) {
                inventoryRepository.save(
                    RoomInventory.create(
                        id = idGenerator.generate(),
                        propertyId = propertyId,
                        roomTypeId = roomTypeId,
                        date = date,
                        totalCount = roomCount
                    )
                )
            } else if (inv.totalCount != roomCount) {
                inventoryRepository.save(inv.updateTotalCount(roomCount))
            }
            date = date.plusDays(1)
        }
        log.info("재고 동기화: propertyId={}, roomTypeId={}", propertyId, roomTypeId)
    }

    fun getAvailability(
        propertyId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RoomInventory> =
        inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(propertyId, roomTypeId, startDate, endDate)

    fun bulkBlock(
        propertyId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        daysOfWeek: List<DayOfWeek>?,
        action: String,
        count: Int
    ): Int {
        require(!startDate.isAfter(endDate)) { "시작일이 종료일보다 클 수 없습니다." }

        var processed = 0
        var date = startDate
        while (!date.isAfter(endDate)) {
            if (daysOfWeek == null || date.dayOfWeek in daysOfWeek) {
                val inv = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate(propertyId, roomTypeId, date)
                if (inv != null) {
                    try {
                        val updated = if (action == "BLOCK") inv.block(count) else inv.unblock(count)
                        if (updated !== inv) {
                            saveAndEvict(updated)
                            availabilitySyncPort.requestAvailabilitySync(
                                propertyId, roomTypeId, date, updated.availableCount
                            )
                            processed++
                        }
                    } catch (_: IllegalArgumentException) {
                        // count < 1 등 — 해당 날짜 건너뜀
                    } catch (_: ConflictException) {
                        // 버전 충돌 — 해당 날짜 건너뜀
                    }
                }
            }
            date = date.plusDays(1)
        }
        if (processed > 0) {
            availabilitySyncPort.processImmediately(propertyId)
        }
        log.info("재고 일괄 {}: propertyId={}, roomTypeId={}, range={}~{}, processed={}", action, propertyId, roomTypeId, startDate, endDate, processed)
        return processed
    }

    fun blockInventory(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        count: Int
    ): RoomInventory {
        val inventory = getOrThrow(propertyId, roomTypeId, date)
        val updated = saveAndEvict(inventory.block(count))
        availabilitySyncPort.requestAvailabilitySync(propertyId, roomTypeId, date, updated.availableCount)
        availabilitySyncPort.processImmediately(propertyId)
        log.info("재고 차단: propertyId={}, roomTypeId={}, date={}, count={}", propertyId, roomTypeId, date, count)
        return updated
    }

    fun unblockInventory(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        count: Int
    ): RoomInventory {
        val inventory = getOrThrow(propertyId, roomTypeId, date)
        val updated = saveAndEvict(inventory.unblock(count))
        availabilitySyncPort.requestAvailabilitySync(propertyId, roomTypeId, date, updated.availableCount)
        availabilitySyncPort.processImmediately(propertyId)
        log.info("재고 차단 해제: propertyId={}, roomTypeId={}, date={}, count={}", propertyId, roomTypeId, date, count)
        return updated
    }

    override fun reserve(propertyId: String, roomTypeId: String, date: LocalDate) {
        val inventory = getOrThrow(propertyId, roomTypeId, date)
        saveAndEvict(inventory.reserve())
        log.info("재고 예약: propertyId={}, roomTypeId={}, date={}", propertyId, roomTypeId, date)
    }

    override fun release(propertyId: String, roomTypeId: String, date: LocalDate) {
        val inventory = getOrThrow(propertyId, roomTypeId, date)
        saveAndEvict(inventory.release())
        log.info("재고 해제: propertyId={}, roomTypeId={}, date={}", propertyId, roomTypeId, date)
    }

    private fun getOrThrow(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory =
        cacheGet(propertyId, roomTypeId, date)
            ?: inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate(propertyId, roomTypeId, date)
                ?.also { cachePut(it) }
            ?: throw NotFoundException("INVENTORY_NOT_FOUND", "재고를 찾을 수 없습니다: $propertyId/$roomTypeId/$date")

    private fun saveAndEvict(inventory: RoomInventory): RoomInventory =
        inventoryRepository.save(inventory).also {
            cacheEvict(inventory.propertyId, inventory.roomTypeId, inventory.date)
        }

    private fun cacheGet(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory? =
        runCatching { cache.get(propertyId, roomTypeId, date) }
            .onFailure {
                log.warn(
                    "재고 캐시 조회 실패, DB 조회로 대체: propertyId={}, roomTypeId={}, date={}",
                    propertyId,
                    roomTypeId,
                    date,
                    it
                )
            }
            .getOrNull()

    private fun cachePut(inventory: RoomInventory) {
        runCatching { cache.put(inventory) }
            .onFailure {
                log.warn(
                    "재고 캐시 저장 실패: propertyId={}, roomTypeId={}, date={}",
                    inventory.propertyId,
                    inventory.roomTypeId,
                    inventory.date,
                    it
                )
            }
    }

    private fun cacheEvict(propertyId: String, roomTypeId: String, date: LocalDate) {
        runCatching { cache.evict(propertyId, roomTypeId, date) }
            .onFailure {
                log.warn(
                    "재고 캐시 무효화 실패: propertyId={}, roomTypeId={}, date={}",
                    propertyId,
                    roomTypeId,
                    date,
                    it
                )
            }
    }
}
