package com.stayops.reservation.api.dto

data class PagedReservationResponse(
    val content: List<ReservationResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
)
