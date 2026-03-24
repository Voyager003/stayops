package com.stayops.shared.security

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.shared.exception.ForbiddenException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class CustomerAuthChecker {

    fun requireCustomer(): Member {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw ForbiddenException("NOT_AUTHENTICATED", "인증이 필요합니다.")
        val member = auth.principal as? Member
            ?: throw ForbiddenException("INVALID_PRINCIPAL", "유효하지 않은 인증 정보입니다.")
        if (member.role != MemberRole.CUSTOMER) {
            throw ForbiddenException("NOT_CUSTOMER", "고객 계정만 접근할 수 있습니다.")
        }
        return member
    }
}
