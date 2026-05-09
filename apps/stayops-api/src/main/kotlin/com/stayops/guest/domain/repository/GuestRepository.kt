package com.stayops.guest.domain.repository

import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.model.GuestTier

interface GuestRepository {
    fun save(guest: Guest): Guest
    fun findById(id: String): Guest?
    fun findByPropertyIdAndPhone(propertyId: String, phone: String): Guest?
    fun findByPropertyId(propertyId: String): List<Guest>
    fun findByPropertyIdAndTier(propertyId: String, tier: GuestTier): List<Guest>
    fun findByPropertyIdAndNameContaining(propertyId: String, name: String): List<Guest>
}
