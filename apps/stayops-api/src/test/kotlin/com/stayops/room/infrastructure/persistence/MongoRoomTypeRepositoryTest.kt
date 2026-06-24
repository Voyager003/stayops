package com.stayops.room.infrastructure.persistence

import com.stayops.room.infrastructure.persistence.dao.RoomTypeMongoDao
import com.stayops.TestcontainersConfiguration
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MongoRoomTypeRepositoryTest @Autowired constructor(
    private val roomTypeRepository: RoomTypeRepository,
    private val mongoDao: RoomTypeMongoDao
) {
    @BeforeEach
    fun setUp() {
        mongoDao.deleteAll()
    }

    private fun newRoomType(
        id: String = "rt-1",
        propertyId: String = "prop-1",
        name: String = "디럭스 더블"
    ) = RoomType.create(
        id = id,
        propertyId = propertyId,
        name = name,
        description = "넓은 더블룸",
        maxOccupancy = 2,
        basePrice = Money.won(150_000),
        amenities = listOf("TV", "에어컨")
    )

    @Nested
    inner class `save 호출 시` {
        @Test
        fun `저장 후 동일한 도메인 객체를 반환한다`() {
            val roomType = newRoomType()
            val saved = roomTypeRepository.save(roomType)

            assertThat(saved.id).isEqualTo(roomType.id)
            assertThat(saved.name).isEqualTo(roomType.name)
            assertThat(saved.basePrice).isEqualTo(Money.won(150_000))
        }

        @Test
        fun `같은 propertyId와 name이면 DuplicateKeyException이 발생한다`() {
            roomTypeRepository.save(newRoomType(id = "rt-1", propertyId = "prop-1", name = "디럭스"))
            org.junit.jupiter.api.assertThrows<DuplicateKeyException> {
                roomTypeRepository.save(newRoomType(id = "rt-2", propertyId = "prop-1", name = "디럭스"))
            }
        }

        @Test
        fun `다른 propertyId이면 동일한 name으로 저장할 수 있다`() {
            roomTypeRepository.save(newRoomType(id = "rt-1", propertyId = "prop-1", name = "디럭스"))
            roomTypeRepository.save(newRoomType(id = "rt-2", propertyId = "prop-2", name = "디럭스"))

            assertThat(roomTypeRepository.findByPropertyId("prop-1")).hasSize(1)
            assertThat(roomTypeRepository.findByPropertyId("prop-2")).hasSize(1)
        }
    }

    @Nested
    inner class `findById 호출 시` {
        @Test
        fun `존재하는 id로 조회하면 RoomType을 반환한다`() {
            val roomType = newRoomType()
            roomTypeRepository.save(roomType)

            val found = roomTypeRepository.findById(roomType.id)
            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo(roomType.id)
            assertThat(found.amenities).containsExactly("TV", "에어컨")
        }

        @Test
        fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
            val found = roomTypeRepository.findById("not-exist")
            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `findByPropertyId 호출 시` {
        @Test
        fun `해당 propertyId의 객실타입 목록을 반환한다`() {
            roomTypeRepository.save(newRoomType(id = "rt-1", propertyId = "prop-1", name = "디럭스"))
            roomTypeRepository.save(newRoomType(id = "rt-2", propertyId = "prop-1", name = "스위트"))
            roomTypeRepository.save(newRoomType(id = "rt-3", propertyId = "prop-2", name = "디럭스"))

            val result = roomTypeRepository.findByPropertyId("prop-1")
            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).containsExactlyInAnyOrder("rt-1", "rt-2")
        }

        @Test
        fun `해당 propertyId의 객실타입이 없으면 빈 리스트를 반환한다`() {
            val result = roomTypeRepository.findByPropertyId("unknown-prop")
            assertThat(result).isEmpty()
        }
    }

    @Nested
    inner class `findByPropertyIdAndName 호출 시` {
        @Test
        fun `존재하는 경우 RoomType을 반환한다`() {
            roomTypeRepository.save(newRoomType(propertyId = "prop-1", name = "디럭스"))

            val found = roomTypeRepository.findByPropertyIdAndName("prop-1", "디럭스")
            assertThat(found).isNotNull
            assertThat(found!!.name).isEqualTo("디럭스")
        }

        @Test
        fun `존재하지 않으면 null을 반환한다`() {
            val found = roomTypeRepository.findByPropertyIdAndName("prop-1", "없는타입")
            assertThat(found).isNull()
        }
    }
}
