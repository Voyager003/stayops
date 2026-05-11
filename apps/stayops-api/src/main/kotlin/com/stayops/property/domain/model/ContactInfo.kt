package com.stayops.property.domain.model

@ConsistentCopyVisibility
data class ContactInfo private constructor(
    val phone: String,
    val email: String,
    val website: String? = null
) {
    init {
        require(phone.isNotBlank()) { "phone은 비어있을 수 없습니다" }
        require(email.isNotBlank()) { "email은 비어있을 수 없습니다" }
    }

    companion object {
        fun of(
            phone: String,
            email: String,
            website: String? = null
        ): ContactInfo = ContactInfo(phone, email, website)
    }
}
