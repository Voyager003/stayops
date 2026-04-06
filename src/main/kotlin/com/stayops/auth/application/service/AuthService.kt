package com.stayops.auth.application.service

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.auth.domain.repository.MemberRepository
import com.stayops.auth.domain.model.MemberStatus
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun signup(email: String, password: String, name: String): Member {
        if (memberRepository.existsByEmail(email)) {
            throw ConflictException("DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다: $email")
        }

        val member = Member.create(
            id = UUID.randomUUID().toString(),
            email = email,
            passwordHash = passwordEncoder.encode(password)!!,
            name = name,
            role = MemberRole.OWNER
        )

        val saved = memberRepository.save(member)
        log.info("회원가입 완료: memberId={}, email={}, role=OWNER", saved.id, email)
        return saved
    }

    fun login(email: String, password: String): Member {
        val member = memberRepository.findByEmail(email)

        if (member == null || !passwordEncoder.matches(password, member.passwordHash)) {
            throw BusinessException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.")
        }

        if (member.status != MemberStatus.ACTIVE) {
            throw BusinessException("INACTIVE_MEMBER", "비활성화된 회원입니다.")
        }

        val loggedIn = member.recordLogin()
        val saved = memberRepository.save(loggedIn)
        log.info("로그인 성공: memberId={}, email={}", saved.id, email)
        return saved
    }

    fun logout(session: HttpSession) {
        log.info("로그아웃 처리")
        session.invalidate()
    }
}
