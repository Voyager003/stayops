package com.stayops.guest.infrastructure.persistence

import com.stayops.IntegrationTestSupport
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.model.GuestTier
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import java.time.LocalDate

class MongoGuestRepositoryTest @Autowired constructor(
    private val guestRepository: GuestRepository,
    private val mongoDataRepository: GuestMongoDataRepository
) : IntegrationTestSupport() {

    @BeforeEach
    fun setUp() {
        mongoDataRepository.deleteAll()
    }

    private fun newGuest(
        id: String = "guest-1",
        propertyId: String = "prop-1",
        name: String = "홍길동",
        phone: String = "010-1234-5678",
        email: String? = null,
        memo: String? = null,
    ): Guest = Guest.create(
        id = id,
        propertyId = propertyId,
        name = name,
        phone = phone,
        email = email,
        memo = memo,
    )

    @Nested
    inner class Save {
        @Test
        fun `Guest를 저장하고 조회하면 동일한 데이터가 반환된다`() {
            val guest = guestRepository.save(newGuest())
            val found = guestRepository.findById("guest-1")

            assertThat(found).isNotNull
            assertThat(found!!.name).isEqualTo("홍길동")
            assertThat(found.phone).isEqualTo("010-1234-5678")
            assertThat(found.tier).isEqualTo(GuestTier.NEW)
        }

        @Test
        fun `같은 숙소에서 동일 전화번호로 저장하면 DuplicateKeyException이 발생한다`() {
            guestRepository.save(newGuest(id = "guest-1", phone = "010-1234-5678"))

            assertThrows<DuplicateKeyException> {
                guestRepository.save(newGuest(id = "guest-2", phone = "010-1234-5678"))
            }
        }

        @Test
        fun `다른 숙소라면 동일 전화번호로 저장할 수 있다`() {
            guestRepository.save(newGuest(id = "guest-1", propertyId = "prop-1", phone = "010-1234-5678"))
            guestRepository.save(newGuest(id = "guest-2", propertyId = "prop-2", phone = "010-1234-5678"))

            assertThat(guestRepository.findById("guest-1")).isNotNull
            assertThat(guestRepository.findById("guest-2")).isNotNull
        }

        @Test
        fun `방문 기록이 포함된 Guest를 저장하고 조회하면 VisitSummary가 유지된다`() {
            val guest = guestRepository.save(newGuest())
            val visited = guest.recordVisit(Money.of(100_000), 2, LocalDate.of(2026, 3, 13))
            guestRepository.save(visited)

            val found = guestRepository.findById("guest-1")!!
            assertThat(found.visitSummary.totalVisits).isEqualTo(1)
            assertThat(found.visitSummary.totalSpend).isEqualTo(Money.of(100_000))
            assertThat(found.visitSummary.lastVisitDate).isEqualTo(LocalDate.of(2026, 3, 13))
        }
    }

    @Nested
    inner class FindByPropertyIdAndPhone {
        @Test
        fun `숙소ID와 전화번호로 Guest를 조회한다`() {
            guestRepository.save(newGuest())

            val found = guestRepository.findByPropertyIdAndPhone("prop-1", "010-1234-5678")
            assertThat(found).isNotNull
            assertThat(found!!.name).isEqualTo("홍길동")
        }

        @Test
        fun `존재하지 않는 전화번호면 null을 반환한다`() {
            val found = guestRepository.findByPropertyIdAndPhone("prop-1", "010-9999-9999")
            assertThat(found).isNull()
        }
    }

    @Nested
    inner class FindByPropertyId {
        @Test
        fun `숙소의 전체 Guest 목록을 반환한다`() {
            guestRepository.save(newGuest(id = "guest-1", name = "홍길동", phone = "010-1111-1111"))
            guestRepository.save(newGuest(id = "guest-2", name = "김철수", phone = "010-2222-2222"))

            val guests = guestRepository.findByPropertyId("prop-1")
            assertThat(guests).hasSize(2)
        }
    }

    @Nested
    inner class FindByPropertyIdAndTier {
        @Test
        fun `특정 등급의 Guest 목록을 반환한다`() {
            val guest1 = guestRepository.save(newGuest(id = "guest-1", phone = "010-1111-1111"))
            // BRONZE로 승급 (2회 방문)
            var bronzeGuest = guest1
            repeat(2) { bronzeGuest = bronzeGuest.recordVisit(Money.of(10_000), 1, LocalDate.now()) }
            guestRepository.save(bronzeGuest)

            guestRepository.save(newGuest(id = "guest-2", phone = "010-2222-2222"))

            val bronzeGuests = guestRepository.findByPropertyIdAndTier("prop-1", GuestTier.BRONZE)
            assertThat(bronzeGuests).hasSize(1)
            assertThat(bronzeGuests[0].name).isEqualTo("홍길동")
        }
    }

    @Nested
    inner class FindByPropertyIdAndNameContaining {
        @Test
        fun `이름으로 검색한다`() {
            guestRepository.save(newGuest(id = "guest-1", name = "홍길동", phone = "010-1111-1111"))
            guestRepository.save(newGuest(id = "guest-2", name = "김철수", phone = "010-2222-2222"))

            val result = guestRepository.findByPropertyIdAndNameContaining("prop-1", "홍")
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("홍길동")
        }
    }
}
