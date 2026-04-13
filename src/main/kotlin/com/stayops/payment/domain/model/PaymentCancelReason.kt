package com.stayops.payment.domain.model

enum class PaymentCancelReason(val message: String) {
    CUSTOMER_REQUEST("고객 요청에 의한 취소"),
    INVENTORY_UNAVAILABLE("재고 부족으로 예약 확정 실패")
}
