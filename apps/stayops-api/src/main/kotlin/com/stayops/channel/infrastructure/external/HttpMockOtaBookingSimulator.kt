package com.stayops.channel.infrastructure.external

import com.stayops.channel.application.required.MockOtaRandomBookingResult
import com.stayops.channel.application.required.MockOtaBookingSimulator
import com.stayops.shared.exception.BusinessException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.LocalDate

@Component
class HttpMockOtaBookingSimulator(
    @Qualifier("otaRestClient") private val restClient: RestClient,
    @Value("\${mock-ota.basic-auth.username:}") private val basicAuthUsername: String = "",
    @Value("\${mock-ota.basic-auth.password:}") private val basicAuthPassword: String = ""
) : MockOtaBookingSimulator {

    override fun simulateRandomBooking(
        endpoint: String,
        propertyId: String,
        channelCode: String
    ): MockOtaRandomBookingResult =
        try {
            restClient.post()
                .uri("$endpoint/api/v1/simulate/random-booking")
                .headers {
                    if (basicAuthUsername.isNotBlank() && basicAuthPassword.isNotBlank()) {
                        it.setBasicAuth(basicAuthUsername, basicAuthPassword)
                    }
                }
                .body(
                    mapOf(
                        "propertyId" to propertyId,
                        "channelCode" to channelCode
                    )
                )
                .retrieve()
                .body(MockOtaRandomBookingResult::class.java)
                ?: throw BusinessException(
                    code = "MOCK_OTA_EMPTY_RESPONSE",
                    message = "Mock OTA 응답이 비어 있습니다"
                )
        } catch (e: BusinessException) {
            throw e
        } catch (e: RestClientException) {
            throw BusinessException(
                code = "MOCK_OTA_SIMULATION_FAILED",
                message = "Mock OTA 예약 시뮬레이션에 실패했습니다"
            )
        }

    override fun simulateInventoryBooking(
        endpoint: String,
        propertyId: String,
        channelCode: String,
        roomTypeCode: String,
        date: LocalDate
    ): MockOtaRandomBookingResult =
        try {
            restClient.post()
                .uri("$endpoint/api/v1/simulate/inventory-booking")
                .headers {
                    if (basicAuthUsername.isNotBlank() && basicAuthPassword.isNotBlank()) {
                        it.setBasicAuth(basicAuthUsername, basicAuthPassword)
                    }
                }
                .body(
                    mapOf(
                        "propertyId" to propertyId,
                        "channelCode" to channelCode,
                        "roomTypeCode" to roomTypeCode,
                        "date" to date.toString()
                    )
                )
                .retrieve()
                .body(MockOtaRandomBookingResult::class.java)
                ?: throw BusinessException(
                    code = "MOCK_OTA_EMPTY_RESPONSE",
                    message = "Mock OTA 응답이 비어 있습니다"
                )
        } catch (e: BusinessException) {
            throw e
        } catch (e: RestClientException) {
            throw BusinessException(
                code = "MOCK_OTA_SIMULATION_FAILED",
                message = "Mock OTA 예약 시뮬레이션에 실패했습니다"
            )
        }
}
