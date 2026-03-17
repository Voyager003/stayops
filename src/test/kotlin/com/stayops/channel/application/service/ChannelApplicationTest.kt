package com.stayops.channel.application.service

import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal

class ChannelApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val mappingRepository = mockk<ChannelMappingRepository>()

    val sut = ChannelApplication(
        channelRepository = channelRepository,
        channelMappingRepository = mappingRepository
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

    // -- addMapping --

    given("매핑 추가 시") {
        `when`("기존 매핑이 없으면 새로 생성한다") {
            then("매핑이 저장된다") {
                clearAllMocks()
                every { mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA") } returns null
                every { mappingRepository.save(any()) } answers { firstArg() }

                val entry = MappingEntry("rt-1", "AGD-DLX", MappingType.ROOM_TYPE)
                val result = sut.addMapping("prop-1", "AGODA", entry)

                result.mappings.size shouldBe 1
                result.mappings[0].externalCode shouldBe "AGD-DLX"
                verify { mappingRepository.save(any()) }
            }
        }
    }
})
