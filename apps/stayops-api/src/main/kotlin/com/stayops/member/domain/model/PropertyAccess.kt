package com.stayops.member.domain.model

data class PropertyAccess(
    val propertyId: String,
    val role: PropertyRole
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

enum class PropertyRole {
    OWNER,
    MANAGER
}
