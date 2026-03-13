package com.stayops.channel.infrastructure.persistence

import com.stayops.TestcontainersConfiguration
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelPolicy
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.model.ChannelType
import com.stayops.channel.domain.repository.ChannelRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.math.BigDecimal

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MongoChannelRepositoryTest @Autowired constructor(
    private val channelRepository: ChannelRepository,
    private val mongoDataRepository: ChannelMongoDataRepository
) {

    @BeforeEach
    fun setUp() {
        mongoDataRepository.deleteAll()
    }

    private fun newDirectChannel(id: String = "ch-1", propertyId: String = "prop-1") =
        Channel.createDirect(id = id, propertyId = propertyId)

    private fun newOtaChannel(
        id: String = "ch-2",
        propertyId: String = "prop-1",
        code: String = "AGODA",
        name: String = "아고다"
    ) = Channel.createOta(
        id = id,
        propertyId = propertyId,
        code = code,
        name = name,
        commissionRate = BigDecimal("0.15"),
        webhookSecret = "secret-key"
    )

    @Nested
    inner class Save {
        @Test
        fun `DIRECT 채널을 저장하고 조회하면 동일한 데이터가 반환된다`() {
            channelRepository.save(newDirectChannel())
            val found = channelRepository.findById("ch-1")

            assertThat(found).isNotNull
            assertThat(found!!.code).isEqualTo("FINESTAY")
            assertThat(found.type).isEqualTo(ChannelType.DIRECT)
            assertThat(found.policy).isInstanceOf(ChannelPolicy.DirectPolicy::class.java)
            assertThat(found.policy.commissionRate).isEqualTo(BigDecimal.ZERO)
            assertThat(found.status).isEqualTo(ChannelStatus.ACTIVE)
        }

        @Test
        fun `OTA 채널을 저장하고 조회하면 동일한 데이터가 반환된다`() {
            channelRepository.save(newOtaChannel())
            val found = channelRepository.findById("ch-2")

            assertThat(found).isNotNull
            assertThat(found!!.code).isEqualTo("AGODA")
            assertThat(found.type).isEqualTo(ChannelType.OTA)
            assertThat(found.policy).isInstanceOf(ChannelPolicy.OtaPolicy::class.java)
            val otaPolicy = found.policy as ChannelPolicy.OtaPolicy
            assertThat(otaPolicy.commissionRate).isEqualTo(BigDecimal("0.15"))
            assertThat(otaPolicy.webhookSecret).isEqualTo("secret-key")
        }
    }

    @Nested
    inner class FindByPropertyIdAndCode {
        @Test
        fun `propertyId와 code로 채널을 조회할 수 있다`() {
            channelRepository.save(newDirectChannel())
            channelRepository.save(newOtaChannel())

            val found = channelRepository.findByPropertyIdAndCode("prop-1", "AGODA")
            assertThat(found).isNotNull
            assertThat(found!!.code).isEqualTo("AGODA")
        }

        @Test
        fun `존재하지 않는 조합이면 null을 반환한다`() {
            channelRepository.save(newDirectChannel())

            val found = channelRepository.findByPropertyIdAndCode("prop-1", "AIRBNB")
            assertThat(found).isNull()
        }
    }

    @Nested
    inner class FindByPropertyIdAndStatus {
        @Test
        fun `활성 채널 목록을 조회한다`() {
            channelRepository.save(newDirectChannel())
            channelRepository.save(newOtaChannel())

            val deactivated = newOtaChannel(id = "ch-3", code = "AIRBNB", name = "에어비앤비").deactivate()
            channelRepository.save(deactivated)

            val result = channelRepository.findByPropertyIdAndStatus("prop-1", ChannelStatus.ACTIVE)
            assertThat(result).hasSize(2)
        }
    }

    @Nested
    inner class FindByPropertyId {
        @Test
        fun `숙소의 전체 채널 목록을 반환한다`() {
            channelRepository.save(newDirectChannel())
            channelRepository.save(newOtaChannel())

            val result = channelRepository.findByPropertyId("prop-1")
            assertThat(result).hasSize(2)
        }
    }

    @Nested
    inner class DeleteById {
        @Test
        fun `채널을 삭제하면 조회되지 않는다`() {
            channelRepository.save(newDirectChannel())
            channelRepository.deleteById("ch-1")

            val found = channelRepository.findById("ch-1")
            assertThat(found).isNull()
        }
    }
}
