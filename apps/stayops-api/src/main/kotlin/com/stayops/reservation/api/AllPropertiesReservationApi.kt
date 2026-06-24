package com.stayops.reservation.api

import com.stayops.member.domain.model.Member
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.reservation.api.dto.PagedReservationResponse
import com.stayops.reservation.api.dto.ReservationResponse
import com.stayops.reservation.application.service.ReservationQueryApplication
import com.stayops.reservation.domain.model.DateType
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/reservations")
class AllPropertiesReservationApi(
    private val reservationQueryApplication: ReservationQueryApplication,
    private val memberAccessApplication: MemberAccessApplication
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
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<PagedReservationResponse> {
        val propertyIds = memberAccessApplication.resolveAccessiblePropertyIds(member)

        val criteria = ReservationSearchCriteria(
            statuses = status,
            roomTypeId = roomTypeId,
            channelCodes = channelCode,
            dateType = dateType,
            startDate = startDate,
            endDate = endDate,
            guestName = guestName
        )
        val result = reservationQueryApplication.searchReservationsByPropertyIds(propertyIds, criteria, page, size)
        return ResponseEntity.ok(PagedReservationResponse(
            content = result.content.map { ReservationResponse.from(it) },
            totalElements = result.totalElements,
            page = result.page,
            size = result.size,
            totalPages = result.totalPages
        ))
    }
}
