package com.stayops.channel.application.service

import com.stayops.channel.domain.model.ChannelType
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.application.required.MockOtaRandomBookingResult
import com.stayops.channel.application.required.MockOtaBookingSimulator
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.NotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

@Service
class MockOtaSimulationApplication(
    private val channelRepository: ChannelRepository,
    private val mockOtaBookingSimulator: MockOtaBookingSimulator,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomInventoryRepository: RoomInventoryRepository,
    private val clock: Clock,
    @Value("\${mock-ota.endpoint}") private val otaEndpoint: String
) {
    companion object {
        private const val SIMULATION_LOOKAHEAD_DAYS = 90L
    }

    fun simulateRandomBooking(propertyId: String, channelId: String): MockOtaRandomBookingResult {
        val channel = channelRepository.findById(channelId)
            ?: throw NotFoundException(code = "CHANNEL_NOT_FOUND", message = "채널을 찾을 수 없습니다: $channelId")
        if (channel.propertyId != propertyId) {
            throw NotFoundException(code = "CHANNEL_NOT_FOUND", message = "채널을 찾을 수 없습니다: $channelId")
        }
        if (channel.type != ChannelType.OTA) {
            throw BusinessException(
                code = "DIRECT_CHANNEL_NOT_SUPPORTED",
                message = "OTA 채널만 예약 시뮬레이션을 실행할 수 있습니다"
            )
        }

        val candidate = findEarliestAvailableInventory(propertyId)
            ?: throw BusinessException(
                code = "MOCK_OTA_SIMULATION_NO_AVAILABLE_INVENTORY",
                message = "Mock OTA 예약 시뮬레이션에 사용할 수 있는 PMS 재고가 없습니다"
            )

        return mockOtaBookingSimulator.simulateInventoryBooking(
            endpoint = otaEndpoint,
            propertyId = propertyId,
            channelCode = channel.code,
            roomTypeCode = candidate.roomTypeId,
            date = candidate.date
        )
    }

    private fun findEarliestAvailableInventory(propertyId: String): RoomInventory? {
        val today = LocalDate.now(clock)
        val endDate = today.plusDays(SIMULATION_LOOKAHEAD_DAYS)
        return roomTypeRepository.findByPropertyId(propertyId)
            .flatMap { roomType ->
                roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                    propertyId,
                    roomType.id,
                    today,
                    endDate
                )
            }
            .filter { it.availableCount > 0 }
            .sortedWith(compareBy<RoomInventory> { it.date }.thenBy { it.roomTypeId })
            .firstOrNull()
    }
}
