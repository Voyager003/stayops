package com.stayops.payment.domain.repository

import com.stayops.payment.domain.model.Payment

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findById(id: String): Payment?
    fun findByReservationId(reservationId: String): Payment?
    fun findByReservationIds(reservationIds: List<String>): List<Payment>
    fun findByMemberId(memberId: String): List<Payment>
    fun findByOrderId(orderId: String): Payment?
}
