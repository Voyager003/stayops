package com.stayops.channel.application.service

import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.ProcessedWebhookEventRepository
import com.stayops.channel.domain.service.SignatureVerifier
import com.stayops.shared.exception.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.dao.DuplicateKeyException
import java.math.BigDecimal

class WebhookApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val mappingRepository = mockk<ChannelMappingRepository>()
    val processedEventRepository = mockk<ProcessedWebhookEventRepository>()
    val signatureVerifier = mockk<SignatureVerifier>()
    val channelSyncApplication = mockk<ChannelSyncApplication>()

    val sut = WebhookApplication(
        channelRepository = channelRepository,
        channelMappingRepository = mappingRepository,
        processedEventRepository = processedEventRepository,
        signatureVerifier = signatureVerifier,
        channelSyncApplication = channelSyncApplication
    )

    val otaChannel = Channel.createOta(
        id = "ch-1",
        propertyId = "prop-1",
        code = "AGODA",
        name = "Agoda",
        commissionRate = BigDecimal("0.15"),
        connectionInfo = ChannelConnectionInfo(
            apiEndpoint = "https://mock-ota/ari",
            apiKey = "key",
            apiSecret = null,
            webhookSecret = "webhook-secret"
        )
    )

    given("유효한 Webhook 수신 시") {
        `when`("서명 검증 통과하고 신규 이벤트이면") {
            then("ProcessedWebhookEvent 저장 후 이벤트가 처리된다") {
                clearAllMocks()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel
                every { signatureVerifier.verify("webhook-secret", any(), "sha256=valid") } returns true
                every { processedEventRepository.save(any()) } answers { firstArg() }
                every { mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA") } returns null

                sut.handleWebhook(
                    propertyId = "prop-1",
                    channelCode = "AGODA",
                    signature = "sha256=valid",
                    rawBody = """{"eventId":"evt-1"}""",
                    eventId = "evt-1",
                    eventType = "BOOKING",
                    payload = mapOf("roomTypeCode" to "AGD-DLX", "bookingId" to "book-1")
                )

                verify { processedEventRepository.save(match { it.eventId == "evt-1" }) }
                verify { mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA") }
            }
        }
    }

    given("중복 이벤트 수신 시") {
        `when`("save에서 DuplicateKeyException이 발생하면") {
            then("이벤트 처리를 건너뛴다") {
                clearAllMocks()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel
                every { signatureVerifier.verify(any(), any(), any()) } returns true
                every { processedEventRepository.save(any()) } throws DuplicateKeyException("duplicate eventId")

                sut.handleWebhook(
                    propertyId = "prop-1",
                    channelCode = "AGODA",
                    signature = "sha256=valid",
                    rawBody = "body",
                    eventId = "evt-dup",
                    eventType = "BOOKING",
                    payload = emptyMap()
                )

                verify(exactly = 0) { mappingRepository.findByPropertyIdAndChannelCode(any(), any()) }
            }
        }
    }

    given("서명이 유효하지 않을 때") {
        `when`("verify가 false를 반환하면") {
            then("BusinessException이 발생한다") {
                clearAllMocks()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel
                every { signatureVerifier.verify(any(), any(), any()) } returns false

                val exception = shouldThrow<BusinessException> {
                    sut.handleWebhook(
                        propertyId = "prop-1",
                        channelCode = "AGODA",
                        signature = "sha256=invalid",
                        rawBody = "body",
                        eventId = "evt-2",
                        eventType = "BOOKING",
                        payload = emptyMap()
                    )
                }
                exception.code shouldBe "INVALID_SIGNATURE"
            }
        }
    }
})
