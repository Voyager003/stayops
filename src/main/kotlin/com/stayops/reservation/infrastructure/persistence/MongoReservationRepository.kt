package com.stayops.reservation.infrastructure.persistence

import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
class MongoReservationRepository(
    private val mongo: ReservationMongoDataRepository,
    private val mongoTemplate: MongoTemplate
) : ReservationRepository {

    @PostConstruct
    fun createIndexes() {
        val indexOps = mongoTemplate.indexOps(ReservationDocument::class.java)

        indexOps.createIndex(
            CompoundIndexDefinition(
                Document(mapOf("propertyId" to 1, "status" to 1, "dateRange.checkIn" to 1))
            )
        )
        indexOps.createIndex(
            CompoundIndexDefinition(
                Document(mapOf("propertyId" to 1, "channel.channelCode" to 1))
            )
        )
        indexOps.createIndex(
            CompoundIndexDefinition(
                Document(mapOf("propertyId" to 1, "guestId" to 1))
            )
        )
    }

    override fun save(reservation: Reservation): Reservation =
        mongo.save(ReservationDocument.from(reservation)).toDomain()

    override fun findById(id: String): Reservation? =
        mongo.findById(id).orElse(null)?.toDomain()

    override fun findByPropertyId(propertyId: String): List<Reservation> =
        mongo.findByPropertyId(propertyId).map { it.toDomain() }

    override fun findByPropertyIdAndStatus(propertyId: String, status: ReservationStatus): List<Reservation> =
        mongo.findByPropertyIdAndStatus(propertyId, status).map { it.toDomain() }

    override fun findByPropertyIdAndDateRange(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Reservation> {
        val query = Query(
            Criteria.where("propertyId").`is`(propertyId)
                .and("dateRange.checkIn").lte(endDate.toString())
                .and("dateRange.checkOut").gte(startDate.toString())
        )
        return mongoTemplate.find(query, ReservationDocument::class.java).map { it.toDomain() }
    }

    override fun findByPropertyIdAndGuestId(propertyId: String, guestId: String): List<Reservation> =
        mongo.findByPropertyIdAndGuestId(propertyId, guestId).map { it.toDomain() }

    override fun findByPropertyIdAndChannelCode(propertyId: String, channelCode: String): List<Reservation> =
        mongo.findByPropertyIdAndChannelChannelCode(propertyId, channelCode).map { it.toDomain() }

    override fun findByMemberId(memberId: String): List<Reservation> =
        mongo.findByMemberId(memberId).map { it.toDomain() }

    override fun findExpiredPending(now: Instant): List<Reservation> {
        val query = Query(
            Criteria.where("status").`is`(ReservationStatus.PENDING.name)
                .and("expiresAt").lt(now)
                .and("expiresAt").ne(null)
        )
        return mongoTemplate.find(query, ReservationDocument::class.java).map { it.toDomain() }
    }
}

interface ReservationMongoDataRepository : MongoRepository<ReservationDocument, String> {
    fun findByPropertyId(propertyId: String): List<ReservationDocument>
    fun findByPropertyIdAndStatus(propertyId: String, status: ReservationStatus): List<ReservationDocument>
    fun findByPropertyIdAndGuestId(propertyId: String, guestId: String): List<ReservationDocument>
    fun findByPropertyIdAndChannelChannelCode(propertyId: String, channelCode: String): List<ReservationDocument>
    fun findByMemberId(memberId: String): List<ReservationDocument>
}
