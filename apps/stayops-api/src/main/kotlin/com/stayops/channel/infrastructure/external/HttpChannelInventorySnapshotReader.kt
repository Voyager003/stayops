package com.stayops.channel.infrastructure.external

import com.stayops.channel.application.required.ChannelInventorySnapshotReader
import com.stayops.channel.application.required.ExternalInventorySnapshot
import com.stayops.shared.exception.BusinessException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.LocalDate

@Component
class HttpChannelInventorySnapshotReader(
    @Qualifier("otaRestClient") private val restClient: RestClient
) : ChannelInventorySnapshotReader {

    override fun fetchInventory(
        apiEndpoint: String,
        propertyId: String,
        channelCode: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ExternalInventorySnapshot> {
        val items = try {
            restClient.get()
                .uri(
                    "$apiEndpoint/api/v1/ari/inventory?propertyId={propertyId}&channelCode={channelCode}&roomTypeId={roomTypeId}&startDate={startDate}&endDate={endDate}",
                    propertyId, channelCode, roomTypeId, startDate.toString(), endDate.toString()
                )
                .retrieve()
                .body(object : ParameterizedTypeReference<List<OtaInventoryDto>>() {})
                ?: emptyList()
        } catch (e: RestClientException) {
            throw BusinessException(
                code = "OTA_CONNECTION_FAILED",
                message = "OTA 서버에 연결할 수 없습니다: ${e.message}"
            )
        }

        return items.map { it.toDomain() }
    }

    private data class OtaInventoryDto(
        val propertyId: String,
        val channelCode: String,
        val roomTypeId: String,
        val date: String,
        val availableCount: Int
    ) {
        fun toDomain() = ExternalInventorySnapshot(
            roomTypeId = roomTypeId,
            date = LocalDate.parse(date),
            availableCount = availableCount
        )
    }
}
