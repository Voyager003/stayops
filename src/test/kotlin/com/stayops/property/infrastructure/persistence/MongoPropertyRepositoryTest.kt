package com.stayops.property.infrastructure.persistence

import com.stayops.TestcontainersConfiguration
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyStatus
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MongoPropertyRepositoryTest @Autowired constructor(
    private val propertyRepository: PropertyRepository,
    private val mongoDataRepository: PropertyMongoDataRepository
) {
    @BeforeEach
    fun setUp() {
        mongoDataRepository.deleteAll()
    }

    private fun newProperty(
        id: String = "prop-1",
        ownerId: String = "owner-1",
        name: String = "해운대 펜션"
    ) = Property.create(
        id = id,
        ownerId = ownerId,
        name = name,
        type = PropertyType.PENSION,
        address = Address.of("해운대로 123", "부산", "부산광역시", "48099", "KR"),
        contactInfo = ContactInfo.of("051-123-4567", "test@pension.com"),
        description = "아름다운 펜션"
    )

    @Nested
    inner class `save 호출 시` {
        @Test
        fun `저장 후 동일한 도메인 객체를 반환한다`() {
            val property = newProperty()
            val saved = propertyRepository.save(property)

            assertThat(saved.id).isEqualTo(property.id)
            assertThat(saved.name).isEqualTo(property.name)
            assertThat(saved.status).isEqualTo(PropertyStatus.INACTIVE)
        }

        @Test
        fun `상태 변경 후 저장하면 변경된 상태가 유지된다`() {
            val property = newProperty().activate()
            propertyRepository.save(property)

            val found = propertyRepository.findById(property.id)
            assertThat(found).isNotNull
            assertThat(found!!.status).isEqualTo(PropertyStatus.ACTIVE)
        }
    }

    @Nested
    inner class `findById 호출 시` {
        @Test
        fun `존재하는 id로 조회하면 Property를 반환한다`() {
            val property = newProperty()
            propertyRepository.save(property)

            val found = propertyRepository.findById(property.id)
            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo(property.id)
            assertThat(found.address.city).isEqualTo("부산")
        }

        @Test
        fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
            val found = propertyRepository.findById("not-exist")
            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `findByOwnerId 호출 시` {
        @Test
        fun `해당 ownerId의 숙소 목록을 반환한다`() {
            propertyRepository.save(newProperty(id = "prop-1", ownerId = "owner-1"))
            propertyRepository.save(newProperty(id = "prop-2", ownerId = "owner-1"))
            propertyRepository.save(newProperty(id = "prop-3", ownerId = "owner-2"))

            val result = propertyRepository.findByOwnerId("owner-1")
            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).containsExactlyInAnyOrder("prop-1", "prop-2")
        }

        @Test
        fun `해당 ownerId의 숙소가 없으면 빈 리스트를 반환한다`() {
            val result = propertyRepository.findByOwnerId("unknown-owner")
            assertThat(result).isEmpty()
        }
    }

    @Nested
    inner class `findAll 호출 시` {
        @Test
        fun `저장된 모든 숙소를 반환한다`() {
            propertyRepository.save(newProperty(id = "prop-1", ownerId = "owner-1"))
            propertyRepository.save(newProperty(id = "prop-2", ownerId = "owner-2"))

            val result = propertyRepository.findAll()
            assertThat(result).hasSize(2)
        }
    }
}
