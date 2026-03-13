package com.stayops.guest.application.service

import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.model.GuestTier
import com.stayops.guest.domain.model.VisitSummary
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDate

class GuestApplicationTest : BehaviorSpec({

    val guestRepository = mockk<GuestRepository>()
    val guestApplication = GuestApplication(guestRepository)

    fun testGuest(
        id: String = "guest-1",
        propertyId: String = "prop-1",
        name: String = "홍길동",
        phone: String = "010-1234-5678",
    ): Guest = Guest.create(
        id = id,
        propertyId = propertyId,
        name = name,
        phone = phone,
    )

    // ─────────────────────────────────────────
    // 단건 조회
    // ─────────────────────────────────────────

    given("Guest 단건 조회 시") {
        `when`("존재하는 ID로 조회하면") {
            val guest = testGuest()
            every { guestRepository.findById("guest-1") } returns guest
            val result = guestApplication.getGuest("guest-1")

            then("Guest를 반환한다") {
                result.name shouldBe "홍길동"
            }
        }

        `when`("존재하지 않는 ID로 조회하면") {
            every { guestRepository.findById("unknown") } returns null

            then("NotFoundException이 발생한다") {
                shouldThrow<NotFoundException> {
                    guestApplication.getGuest("unknown")
                }
            }
        }
    }

    // ─────────────────────────────────────────
    // 목록 조회
    // ─────────────────────────────────────────

    given("Guest 목록 조회 시") {
        `when`("tier 필터가 있으면") {
            val guests = listOf(testGuest())
            every { guestRepository.findByPropertyIdAndTier("prop-1", GuestTier.BRONZE) } returns guests
            val result = guestApplication.getGuests("prop-1", tier = GuestTier.BRONZE, name = null)

            then("해당 등급의 Guest 목록을 반환한다") {
                result.size shouldBe 1
            }
        }

        `when`("name 필터가 있으면") {
            val guests = listOf(testGuest())
            every { guestRepository.findByPropertyIdAndNameContaining("prop-1", "홍") } returns guests
            val result = guestApplication.getGuests("prop-1", tier = null, name = "홍")

            then("이름이 포함된 Guest 목록을 반환한다") {
                result.size shouldBe 1
            }
        }

        `when`("필터가 없으면") {
            val guests = listOf(testGuest(id = "g-1", phone = "010-1111-1111"), testGuest(id = "g-2", phone = "010-2222-2222"))
            every { guestRepository.findByPropertyId("prop-1") } returns guests
            val result = guestApplication.getGuests("prop-1", tier = null, name = null)

            then("숙소의 전체 Guest 목록을 반환한다") {
                result.size shouldBe 2
            }
        }
    }

    // ─────────────────────────────────────────
    // 정보 수정
    // ─────────────────────────────────────────

    given("Guest 정보 수정 시") {
        `when`("유효한 정보로 수정하면") {
            val guest = testGuest()
            every { guestRepository.findById("guest-1") } returns guest
            val savedSlot = slot<Guest>()
            every { guestRepository.save(capture(savedSlot)) } answers { firstArg() }

            val result = guestApplication.updateGuest("guest-1", name = "김철수", memo = "VIP 고객")

            then("이름과 메모가 변경된다") {
                result.name shouldBe "김철수"
                result.memo shouldBe "VIP 고객"
                savedSlot.captured.name shouldBe "김철수"
            }
        }

        `when`("존재하지 않는 Guest를 수정하면") {
            every { guestRepository.findById("unknown") } returns null

            then("NotFoundException이 발생한다") {
                shouldThrow<NotFoundException> {
                    guestApplication.updateGuest("unknown", name = "김철수", memo = null)
                }
            }
        }
    }
})
