package com.stayops.channel.infrastructure.external

import com.stayops.channel.application.required.ChannelAvailabilityPublisher
import com.stayops.channel.application.required.SyncResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Channel 동기화용 HTTP 어댑터 기본 구현.
 *
 * **계약**: 이 어댑터는 절대 예외를 상위(Application)로 전파하지 않는다.
 * 모든 실패는 SyncResult(success = false, errorMessage = ...)로 반환한다.
 * 이를 통해 Application은 try/catch 없이 결과 값만으로 분기하여
 * 비즈니스 흐름을 선언적으로 기술할 수 있다.
 */
@Component
class HttpChannelAvailabilityPublisher(
    @Qualifier("otaRestClient") private val restClient: RestClient
) : ChannelAvailabilityPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun pushAvailability(
        endpoint: String,
        apiKey: String?,
        externalRoomTypeCode: String,
        payload: Map<String, Any>,
        idempotencyKey: String
    ): SyncResult {
        val body = payload.toMutableMap()
        body["roomTypeCode"] = externalRoomTypeCode

        return try {
            val requestSpec = restClient.post()
                .uri("$endpoint/api/v1/ari/availability")
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", idempotencyKey)

            if (apiKey != null) {
                requestSpec.header("X-API-Key", apiKey)
            }

            requestSpec.body(body)
                .retrieve()
                .toBodilessEntity()

            SyncResult(success = true)
        } catch (e: RestClientException) {
            SyncResult(success = false, errorMessage = e.message ?: "HTTP call failed")
        } catch (e: Exception) {
            // 어댑터 계약: 예상치 못한 예외도 절대 상위로 전파하지 않는다.
            // 직렬화 오류, 매핑 오류, NPE 등 모든 예외를 결과 타입으로 변환.
            log.warn("어댑터 내부 예외 발생 — SyncResult(false)로 흡수: ${e.message}", e)
            SyncResult(success = false, errorMessage = e.message ?: "Unexpected adapter error")
        }
    }
}
