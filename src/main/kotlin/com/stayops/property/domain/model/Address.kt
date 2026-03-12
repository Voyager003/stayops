package com.stayops.property.domain.model

@ConsistentCopyVisibility
data class Address private constructor(
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String
) {
    init {
        require(street.isNotBlank()) { "street은 비어있을 수 없습니다" }
        require(city.isNotBlank()) { "city는 비어있을 수 없습니다" }
        require(state.isNotBlank()) { "state는 비어있을 수 없습니다" }
        require(zipCode.isNotBlank()) { "zipCode는 비어있을 수 없습니다" }
        require(country.isNotBlank()) { "country는 비어있을 수 없습니다" }
    }

    companion object {
        fun of(
            street: String,
            city: String,
            state: String,
            zipCode: String,
            country: String
        ): Address = Address(street, city, state, zipCode, country)
    }
}
