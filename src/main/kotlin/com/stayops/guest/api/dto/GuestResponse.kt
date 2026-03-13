package com.stayops.guest.api.dto

import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.model.GuestTier

data class GuestResponse(
    val id: String,
    val propertyId: String,
    val name: String,
    val phone: String,
    val email: String?,
    val tier: GuestTier,
    val memo: String?,
    val totalVisits: Int,
    val totalSpend: Long,
    val lastVisitDate: String?,
    val averageStayNights: Double
) {
    companion object {
        fun from(guest: Guest) = GuestResponse(
            id = guest.id,
            propertyId = guest.propertyId,
            name = guest.name,
            phone = guest.phone,
            email = guest.email,
            tier = guest.tier,
            memo = guest.memo,
            totalVisits = guest.visitSummary.totalVisits,
            totalSpend = guest.visitSummary.totalSpend.amount.toLong(),
            lastVisitDate = guest.visitSummary.lastVisitDate?.toString(),
            averageStayNights = guest.visitSummary.averageStayNights
        )
    }
}
