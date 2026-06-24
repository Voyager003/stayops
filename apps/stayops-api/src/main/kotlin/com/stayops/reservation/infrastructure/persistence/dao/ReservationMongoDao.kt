package com.stayops.reservation.infrastructure.persistence.dao

import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.infrastructure.persistence.ReservationDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface ReservationMongoDao : MongoRepository<ReservationDocument, String> {
    fun findByPropertyId(propertyId: String): List<ReservationDocument>
    fun findByPropertyIdAndStatus(propertyId: String, status: ReservationStatus): List<ReservationDocument>
    fun findByPropertyIdAndGuestId(propertyId: String, guestId: String): List<ReservationDocument>
    fun findByPropertyIdAndChannelChannelCode(propertyId: String, channelCode: String): List<ReservationDocument>
    fun findByPropertyIdAndChannelChannelCodeAndChannelExternalReservationId(
        propertyId: String,
        channelCode: String,
        externalReservationId: String
    ): ReservationDocument?
    fun findByMemberId(memberId: String): List<ReservationDocument>
}
