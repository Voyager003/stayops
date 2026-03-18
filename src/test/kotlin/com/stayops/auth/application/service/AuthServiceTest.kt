package com.stayops.auth.application.service

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.auth.domain.model.MemberStatus
import com.stayops.auth.domain.repository.MemberRepository
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.security.crypto.password.PasswordEncoder

class AuthServiceTest : BehaviorSpec({

    val memberRepository = mockk<MemberRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()
    val sut = AuthService(memberRepository, passwordEncoder)

    given("회원가입 시") {

        `when`("유효한 정보로 가입하면") {
            then("Member가 생성된다") {
                clearAllMocks()
                every { memberRepository.existsByEmail("new@stayops.com") } returns false
                every { passwordEncoder.encode("password123") } returns "hashed-password"
                every { memberRepository.save(any()) } answers { firstArg() }

                val result = sut.signup(
                    email = "new@stayops.com",
                    password = "password123",
                    name = "홍길동"
                )

                result.email shouldBe "new@stayops.com"
                result.role shouldBe MemberRole.OWNER
                result.status shouldBe MemberStatus.ACTIVE
                verify { memberRepository.save(any()) }
            }
        }

        `when`("이미 존재하는 이메일이면") {
            then("예외가 발생한다") {
                clearAllMocks()
                every { memberRepository.existsByEmail("existing@stayops.com") } returns true

                shouldThrow<BusinessException> {
                    sut.signup(
                        email = "existing@stayops.com",
                        password = "password123",
                        name = "홍길동"
                    )
                }
            }
        }
    }

    given("로그인 시") {

        `when`("유효한 이메일과 비밀번호이면") {
            then("Member를 반환하고 로그인 시간을 기록한다") {
                clearAllMocks()
                val member = Member.create(
                    id = "member-1",
                    email = "test@stayops.com",
                    passwordHash = "hashed-password",
                    name = "홍길동",
                    role = MemberRole.OWNER
                )
                every { memberRepository.findByEmail("test@stayops.com") } returns member
                every { passwordEncoder.matches("password123", "hashed-password") } returns true
                every { memberRepository.save(any()) } answers { firstArg() }

                val result = sut.login("test@stayops.com", "password123")

                result.email shouldBe "test@stayops.com"
                result.lastLoginAt shouldNotBe null
                verify { memberRepository.save(any()) }
            }
        }

        `when`("존재하지 않는 이메일이면") {
            then("예외가 발생한다") {
                clearAllMocks()
                every { memberRepository.findByEmail("unknown@stayops.com") } returns null

                shouldThrow<NotFoundException> {
                    sut.login("unknown@stayops.com", "password123")
                }
            }
        }

        `when`("비밀번호가 틀리면") {
            then("예외가 발생한다") {
                clearAllMocks()
                val member = Member.create(
                    id = "member-1",
                    email = "test@stayops.com",
                    passwordHash = "hashed-password",
                    name = "홍길동",
                    role = MemberRole.OWNER
                )
                every { memberRepository.findByEmail("test@stayops.com") } returns member
                every { passwordEncoder.matches("wrong-password", "hashed-password") } returns false

                shouldThrow<BusinessException> {
                    sut.login("test@stayops.com", "wrong-password")
                }
            }
        }

        `when`("비활성화된 회원이면") {
            then("예외가 발생한다") {
                clearAllMocks()
                val member = Member.create(
                    id = "member-1",
                    email = "test@stayops.com",
                    passwordHash = "hashed-password",
                    name = "홍길동",
                    role = MemberRole.OWNER
                ).deactivate()
                every { memberRepository.findByEmail("test@stayops.com") } returns member
                every { passwordEncoder.matches("password123", "hashed-password") } returns true

                shouldThrow<BusinessException> {
                    sut.login("test@stayops.com", "password123")
                }
            }
        }
    }
})
