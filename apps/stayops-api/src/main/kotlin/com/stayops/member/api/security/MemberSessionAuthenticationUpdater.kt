package com.stayops.member.api.security

import com.stayops.member.domain.model.Member
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Component

@Component
class MemberSessionAuthenticationUpdater {

    private val securityContextRepository = HttpSessionSecurityContextRepository()

    fun update(member: Member, request: HttpServletRequest, response: HttpServletResponse) {
        val authentication = UsernamePasswordAuthenticationToken(member, null, emptyList())
        val context = SecurityContextHolder.getContext()
        context.authentication = authentication
        securityContextRepository.saveContext(context, request, response)
    }
}
