package com.stayops.reservation.infrastructure.persistence

import com.stayops.reservation.domain.model.DateType
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.PagedResult
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
import java.time.ZoneId

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
            Criteria().andOperator(
                Criteria.where("status").`is`(ReservationStatus.PENDING.name),
                Criteria.where("expiresAt").lt(now),
                Criteria.where("expiresAt").ne(null)
            )
        )
        return mongoTemplate.find(query, ReservationDocument::class.java).map { it.toDomain() }
    }

    override fun countByPropertyIdAndCreatedDate(propertyId: String, date: LocalDate): Int {
        val zone = ZoneId.of("Asia/Seoul")
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val startOfNextDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        val query = Query(
            Criteria.where("propertyId").`is`(propertyId)
                .and("createdAt").gte(startOfDay).lt(startOfNextDay)
        )
        return mongoTemplate.count(query, ReservationDocument::class.java).toInt()
    }

    override fun existsByMemberIdAndRoomTypeIdAndCheckInAndCheckOutAndStatusIn(
        memberId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        statuses: List<ReservationStatus>
    ): Boolean {
        val query = Query(
            Criteria.where("memberId").`is`(memberId)
                .and("roomTypeId").`is`(roomTypeId)
                .and("dateRange.checkIn").`is`(checkIn.toString())
                .and("dateRange.checkOut").`is`(checkOut.toString())
                .and("status").`in`(statuses.map { it.name })
        )
        return mongoTemplate.exists(query, ReservationDocument::class.java)
    }

    override fun search(
        propertyId: String,
        criteria: ReservationSearchCriteria,
        page: Int,
        size: Int
    ): PagedResult<Reservation> {
        val criteriaList = mutableListOf(Criteria.where("propertyId").`is`(propertyId))

        criteria.statuses?.takeIf { it.isNotEmpty() }?.let {
            criteriaList.add(Criteria.where("status").`in`(it.map { s -> s.name }))
        }

        criteria.roomTypeId?.let {
            criteriaList.add(Criteria.where("roomTypeId").`is`(it))
        }

        criteria.channelCodes?.takeIf { it.isNotEmpty() }?.let {
            criteriaList.add(Criteria.where("channel.channelCode").`in`(it))
        }

        if (criteria.startDate != null && criteria.endDate != null) {
            val dateField = when (criteria.dateType ?: DateType.CHECK_IN) {
                DateType.CHECK_IN -> "dateRange.checkIn"
                DateType.CHECK_OUT -> "dateRange.checkOut"
                DateType.CREATED -> "createdAt"
            }
            if (dateField == "createdAt") {
                val startInstant = criteria.startDate.atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant()
                val endInstant = criteria.endDate.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant()
                criteriaList.add(Criteria.where(dateField).gte(startInstant).lt(endInstant))
            } else {
                criteriaList.add(Criteria.where(dateField).gte(criteria.startDate.toString()).lte(criteria.endDate.toString()))
            }
        }

        criteria.guestName?.takeIf { it.isNotBlank() }?.let {
            val escaped = java.util.regex.Pattern.quote(it)
            criteriaList.add(Criteria.where("guestInfo.name").regex("^$escaped", "i"))
        }

        val query = Query(Criteria().andOperator(*criteriaList.toTypedArray()))
        val totalElements = mongoTemplate.count(query, ReservationDocument::class.java)

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        query.with(pageable)

        val content = mongoTemplate.find(query, ReservationDocument::class.java).map { it.toDomain() }
        val totalPages = if (totalElements == 0L) 0 else ((totalElements - 1) / size + 1).toInt()

        return PagedResult(
            content = content,
            totalElements = totalElements,
            page = page,
            size = size,
            totalPages = totalPages
        )
    }
}

interface ReservationMongoDataRepository : MongoRepository<ReservationDocument, String> {
    fun findByPropertyId(propertyId: String): List<ReservationDocument>
    fun findByPropertyIdAndStatus(propertyId: String, status: ReservationStatus): List<ReservationDocument>
    fun findByPropertyIdAndGuestId(propertyId: String, guestId: String): List<ReservationDocument>
    fun findByPropertyIdAndChannelChannelCode(propertyId: String, channelCode: String): List<ReservationDocument>
    fun findByMemberId(memberId: String): List<ReservationDocument>
}
