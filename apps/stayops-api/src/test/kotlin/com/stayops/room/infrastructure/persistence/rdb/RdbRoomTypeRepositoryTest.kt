package com.stayops.room.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.jooq.generated.Tables.ROOM_TYPE_AMENITIES
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
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

@SpringJUnitConfig(classes = [RdbRoomTypeRepositoryTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbRoomTypeRepositoryTest @Autowired constructor(
    private val roomTypeRepository: RoomTypeRepository,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, RdbRoomTypeRepository::class)
    class TestApplication

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(ROOM_TYPE_AMENITIES).execute()
        dsl.deleteFrom(ROOM_TYPES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
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
            insertProperty("prop-1", "owner-1")
            val roomType = newRoomType()

            val saved = roomTypeRepository.save(roomType)

            assertThat(saved.id).isEqualTo(roomType.id)
            assertThat(saved.name).isEqualTo(roomType.name)
            assertThat(saved.basePrice.amount).isEqualByComparingTo(BigDecimal("150000"))
            assertThat(saved.basePrice.currency).isEqualTo("KRW")
            assertThat(saved.amenities).containsExactlyInAnyOrder("TV", "에어컨")
        }

        @Test
        fun `수정 후 저장하면 편의시설 목록이 교체된다`() {
            insertProperty("prop-1", "owner-1")
            val roomType = newRoomType()
            roomTypeRepository.save(roomType)

            val updated = roomType.updateInfo(
                name = "디럭스 트윈",
                amenities = listOf("넷플릭스", "공기청정기")
            )
            roomTypeRepository.save(updated)

            val found = roomTypeRepository.findById(roomType.id)

            assertThat(found).isNotNull
            assertThat(found!!.name).isEqualTo("디럭스 트윈")
            assertThat(found.amenities).containsExactlyInAnyOrder("넷플릭스", "공기청정기")
        }
    }

    @Nested
    inner class `findById 호출 시` {

        @Test
        fun `존재하는 id로 조회하면 RoomType을 반환한다`() {
            insertProperty("prop-1", "owner-1")
            val roomType = newRoomType()
            roomTypeRepository.save(roomType)

            val found = roomTypeRepository.findById(roomType.id)

            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo(roomType.id)
            assertThat(found.amenities).containsExactlyInAnyOrder("TV", "에어컨")
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
            insertProperty("prop-1", "owner-1")
            insertProperty("prop-2", "owner-2")
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
            insertProperty("prop-1", "owner-1")
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

    @Nested
    inner class `deleteById 호출 시` {

        @Test
        fun `객실타입과 편의시설을 함께 삭제한다`() {
            insertProperty("prop-1", "owner-1")
            roomTypeRepository.save(newRoomType(id = "rt-1", propertyId = "prop-1"))

            roomTypeRepository.deleteById("rt-1")

            assertThat(roomTypeRepository.findById("rt-1")).isNull()
            assertThat(
                dsl.fetchCount(
                    ROOM_TYPE_AMENITIES,
                    ROOM_TYPE_AMENITIES.ROOM_TYPE_ID.eq("rt-1")
                )
            ).isZero()
        }
    }

    private fun insertProperty(propertyId: String, ownerId: String) {
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
