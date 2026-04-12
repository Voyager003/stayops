package com.stayops.member.application.service

import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.security.crypto.password.PasswordEncoder

class AuthApplicationTest : BehaviorSpec({

    val memberRepository = mockk<MemberRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()
    val idGenerator = object : IdGenerator {
        override fun generate() = "member-1"
    }
    val sut = AuthApplication(memberRepository, passwordEncoder, idGenerator)

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

                shouldThrow<ConflictException> {
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
            then("동일한 에러 메시지로 예외가 발생한다") {
                clearAllMocks()
                every { memberRepository.findByEmail("unknown@stayops.com") } returns null

                val exception = shouldThrow<BusinessException> {
                    sut.login("unknown@stayops.com", "password123")
                }
                exception.code shouldBe "INVALID_CREDENTIALS"
            }
        }

        `when`("비밀번호가 틀리면") {
            then("동일한 에러 메시지로 예외가 발생한다") {
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

                val exception = shouldThrow<BusinessException> {
                    sut.login("test@stayops.com", "wrong-password")
                }
                exception.code shouldBe "INVALID_CREDENTIALS"
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
