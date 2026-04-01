package com.stayops.channel.application.service

import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal

class ChannelApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()

    val sut = ChannelApplication(
        channelRepository = channelRepository
    )

    fun otaChannel(id: String = "ch-1", code: String = "AGODA") = Channel.createOta(
        id = id,
        propertyId = "prop-1",
        code = code,
        name = code,
        commissionRate = BigDecimal("0.15"),
        connectionInfo = ChannelConnectionInfo(
            apiEndpoint = "https://mock-ota/ari",
            apiKey = "key",
            apiSecret = null,
            webhookSecret = "secret"
        )
    )

    // -- createOtaChannel --

    given("OTA 채널 생성 시") {
        `when`("유효한 요청이면") {
            then("채널이 저장되고 반환된다") {
                clearAllMocks()
                every { channelRepository.save(any()) } answers { firstArg() }

                val result = sut.createOtaChannel(
                    propertyId = "prop-1",
                    code = "AGODA",
                    name = "Agoda",
                    commissionRate = BigDecimal("0.15"),
                    connectionInfo = ChannelConnectionInfo(
                        apiEndpoint = "https://mock-ota/ari",
                        apiKey = null,
                        apiSecret = null,
                        webhookSecret = "secret"
                    )
                )

                result.code shouldBe "AGODA"
                result.type shouldBe ChannelType.OTA
                verify { channelRepository.save(any()) }
            }
        }
    }

    // -- findChannel --

    given("채널 조회 시") {
        `when`("존재하지 않는 ID이면") {
            then("NotFoundException이 발생한다") {
                clearAllMocks()
                every { channelRepository.findById("not-exist") } returns null

                shouldThrow<NotFoundException> {
                    sut.findChannel("prop-1", "not-exist")
                }
            }
        }
    }

    given("채널 조회 시") {
        `when`("다른 숙소의 channelId로 조회하면") {
            then("NotFoundException이 발생한다 (테넌트 격리)") {
                clearAllMocks()
                every { channelRepository.findById("ch-1") } returns otaChannel()

                shouldThrow<NotFoundException> {
                    sut.findChannel("other-property", "ch-1")
                }
            }
        }
    }

    // -- activateChannel --

    given("비활성 채널 활성화 시") {
        `when`("activate 호출하면") {
            then("ACTIVE 상태로 변경된다") {
                clearAllMocks()
                val channel = otaChannel().deactivate()
                every { channelRepository.findById("ch-1") } returns channel
                every { channelRepository.save(any()) } answers { firstArg() }

                val result = sut.activateChannel("prop-1", "ch-1")

                result.status shouldBe ChannelStatus.ACTIVE
            }
        }
    }

})
