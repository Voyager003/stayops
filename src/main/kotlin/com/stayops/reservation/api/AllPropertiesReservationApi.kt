package com.stayops.reservation.api

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.api.dto.PagedReservationResponse
import com.stayops.reservation.api.dto.ReservationResponse
import com.stayops.reservation.application.service.ReservationApplication
import com.stayops.reservation.domain.model.DateType
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/reservations")
class AllPropertiesReservationApi(
    private val reservationApplication: ReservationApplication,
    private val propertyRepository: PropertyRepository
) {

    @GetMapping
    fun getAllPropertiesReservations(
        @RequestParam(required = false) status: List<ReservationStatus>?,
        @RequestParam(required = false) roomTypeId: String?,
        @RequestParam(required = false) channelCode: List<String>?,
        @RequestParam(required = false) dateType: DateType?,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
        @RequestParam(required = false) guestName: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedReservationResponse> {
        val member = SecurityContextHolder.getContext().authentication?.principal as Member
        val propertyIds = if (member.role == MemberRole.ADMIN) {
            propertyRepository.findAll().map { it.id }
        } else {
            member.propertyAccess.map { it.propertyId }
        }

        val criteria = ReservationSearchCriteria(
            statuses = status,
            roomTypeId = roomTypeId,
            channelCodes = channelCode,
            dateType = dateType,
            startDate = startDate,
            endDate = endDate,
            guestName = guestName
        )
        val result = reservationApplication.searchReservationsByPropertyIds(propertyIds, criteria, page, size)
        return ResponseEntity.ok(PagedReservationResponse(
            content = result.content.map { ReservationResponse.from(it) },
            totalElements = result.totalElements,
            page = result.page,
            size = result.size,
            totalPages = result.totalPages
        ))
    }
}
