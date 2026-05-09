package com.stayops.reservation.domain.model

data class GuestInfo(
    val name: String,
    val phone: String,
    val email: String?
) {
    init {
        require(name.isNotBlank()) { "고객 이름은 공백일 수 없습니다." }
        require(phone.isNotBlank()) { "전화번호는 공백일 수 없습니다." }
    }
}
