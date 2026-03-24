package com.stayops.channel.infrastructure.persistence

import com.stayops.TestcontainersConfiguration
import com.stayops.channel.domain.model.ChannelMapping
import com.stayops.channel.domain.model.MappingEntry
import com.stayops.channel.domain.model.MappingType
import com.stayops.channel.domain.repository.ChannelMappingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MongoChannelMappingRepositoryTest @Autowired constructor(
    private val mappingRepository: ChannelMappingRepository,
    private val mongoDataRepository: ChannelMappingMongoDataRepository
) {

    @BeforeEach
    fun setUp() {
        mongoDataRepository.deleteAll()
    }

    private fun newMapping(
        id: String = "map-1",
        propertyId: String = "prop-1",
        channelCode: String = "AGODA"
    ) = ChannelMapping.create(id = id, propertyId = propertyId, channelCode = channelCode)

    @Nested
    inner class `save 호출 시` {
        @Test
        fun `빈 매핑을 저장하고 반환한다`() {
            val mapping = newMapping()
            val saved = mappingRepository.save(mapping)

            assertThat(saved.propertyId).isEqualTo("prop-1")
            assertThat(saved.channelCode).isEqualTo("AGODA")
            assertThat(saved.mappings).isEmpty()
        }

        @Test
        fun `매핑 엔트리가 포함된 상태로 저장하고 복원한다`() {
            val mapping = newMapping()
                .addMapping(MappingEntry("rt-1", "OTA-DELUXE", MappingType.ROOM_TYPE))
                .addMapping(MappingEntry("rt-2", "OTA-STANDARD", MappingType.ROOM_TYPE))

            val saved = mappingRepository.save(mapping)

            assertThat(saved.mappings).hasSize(2)
            assertThat(saved.findExternalCode("rt-1", MappingType.ROOM_TYPE)).isEqualTo("OTA-DELUXE")
            assertThat(saved.findExternalCode("rt-2", MappingType.ROOM_TYPE)).isEqualTo("OTA-STANDARD")
        }
    }

    @Nested
    inner class `findByPropertyIdAndChannelCode 호출 시` {
        @Test
        fun `존재하는 매핑을 반환한다`() {
            mappingRepository.save(newMapping())

            val found = mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA")

            assertThat(found).isNotNull
            assertThat(found!!.channelCode).isEqualTo("AGODA")
        }

        @Test
        fun `존재하지 않으면 null을 반환한다`() {
            val found = mappingRepository.findByPropertyIdAndChannelCode("prop-1", "BOOKING")

            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `deleteByPropertyIdAndChannelCode 호출 시` {
        @Test
        fun `매핑이 삭제된다`() {
            mappingRepository.save(newMapping())

            mappingRepository.deleteByPropertyIdAndChannelCode("prop-1", "AGODA")

            assertThat(mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA")).isNull()
        }
    }

    @Nested
    inner class `유니크 제약 조건` {
        @Test
        fun `같은 propertyId와 channelCode로 저장하면 DuplicateKeyException이 발생한다`() {
            mappingRepository.save(newMapping(id = "map-1"))

            assertThrows<DuplicateKeyException> {
                mappingRepository.save(newMapping(id = "map-2"))
            }
        }

        @Test
        fun `다른 channelCode면 같은 propertyId로 저장할 수 있다`() {
            mappingRepository.save(newMapping(id = "map-1", channelCode = "AGODA"))
            mappingRepository.save(newMapping(id = "map-2", channelCode = "BOOKING"))

            assertThat(mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA")).isNotNull
            assertThat(mappingRepository.findByPropertyIdAndChannelCode("prop-1", "BOOKING")).isNotNull
        }
    }
}
