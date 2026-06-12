package com.stayops.member.application.service

import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.exception.ForbiddenException
import org.springframework.stereotype.Service

@Service
class MemberAccessApplication(
    private val propertyRepository: PropertyRepository
) {

    fun requirePropertyAccess(member: Member?, propertyId: String) {
        val authenticatedMember = requireAuthenticated(member)
        if (!authenticatedMember.hasAccessTo(propertyId)) {
            throw ForbiddenException("ACCESS_DENIED", "해당 숙소에 접근 권한이 없습니다: $propertyId")
        }
    }

    fun resolveAccessiblePropertyIds(member: Member?): List<String> {
        val authenticatedMember = requireAuthenticated(member)
        return if (authenticatedMember.role == MemberRole.ADMIN) {
            propertyRepository.findAll().map { it.id }
        } else {
            authenticatedMember.propertyAccess.map { it.propertyId }
        }
    }

    fun requireCustomer(member: Member?): Member {
        val authenticatedMember = requireAuthenticated(member)
        if (authenticatedMember.role != MemberRole.CUSTOMER) {
            throw ForbiddenException("NOT_CUSTOMER", "고객 계정만 접근할 수 있습니다.")
        }
        return authenticatedMember
    }

    fun requireAuthenticatedMember(member: Member?): Member = requireAuthenticated(member)

    private fun requireAuthenticated(member: Member?): Member =
        member ?: throw ForbiddenException("NOT_AUTHENTICATED", "인증이 필요합니다.")
}
