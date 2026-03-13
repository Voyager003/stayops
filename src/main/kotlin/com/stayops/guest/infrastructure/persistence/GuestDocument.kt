package com.stayops.guest.infrastructure.persistence

import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.model.GuestTier
import com.stayops.guest.domain.model.VisitSummary
import com.stayops.shared.domain.Money
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.time.LocalDate

@Document("guests")
@CompoundIndex(def = "{'propertyId': 1, 'phone': 1}", unique = true)
data class GuestDocument(
    @Id val id: String,
    val propertyId: String,
    val name: String,
    val phone: String,
    val email: String?,
    val tier: GuestTier,
    val memo: String?,
    val totalVisits: Int,
    val totalSpendAmount: Long,
    val lastVisitDate: String?,
    val averageStayNights: Double,
    @Version val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toDomain(): Guest = Guest.reconstitute(
        id = id,
        propertyId = propertyId,
        name = name,
        phone = phone,
        email = email,
        tier = tier,
        memo = memo,
        visitSummary = VisitSummary(
            totalVisits = totalVisits,
            totalSpend = Money.of(totalSpendAmount),
            lastVisitDate = lastVisitDate?.let { LocalDate.parse(it) },
            averageStayNights = averageStayNights
        ),
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(guest: Guest): GuestDocument = GuestDocument(
            id = guest.id,
            propertyId = guest.propertyId,
            name = guest.name,
            phone = guest.phone,
            email = guest.email,
            tier = guest.tier,
            memo = guest.memo,
            totalVisits = guest.visitSummary.totalVisits,
            totalSpendAmount = guest.visitSummary.totalSpend.amount.toLong(),
            lastVisitDate = guest.visitSummary.lastVisitDate?.toString(),
            averageStayNights = guest.visitSummary.averageStayNights,
            version = guest.version,
            createdAt = guest.createdAt,
            updatedAt = guest.updatedAt
        )
    }
}
