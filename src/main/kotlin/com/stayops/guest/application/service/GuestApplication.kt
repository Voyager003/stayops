package com.stayops.guest.application.service

import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.model.GuestTier
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class GuestApplication(
    private val guestRepository: GuestRepository
) {
    fun getGuest(id: String): Guest =
        guestRepository.findById(id)
            ?: throw NotFoundException("GUEST_NOT_FOUND", "고객을 찾을 수 없습니다: $id")

    fun getGuests(propertyId: String, tier: GuestTier?, name: String?): List<Guest> = when {
        tier != null -> guestRepository.findByPropertyIdAndTier(propertyId, tier)
        name != null -> guestRepository.findByPropertyIdAndNameContaining(propertyId, name)
        else -> guestRepository.findByPropertyId(propertyId)
    }

    fun updateGuest(id: String, name: String, memo: String?): Guest {
        val guest = guestRepository.findById(id)
            ?: throw NotFoundException("GUEST_NOT_FOUND", "고객을 찾을 수 없습니다: $id")
        return guestRepository.save(guest.update(name = name, memo = memo))
    }
}
