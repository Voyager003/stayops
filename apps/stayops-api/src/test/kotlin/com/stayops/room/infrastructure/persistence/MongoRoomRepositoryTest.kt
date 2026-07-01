package com.stayops.room.infrastructure.persistence.mongo

import com.stayops.room.infrastructure.persistence.mongo.dao.RoomMongoDao
import com.stayops.TestcontainersConfiguration
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomStatus
import com.stayops.room.domain.repository.RoomRepository
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
class MongoRoomRepositoryTest @Autowired constructor(
    private val roomRepository: RoomRepository,
    private val mongoDao: RoomMongoDao
) {
    @BeforeEach
    fun setUp() {
        mongoDao.deleteAll()
    }

    private fun newRoom(
        id: String = "room-1",
        propertyId: String = "prop-1",
        roomTypeId: String = "rt-1",
        roomNumber: String = "101",
        floor: Int = 1
    ) = Room.create(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        roomNumber = roomNumber,
        floor = floor
    )

    @Nested
    inner class `save 호출 시` {
        @Test
        fun `저장 후 동일한 도메인 객체를 반환한다`() {
            val room = newRoom()
            val saved = roomRepository.save(room)

            assertThat(saved.id).isEqualTo(room.id)
            assertThat(saved.roomNumber).isEqualTo(room.roomNumber)
            assertThat(saved.status).isEqualTo(RoomStatus.AVAILABLE)
            assertThat(saved.memo).isNull()
        }

        @Test
        fun `상태 변경 후 저장하면 변경된 상태가 유지된다`() {
            val room = newRoom().checkIn()
            roomRepository.save(room)

            val found = roomRepository.findById(room.id)
            assertThat(found).isNotNull
            assertThat(found!!.status).isEqualTo(RoomStatus.OCCUPIED)
        }

        @Test
        fun `같은 propertyId와 roomNumber이면 DuplicateKeyException이 발생한다`() {
            roomRepository.save(newRoom(id = "room-1", propertyId = "prop-1", roomNumber = "101"))
            org.junit.jupiter.api.assertThrows<DuplicateKeyException> {
                roomRepository.save(newRoom(id = "room-2", propertyId = "prop-1", roomNumber = "101"))
            }
        }

        @Test
        fun `다른 propertyId이면 동일한 roomNumber로 저장할 수 있다`() {
            roomRepository.save(newRoom(id = "room-1", propertyId = "prop-1", roomNumber = "101"))
            roomRepository.save(newRoom(id = "room-2", propertyId = "prop-2", roomNumber = "101"))

            assertThat(roomRepository.findByPropertyId("prop-1")).hasSize(1)
            assertThat(roomRepository.findByPropertyId("prop-2")).hasSize(1)
        }
    }

    @Nested
    inner class `findById 호출 시` {
        @Test
        fun `존재하는 id로 조회하면 Room을 반환한다`() {
            val room = newRoom()
            roomRepository.save(room)

            val found = roomRepository.findById(room.id)
            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo(room.id)
            assertThat(found.floor).isEqualTo(1)
        }

        @Test
        fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
            val found = roomRepository.findById("not-exist")
            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `findByPropertyId 호출 시` {
        @Test
        fun `해당 propertyId의 객실 목록을 반환한다`() {
            roomRepository.save(newRoom(id = "room-1", propertyId = "prop-1", roomNumber = "101"))
            roomRepository.save(newRoom(id = "room-2", propertyId = "prop-1", roomNumber = "102"))
            roomRepository.save(newRoom(id = "room-3", propertyId = "prop-2", roomNumber = "101"))

            val result = roomRepository.findByPropertyId("prop-1")
            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).containsExactlyInAnyOrder("room-1", "room-2")
        }

        @Test
        fun `해당 propertyId의 객실이 없으면 빈 리스트를 반환한다`() {
            val result = roomRepository.findByPropertyId("unknown-prop")
            assertThat(result).isEmpty()
        }
    }

    @Nested
    inner class `findByRoomTypeId 호출 시` {
        @Test
        fun `해당 roomTypeId의 객실 목록을 반환한다`() {
            roomRepository.save(newRoom(id = "room-1", roomTypeId = "rt-1", roomNumber = "101"))
            roomRepository.save(newRoom(id = "room-2", roomTypeId = "rt-1", roomNumber = "102"))
            roomRepository.save(newRoom(id = "room-3", roomTypeId = "rt-2", roomNumber = "201"))

            val result = roomRepository.findByRoomTypeId("rt-1")
            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).containsExactlyInAnyOrder("room-1", "room-2")
        }
    }

    @Nested
    inner class `findByPropertyIdAndRoomNumber 호출 시` {
        @Test
        fun `존재하는 경우 Room을 반환한다`() {
            roomRepository.save(newRoom(propertyId = "prop-1", roomNumber = "101"))

            val found = roomRepository.findByPropertyIdAndRoomNumber("prop-1", "101")
            assertThat(found).isNotNull
            assertThat(found!!.roomNumber).isEqualTo("101")
        }

        @Test
        fun `존재하지 않으면 null을 반환한다`() {
            val found = roomRepository.findByPropertyIdAndRoomNumber("prop-1", "999")
            assertThat(found).isNull()
        }
    }
}
