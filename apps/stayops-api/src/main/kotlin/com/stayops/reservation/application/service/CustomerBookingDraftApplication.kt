package com.stayops.reservation.application.service

import com.stayops.shared.domain.IdGenerator
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.LocalDate

@Service
class CustomerBookingDraftApplication(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val idGenerator: IdGenerator
) {
    companion object {
        private const val KEY_PREFIX = "booking:draft:"
        private val DRAFT_TTL: Duration = Duration.ofMinutes(30)
    }

    fun createDraft(
        propertyId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int
    ): CustomerBookingDraft {
        val draftId = idGenerator.generate()
        val draft = CustomerBookingDraft(
            draftId = draftId,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            checkIn = checkIn,
            checkOut = checkOut,
            guests = guests
        )

        redisTemplate.opsForValue().set(
            redisKey(draftId),
            objectMapper.writeValueAsString(draft.toStoredValue()),
            DRAFT_TTL
        )

        return draft
    }

    fun getDraft(draftId: String): CustomerBookingDraft? {
        val raw = redisTemplate.opsForValue().get(redisKey(draftId)) ?: return null
        val stored = objectMapper.readValue(raw, StoredCustomerBookingDraft::class.java)
        return CustomerBookingDraft(
            draftId = draftId,
            propertyId = stored.propertyId,
            roomTypeId = stored.roomTypeId,
            checkIn = stored.checkIn,
            checkOut = stored.checkOut,
            guests = stored.guests
        )
    }

    fun deleteDraft(draftId: String) {
        redisTemplate.delete(redisKey(draftId))
    }

    private fun redisKey(draftId: String): String = "$KEY_PREFIX$draftId"
}

data class CustomerBookingDraft(
    val draftId: String,
    val propertyId: String,
    val roomTypeId: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guests: Int
) {
    fun toStoredValue(): StoredCustomerBookingDraft =
        StoredCustomerBookingDraft(
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            checkIn = checkIn,
            checkOut = checkOut,
            guests = guests
        )
}

data class StoredCustomerBookingDraft(
    val propertyId: String,
    val roomTypeId: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guests: Int
)
