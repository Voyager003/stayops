package com.stayops.channel.infrastructure.external

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.web.client.RestClient

class HttpMockOtaSimulationAdapterTest : BehaviorSpec({

    lateinit var server: MockWebServer
    lateinit var sut: HttpMockOtaSimulationAdapter

    beforeTest {
        server = MockWebServer()
        server.start()
        sut = HttpMockOtaSimulationAdapter(
            restClient = RestClient.builder().build(),
            basicAuthUsername = "mock-user",
            basicAuthPassword = "mock-password"
        )
    }

    afterTest {
        server.shutdown()
    }

    given("Mock OTA 랜덤 예약 시뮬레이션 호출 시") {
        `when`("Mock OTA가 성공 응답을 반환하면") {
            then("설정된 endpoint의 simulate API로 서버 간 요청을 보낸다") {
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(
                            """
                            {
                              "status": "sent",
                              "bookingId": "booking-1",
                              "roomTypeId": "rt-1",
                              "date": "2026-05-01",
                              "guestName": "김민수"
                            }
                            """.trimIndent()
                        )
                )

                val result = sut.simulateRandomBooking(
                    endpoint = server.url("").toString().trimEnd('/'),
                    propertyId = "prop-1",
                    channelCode = "AGODA"
                )

                val request = server.takeRequest()
                request.method shouldBe "POST"
                request.path shouldBe "/api/v1/simulate/random-booking"
                request.getHeader("Authorization") shouldBe "Basic bW9jay11c2VyOm1vY2stcGFzc3dvcmQ="
                request.body.readUtf8() shouldBe """{"propertyId":"prop-1","channelCode":"AGODA"}"""
                result.bookingId shouldBe "booking-1"
                result.guestName shouldBe "김민수"
            }
        }
    }
})
