package com.stayops.channel.infrastructure.persistence

import com.stayops.IntegrationTestSupport
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelConnectionInfo
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.repository.ChannelRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import java.math.BigDecimal

class MongoChannelRepositoryTest @Autowired constructor(
    private val channelRepository: ChannelRepository,
    private val mongoDataRepository: ChannelMongoDataRepository
) : IntegrationTestSupport() {

    @BeforeEach
    fun setUp() {
        mongoDataRepository.deleteAll()
    }

    private fun directChannel(
        id: String = "ch-1",
        propertyId: String = "prop-1"
    ) = Channel.createDirect(id = id, propertyId = propertyId)

    private fun otaChannel(
        id: String = "ch-2",
        propertyId: String = "prop-1",
        code: String = "AGODA",
        name: String = "Agoda"
    ) = Channel.createOta(
        id = id,
        propertyId = propertyId,
        code = code,
        name = name,
        commissionRate = BigDecimal("0.15"),
        connectionInfo = ChannelConnectionInfo(
            apiEndpoint = "https://mock-ota.example.com/ari",
            apiKey = "test-key",
            apiSecret = "test-secret",
            webhookSecret = "webhook-secret-256bit"
        )
    )

    @Nested
    inner class `save 호출 시` {
        @Test
        fun `DIRECT 채널을 저장하고 동일한 도메인 객체를 반환한다`() {
            val channel = directChannel()
            val saved = channelRepository.save(channel)

            assertThat(saved.id).isEqualTo(channel.id)
            assertThat(saved.code).isEqualTo("DIRECT")
            assertThat(saved.commissionRate).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(saved.connectionInfo).isNull()
        }

        @Test
        fun `OTA 채널을 저장하고 connectionInfo가 보존된다`() {
            val channel = otaChannel()
            val saved = channelRepository.save(channel)

            assertThat(saved.id).isEqualTo(channel.id)
            assertThat(saved.code).isEqualTo("AGODA")
            assertThat(saved.connectionInfo).isNotNull
            assertThat(saved.connectionInfo!!.apiEndpoint).isEqualTo("https://mock-ota.example.com/ari")
            assertThat(saved.connectionInfo!!.webhookSecret).isEqualTo("webhook-secret-256bit")
        }
    }

    @Nested
    inner class `findById 호출 시` {
        @Test
        fun `존재하는 id로 조회하면 Channel을 반환한다`() {
            channelRepository.save(otaChannel())

            val found = channelRepository.findById("ch-2")

            assertThat(found).isNotNull
            assertThat(found!!.code).isEqualTo("AGODA")
        }

        @Test
        fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
            val found = channelRepository.findById("not-exist")

            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `findByPropertyId 호출 시` {
        @Test
        fun `해당 propertyId의 모든 채널을 반환한다`() {
            channelRepository.save(directChannel())
            channelRepository.save(otaChannel(id = "ch-2", code = "AGODA"))
            channelRepository.save(otaChannel(id = "ch-3", code = "BOOKING", name = "Booking.com"))
            channelRepository.save(otaChannel(id = "ch-4", propertyId = "prop-2", code = "AGODA"))

            val channels = channelRepository.findByPropertyId("prop-1")

            assertThat(channels).hasSize(3)
        }
    }

    @Nested
    inner class `findByPropertyIdAndCode 호출 시` {
        @Test
        fun `propertyId와 code로 단일 채널을 반환한다`() {
            channelRepository.save(otaChannel())

            val found = channelRepository.findByPropertyIdAndCode("prop-1", "AGODA")

            assertThat(found).isNotNull
            assertThat(found!!.name).isEqualTo("Agoda")
        }

        @Test
        fun `일치하는 채널이 없으면 null을 반환한다`() {
            val found = channelRepository.findByPropertyIdAndCode("prop-1", "EXPEDIA")

            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `findByPropertyIdAndStatus 호출 시` {
        @Test
        fun `해당 상태의 채널만 반환한다`() {
            channelRepository.save(directChannel())
            channelRepository.save(otaChannel(id = "ch-2", code = "AGODA"))
            val deactivated = otaChannel(id = "ch-3", code = "BOOKING", name = "Booking.com").deactivate()
            channelRepository.save(deactivated)

            val activeChannels = channelRepository.findByPropertyIdAndStatus("prop-1", ChannelStatus.ACTIVE)
            val inactiveChannels = channelRepository.findByPropertyIdAndStatus("prop-1", ChannelStatus.INACTIVE)

            assertThat(activeChannels).hasSize(2)
            assertThat(inactiveChannels).hasSize(1)
            assertThat(inactiveChannels[0].code).isEqualTo("BOOKING")
        }
    }

    @Nested
    inner class `deleteById 호출 시` {
        @Test
        fun `채널이 삭제된다`() {
            channelRepository.save(otaChannel())

            channelRepository.deleteById("ch-2")

            assertThat(channelRepository.findById("ch-2")).isNull()
        }
    }

    @Nested
    inner class `유니크 제약 조건` {
        @Test
        fun `같은 propertyId와 code로 저장하면 DuplicateKeyException이 발생한다`() {
            channelRepository.save(otaChannel(id = "ch-1", code = "AGODA"))

            assertThrows<DuplicateKeyException> {
                channelRepository.save(otaChannel(id = "ch-2", code = "AGODA"))
            }
        }

        @Test
        fun `다른 propertyId면 같은 code를 저장할 수 있다`() {
            channelRepository.save(otaChannel(id = "ch-1", propertyId = "prop-1", code = "AGODA"))
            channelRepository.save(otaChannel(id = "ch-2", propertyId = "prop-2", code = "AGODA"))

            assertThat(channelRepository.findByPropertyIdAndCode("prop-1", "AGODA")).isNotNull
            assertThat(channelRepository.findByPropertyIdAndCode("prop-2", "AGODA")).isNotNull
        }
    }
}
