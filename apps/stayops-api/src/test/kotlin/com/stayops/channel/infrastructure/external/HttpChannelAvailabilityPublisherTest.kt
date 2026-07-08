package com.stayops.channel.infrastructure.external

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.web.client.RestClient

class HttpChannelAvailabilityPublisherTest : BehaviorSpec({

    lateinit var server: MockWebServer
    lateinit var sut: HttpChannelAvailabilityPublisher

    beforeTest {
        server = MockWebServer()
        server.start()
        sut = HttpChannelAvailabilityPublisher(RestClient.builder().build())
    }

    afterTest {
        server.shutdown()
    }

    given("ARI availability push") {
        `when`("Mock OTA로 재고를 전송하면") {
            then("propertyId와 channelCode를 포함한 scoped payload를 보낸다") {
                server.enqueue(MockResponse().setResponseCode(200))

                val result = sut.pushAvailability(
                    endpoint = server.url("").toString().trimEnd('/'),
                    apiKey = null,
                    propertyId = "prop-1",
                    channelCode = "YANOLJA",
                    externalRoomTypeCode = "rt-1",
                    payload = mapOf(
                        "roomTypeId" to "rt-1",
                        "date" to "2026-04-05",
                        "availableCount" to 3
                    ),
                    idempotencyKey = "task-1"
                )

                val request = server.takeRequest()
                result.success shouldBe true
                request.path shouldBe "/api/v1/ari/availability"
                request.getHeader("X-Idempotency-Key") shouldBe "task-1"
                request.body.readUtf8() shouldBe
                    """{"roomTypeId":"rt-1","date":"2026-04-05","availableCount":3,"propertyId":"prop-1","channelCode":"YANOLJA","roomTypeCode":"rt-1"}"""
            }
        }
    }
})
