package com.stayops.auth.domain.model

data class PropertyAccess(
    val propertyId: String,
    val role: PropertyRole
)

enum class PropertyRole {
    OWNER,
    MANAGER
}
