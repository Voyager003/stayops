package com.stayops.payment.infrastructure.external

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.util.Base64

@ConfigurationProperties(prefix = "toss.payments")
data class TossPaymentsProperties(
    val secretKey: String,
    val baseUrl: String
)

@Configuration
class TossPaymentsConfig {

    @Bean
    fun tossRestClient(properties: TossPaymentsProperties): RestClient {
        val encodedKey = Base64.getEncoder().encodeToString("${properties.secretKey}:".toByteArray())
        return RestClient.builder()
            .baseUrl(properties.baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $encodedKey")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}
