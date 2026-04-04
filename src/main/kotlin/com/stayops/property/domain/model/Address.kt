package com.stayops.property.domain.model

@ConsistentCopyVisibility
data class Address private constructor(
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String,
    val latitude: Double?,
    val longitude: Double?
) {
    init {
        require(street.isNotBlank()) { "street은 비어있을 수 없습니다" }
        require(city.isNotBlank()) { "city는 비어있을 수 없습니다" }
        require(state.isNotBlank()) { "state는 비어있을 수 없습니다" }
        require(zipCode.isNotBlank()) { "zipCode는 비어있을 수 없습니다" }
        require(country.isNotBlank()) { "country는 비어있을 수 없습니다" }
        latitude?.let {
            require(it in -90.0..90.0) { "위도는 -90 ~ 90 범위여야 합니다: $it" }
        }
        longitude?.let {
            require(it in -180.0..180.0) { "경도는 -180 ~ 180 범위여야 합니다: $it" }
        }
    }

    companion object {
        fun of(
            street: String,
            city: String,
            state: String,
            zipCode: String,
            country: String,
            latitude: Double? = null,
            longitude: Double? = null
        ): Address = Address(street, city, state, zipCode, country, latitude, longitude)
    }
}
