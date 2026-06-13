package com.stayops.channel.application.service

import com.stayops.channel.domain.model.ChannelType
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.service.MockOtaRandomBookingResult
import com.stayops.channel.domain.service.MockOtaSimulationPort
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.NotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class MockOtaSimulationApplication(
    private val channelRepository: ChannelRepository,
    private val mockOtaSimulationPort: MockOtaSimulationPort,
    @Value("\${mock-ota.endpoint}") private val otaEndpoint: String
) {
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

        return mockOtaSimulationPort.simulateRandomBooking(
            endpoint = otaEndpoint,
            propertyId = propertyId,
            channelCode = channel.code
        )
    }
}
