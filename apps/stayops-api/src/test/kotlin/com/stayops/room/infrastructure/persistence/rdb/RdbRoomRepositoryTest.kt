package com.stayops.room.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.ROOMS
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.jooq.generated.Tables.ROOM_TYPE_AMENITIES
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomStatus
import com.stayops.room.domain.repository.RoomRepository
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringJUnitConfig(classes = [RdbRoomRepositoryTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbRoomRepositoryTest @Autowired constructor(
    private val roomRepository: RoomRepository,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, RdbRoomRepository::class)
    class TestApplication

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(ROOMS).execute()
        dsl.deleteFrom(ROOM_TYPE_AMENITIES).execute()
        dsl.deleteFrom(ROOM_TYPES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
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
            insertRoomType("rt-1", "prop-1", "owner-1")
            val room = newRoom()

            val saved = roomRepository.save(room)

            assertThat(saved.id).isEqualTo(room.id)
            assertThat(saved.roomNumber).isEqualTo(room.roomNumber)
            assertThat(saved.status).isEqualTo(RoomStatus.AVAILABLE)
            assertThat(saved.memo).isNull()
        }

        @Test
        fun `상태 변경 후 저장하면 변경된 상태가 유지된다`() {
            insertRoomType("rt-1", "prop-1", "owner-1")
            val room = newRoom().checkIn()
            roomRepository.save(room)

            val found = roomRepository.findById(room.id)

            assertThat(found).isNotNull
            assertThat(found!!.status).isEqualTo(RoomStatus.OCCUPIED)
        }

        @Test
        fun `메모 변경 후 저장하면 변경된 메모가 유지된다`() {
            insertRoomType("rt-1", "prop-1", "owner-1")
            val room = newRoom()
            roomRepository.save(room)

            val updated = room.updateMemo("late checkout")
            roomRepository.save(updated)

            val found = roomRepository.findById(room.id)

            assertThat(found).isNotNull
            assertThat(found!!.memo).isEqualTo("late checkout")
        }
    }

    @Nested
    inner class `findById 호출 시` {

        @Test
        fun `존재하는 id로 조회하면 Room을 반환한다`() {
            insertRoomType("rt-1", "prop-1", "owner-1")
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
            insertRoomType("rt-1", "prop-1", "owner-1")
            insertRoomType("rt-2", "prop-2", "owner-2")
            roomRepository.save(newRoom(id = "room-1", propertyId = "prop-1", roomTypeId = "rt-1", roomNumber = "101"))
            roomRepository.save(newRoom(id = "room-2", propertyId = "prop-1", roomTypeId = "rt-1", roomNumber = "102"))
            roomRepository.save(newRoom(id = "room-3", propertyId = "prop-2", roomTypeId = "rt-2", roomNumber = "101"))

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
            insertRoomType("rt-1", "prop-1", "owner-1")
            insertRoomType("rt-2", "prop-1", "owner-1")
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
            insertRoomType("rt-1", "prop-1", "owner-1")
            roomRepository.save(newRoom(propertyId = "prop-1", roomTypeId = "rt-1", roomNumber = "101"))

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

    private fun insertRoomType(roomTypeId: String, propertyId: String, ownerId: String) {
        insertProperty(propertyId, ownerId)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(ROOM_TYPES)
            .set(ROOM_TYPES.ID, roomTypeId)
            .set(ROOM_TYPES.PROPERTY_ID, propertyId)
            .set(ROOM_TYPES.NAME, roomTypeId)
            .set(ROOM_TYPES.DESCRIPTION, "test room type")
            .set(ROOM_TYPES.MAX_OCCUPANCY, 2)
            .set(ROOM_TYPES.BASE_PRICE_AMOUNT, BigDecimal("150000.00"))
            .set(ROOM_TYPES.BASE_PRICE_CURRENCY, "KRW")
            .set(ROOM_TYPES.VERSION, 0L)
            .set(ROOM_TYPES.CREATED_AT, now)
            .set(ROOM_TYPES.UPDATED_AT, now)
            .execute()
    }

    private fun insertProperty(propertyId: String, ownerId: String) {
        if (dsl.fetchExists(PROPERTIES, PROPERTIES.ID.eq(propertyId))) return

        insertMember(ownerId)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(PROPERTIES)
            .set(PROPERTIES.ID, propertyId)
            .set(PROPERTIES.OWNER_ID, ownerId)
            .set(PROPERTIES.NAME, "Stay Ops Hotel")
            .set(PROPERTIES.TYPE, "HOTEL")
            .set(PROPERTIES.ADDRESS_STREET, "1 Test Street")
            .set(PROPERTIES.ADDRESS_CITY, "Seoul")
            .set(PROPERTIES.ADDRESS_STATE, "Seoul")
            .set(PROPERTIES.ADDRESS_ZIP_CODE, "00000")
            .set(PROPERTIES.ADDRESS_COUNTRY, "KR")
            .set(PROPERTIES.CONTACT_PHONE, "010-0000-0000")
            .set(PROPERTIES.CONTACT_EMAIL, "property@stayops.com")
            .set(PROPERTIES.DESCRIPTION, "test property")
            .set(PROPERTIES.STATUS, "ACTIVE")
            .set(PROPERTIES.TIMEZONE, "Asia/Seoul")
            .set(PROPERTIES.CURRENCY, "KRW")
            .set(PROPERTIES.VERSION, 0L)
            .set(PROPERTIES.CREATED_AT, now)
            .set(PROPERTIES.UPDATED_AT, now)
            .execute()
    }

    private fun insertMember(memberId: String) {
        if (dsl.fetchExists(MEMBERS, MEMBERS.ID.eq(memberId))) return

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(MEMBERS)
            .set(MEMBERS.ID, memberId)
            .set(MEMBERS.EMAIL, "$memberId@stayops.com")
            .set(MEMBERS.PASSWORD_HASH, "hashed-password")
            .set(MEMBERS.NAME, memberId)
            .set(MEMBERS.ROLE, MemberRole.OWNER.name)
            .set(MEMBERS.STATUS, MemberStatus.ACTIVE.name)
            .set(MEMBERS.VERSION, 0L)
            .set(MEMBERS.CREATED_AT, now)
            .set(MEMBERS.UPDATED_AT, now)
            .execute()
    }
}
