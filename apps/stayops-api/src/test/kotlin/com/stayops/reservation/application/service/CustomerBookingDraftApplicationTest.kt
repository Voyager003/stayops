package com.stayops.reservation.application.service

import com.stayops.shared.domain.IdGenerator
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.LocalDate

class CustomerBookingDraftApplicationTest : BehaviorSpec({

    val redisTemplate = mockk<StringRedisTemplate>()
    val valueOperations = mockk<ValueOperations<String, String>>()
    val idGenerator = object : IdGenerator {
        override fun generate(): String = "draft-1"
    }
    val objectMapper = ObjectMapper()
    val sut = CustomerBookingDraftApplication(redisTemplate, objectMapper, idGenerator)

    beforeTest {
        every { redisTemplate.opsForValue() } returns valueOperations
    }

    given("예약 draft 생성 시") {
        `when`("숙소/객실/날짜/인원만 전달하면") {
            then("Redis에 30분 TTL로 저장하고 draftId를 반환한다") {
                val keySlot = slot<String>()
                val valueSlot = slot<String>()
                val ttlSlot = slot<Duration>()
                every { valueOperations.set(capture(keySlot), capture(valueSlot), capture(ttlSlot)) } just runs

                val result = sut.createDraft(
                    propertyId = "prop-1",
                    roomTypeId = "rt-1",
                    checkIn = LocalDate.of(2026, 6, 10),
                    checkOut = LocalDate.of(2026, 6, 12),
                    guests = 2
                )

                result.draftId shouldBe "draft-1"
                keySlot.captured shouldBe "booking:draft:draft-1"
                ttlSlot.captured shouldBe Duration.ofMinutes(30)
                valueSlot.captured.contains("guestName") shouldBe false
                valueSlot.captured.contains("guestPhone") shouldBe false
                valueSlot.captured.contains("guestEmail") shouldBe false
            }
        }
    }

    given("예약 draft 조회 시") {
        `when`("Redis에 draft가 있으면") {
            then("저장된 선택 정보를 반환한다") {
                every { valueOperations.get("booking:draft:draft-1") } returns """
                    {
                      "propertyId": "prop-1",
                      "roomTypeId": "rt-1",
                      "checkIn": "2026-06-10",
                      "checkOut": "2026-06-12",
                      "guests": 2
                    }
                """.trimIndent()

                val result = sut.getDraft("draft-1")

                result!!.propertyId shouldBe "prop-1"
                result.roomTypeId shouldBe "rt-1"
                result.checkIn shouldBe LocalDate.of(2026, 6, 10)
                result.checkOut shouldBe LocalDate.of(2026, 6, 12)
                result.guests shouldBe 2
            }
        }

        `when`("Redis에 draft가 없으면") {
            then("null을 반환한다") {
                every { valueOperations.get("booking:draft:missing") } returns null

                sut.getDraft("missing") shouldBe null
            }
        }
    }

    given("예약 draft 삭제 시") {
        `when`("draftId를 전달하면") {
            then("Redis key를 삭제한다") {
                every { redisTemplate.delete("booking:draft:draft-1") } returns true

                sut.deleteDraft("draft-1")

                verify { redisTemplate.delete("booking:draft:draft-1") }
            }
        }
    }
})
