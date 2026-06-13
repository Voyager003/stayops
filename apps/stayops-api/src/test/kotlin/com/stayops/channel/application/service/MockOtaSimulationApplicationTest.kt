package com.stayops.channel.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.service.MockOtaRandomBookingResult
import com.stayops.channel.domain.service.MockOtaSimulationPort
import com.stayops.shared.exception.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class MockOtaSimulationApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val mockOtaSimulationPort = mockk<MockOtaSimulationPort>()

    val sut = MockOtaSimulationApplication(
        channelRepository = channelRepository,
        mockOtaSimulationPort = mockOtaSimulationPort,
        otaEndpoint = "https://mock-ota/ari"
    )

    fun otaChannel(id: String = "ch-1", code: String = "AGODA") = Channel.createOta(
        id = id,
        propertyId = "prop-1",
        code = code,
        name = code,
        commissionRate = BigDecimal("0.15"),
        apiEndpoint = "https://mock-ota/ari"
    )

    given("Mock OTA 예약 시뮬레이션은") {
        `when`("OTA 채널이면") {
            then("설정된 endpoint로 Mock OTA를 호출한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-1") } returns otaChannel()
                every {
                    mockOtaSimulationPort.simulateRandomBooking("https://mock-ota/ari", "prop-1", "AGODA")
                } returns MockOtaRandomBookingResult(
                    status = "sent",
                    bookingId = "booking-1",
                    roomTypeId = "rt-1",
                    date = "2026-05-01",
                    guestName = "김민수"
                )

                val result = sut.simulateRandomBooking("prop-1", "ch-1")

                result.bookingId shouldBe "booking-1"
                result.guestName shouldBe "김민수"
                verify {
                    mockOtaSimulationPort.simulateRandomBooking("https://mock-ota/ari", "prop-1", "AGODA")
                }
            }
        }

        `when`("DIRECT 채널이면") {
            then("OTA 채널만 지원한다고 거부한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-0") } returns Channel.createDirect(id = "ch-0", propertyId = "prop-1")

                val exception = shouldThrow<BusinessException> {
                    sut.simulateRandomBooking("prop-1", "ch-0")
                }

                exception.code shouldBe "DIRECT_CHANNEL_NOT_SUPPORTED"
            }
        }
    }
})
