package com.stayops.member.infrastructure.security

import com.stayops.member.domain.model.Member
import com.stayops.shared.exception.ForbiddenException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class PropertyAccessChecker {

    fun requireAccess(propertyId: String) {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw ForbiddenException("NOT_AUTHENTICATED", "인증이 필요합니다.")
        val member = auth.principal as? Member
            ?: throw ForbiddenException("INVALID_PRINCIPAL", "유효하지 않은 인증 정보입니다.")
        if (!member.hasAccessTo(propertyId)) {
            throw ForbiddenException("ACCESS_DENIED", "해당 숙소에 접근 권한이 없습니다: $propertyId")
        }
    }
}
