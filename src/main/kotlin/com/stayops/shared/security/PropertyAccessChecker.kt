package com.stayops.shared.security

import com.stayops.auth.domain.model.Member
import com.stayops.shared.exception.ForbiddenException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class PropertyAccessChecker {

    fun requireAccess(propertyId: String) {
        val auth = SecurityContextHolder.getContext().authentication ?: return
        val member = auth.principal as? Member ?: return
        if (!member.hasAccessTo(propertyId)) {
            throw ForbiddenException("ACCESS_DENIED", "해당 숙소에 접근 권한이 없습니다: $propertyId")
        }
    }
}
