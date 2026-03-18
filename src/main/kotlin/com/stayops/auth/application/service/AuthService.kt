package com.stayops.auth.application.service

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.auth.domain.repository.MemberRepository
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.NotFoundException
import jakarta.servlet.http.HttpSession
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun signup(email: String, password: String, name: String): Member {
        if (memberRepository.existsByEmail(email)) {
            throw BusinessException("DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다: $email")
        }

        val member = Member.create(
            id = UUID.randomUUID().toString(),
            email = email,
            passwordHash = passwordEncoder.encode(password)!!,
            name = name,
            role = MemberRole.OWNER
        )

        return memberRepository.save(member)
    }

    fun login(email: String, password: String): Member {
        val member = memberRepository.findByEmail(email)
            ?: throw NotFoundException("MEMBER_NOT_FOUND", "존재하지 않는 이메일입니다: $email")

        if (!passwordEncoder.matches(password, member.passwordHash)) {
            throw BusinessException("INVALID_PASSWORD", "비밀번호가 일치하지 않습니다.")
        }

        if (member.status != com.stayops.auth.domain.model.MemberStatus.ACTIVE) {
            throw BusinessException("INACTIVE_MEMBER", "비활성화된 회원입니다.")
        }

        val loggedIn = member.recordLogin()
        return memberRepository.save(loggedIn)
    }

    fun logout(session: HttpSession) {
        session.invalidate()
    }
}
