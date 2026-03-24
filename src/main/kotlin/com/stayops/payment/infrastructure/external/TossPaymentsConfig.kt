package com.stayops.payment.infrastructure.external

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration
import java.util.Base64

@ConfigurationProperties(prefix = "toss.payments")
data class TossPaymentsProperties(
    val secretKey: String,
    val baseUrl: String,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(30)
)

@Configuration
class TossPaymentsConfig {

    @Bean
    fun tossRestClient(
        properties: TossPaymentsProperties,
        objectMapper: ObjectMapper
    ): RestClient {
        val encodedKey = Base64.getEncoder().encodeToString("${properties.secretKey}:".toByteArray())

        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient)
        requestFactory.setReadTimeout(properties.readTimeout)

        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $encodedKey")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultStatusHandler({ it.isError }) { _, response ->
                val body = response.body.readAllBytes().decodeToString()
                val errorResponse = try {
                    objectMapper.readValue(body, TossErrorResponse::class.java)
                } catch (_: Exception) {
                    TossErrorResponse("UNKNOWN_ERROR", body.ifBlank { "Empty response" })
                }
                throw TossErrorMapper.map(errorResponse)
            }
            .build()
    }
}
