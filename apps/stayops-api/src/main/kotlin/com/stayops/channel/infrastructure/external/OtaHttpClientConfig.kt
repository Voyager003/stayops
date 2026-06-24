package com.stayops.channel.infrastructure.external

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * OTA 연동용 공통 HTTP 설정.
 *
 * 과거에는 HttpChannelAvailabilityPublisher 와 HttpChannelInventorySnapshotReader 가 각자
 * RestClient.create() 로 독립 인스턴스를 생성하여 (1) TCP 연결 재사용 불가,
 * (2) 타임아웃 미설정으로 OTA 장애 시 스레드 풀 고갈, (3) 설정 중복 문제가 있었다.
 *
 * 본 Configuration 은 단일 RestClient Bean (`otaRestClient`) 을 제공하여
 * 두 Adapter 가 HttpClient 인스턴스 및 기본 설정을 공유하도록 한다.
 *
 * baseUrl 을 설정하지 않는 이유: 여러 OTA (Agoda, Booking.com 등) 를 동시 연동할 때
 * 채널마다 다른 호스트를 사용해야 하므로 Adapter 가 호출 시점에 endpoint 를 전달한다.
 *
 * 설계 결정 기록: docs/context/infra/rest-client-bean-sharing.md
 */
@ConfigurationProperties(prefix = "ota.http")
data class OtaHttpClientProperties(
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(10)
)

@Configuration
@EnableConfigurationProperties(OtaHttpClientProperties::class)
class OtaHttpClientConfig {

    @Bean
    fun otaRestClient(properties: OtaHttpClientProperties): RestClient {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient)
        requestFactory.setReadTimeout(properties.readTimeout)

        return RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}
