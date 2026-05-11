package com.stayops.guest.domain.model

import com.stayops.shared.domain.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@ConsistentCopyVisibility
data class Guest private constructor(
    val id: String,
    val propertyId: String,
    val name: String,
    val phone: String,
    val email: String?,
    val tier: GuestTier,
    val memo: String?,
    val visitSummary: VisitSummary,
    val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun recordVisit(spend: Money, stayNights: Int, visitDate: LocalDate): Guest {
        val updatedSummary = visitSummary.recordVisit(spend, stayNights, visitDate)
        return copy(
            visitSummary = updatedSummary,
            tier = calculateTier(updatedSummary),
            updatedAt = Instant.now()
        )
    }

    fun update(name: String, memo: String?): Guest {
        require(name.isNotBlank()) { "이름은 공백일 수 없습니다." }
        return copy(
            name = name,
            memo = memo,
            updatedAt = Instant.now()
        )
    }

    private fun calculateTier(summary: VisitSummary): GuestTier = when {
        summary.totalVisits >= 20 || summary.totalSpend.amount >= BigDecimal(5_000_000) -> GuestTier.VIP
        summary.totalVisits >= 10 -> GuestTier.GOLD
        summary.totalVisits >= 5  -> GuestTier.SILVER
        summary.totalVisits >= 2  -> GuestTier.BRONZE
        else                      -> GuestTier.NEW
    }

    companion object {
        fun create(
            id: String,
            propertyId: String,
            name: String,
            phone: String,
            email: String? = null,
            memo: String? = null,
        ): Guest {
            require(name.isNotBlank()) { "이름은 공백일 수 없습니다." }
            require(phone.isNotBlank()) { "전화번호는 공백일 수 없습니다." }
            val now = Instant.now()
            return Guest(
                id = id,
                propertyId = propertyId,
                name = name,
                phone = phone,
                email = email,
                tier = GuestTier.NEW,
                memo = memo,
                visitSummary = VisitSummary.EMPTY,
                version = null,
                createdAt = now,
                updatedAt = now
            )
        }

        fun reconstitute(
            id: String,
            propertyId: String,
            name: String,
            phone: String,
            email: String?,
            tier: GuestTier,
            memo: String?,
            visitSummary: VisitSummary,
            version: Long?,
            createdAt: Instant,
            updatedAt: Instant
        ): Guest = Guest(
            id = id,
            propertyId = propertyId,
            name = name,
            phone = phone,
            email = email,
            tier = tier,
            memo = memo,
            visitSummary = visitSummary,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
