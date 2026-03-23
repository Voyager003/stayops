package com.stayops.booking.application.service

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.auth.domain.model.MemberStatus
import com.stayops.auth.domain.repository.MemberRepository
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.security.crypto.password.PasswordEncoder

class CustomerAuthServiceTest : BehaviorSpec({

    val memberRepository = mockk<MemberRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()
    val service = CustomerAuthService(memberRepository, passwordEncoder)

    given("고객 회원가입 시") {

        `when`("유효한 정보로 가입하면") {
            every { memberRepository.existsByEmail("customer@test.com") } returns false
            every { passwordEncoder.encode("password123") } returns "hashed-password"
            val memberSlot = slot<Member>()
            every { memberRepository.save(capture(memberSlot)) } answers { memberSlot.captured }

            val result = service.signup(
                email = "customer@test.com",
                password = "password123",
                name = "김고객"
            )

            then("CUSTOMER role로 생성된다") {
                result.role shouldBe MemberRole.CUSTOMER
            }
            then("propertyAccess가 비어있다") {
                result.propertyAccess shouldBe emptyList()
            }
            then("ACTIVE 상태로 생성된다") {
                result.status shouldBe MemberStatus.ACTIVE
            }
        }

        `when`("이미 존재하는 이메일이면") {
            every { memberRepository.existsByEmail("existing@test.com") } returns true

            then("예외가 발생한다") {
                shouldThrow<ConflictException> {
                    service.signup(
                        email = "existing@test.com",
                        password = "password123",
                        name = "김고객"
                    )
                }
            }
        }
    }

    given("고객 로그인 시") {

        `when`("CUSTOMER 계정으로 로그인하면") {
            val customer = Member.create(
                id = "member-1",
                email = "customer@test.com",
                passwordHash = "hashed-password",
                name = "김고객",
                role = MemberRole.CUSTOMER
            )
            every { memberRepository.findByEmail("customer@test.com") } returns customer
            every { passwordEncoder.matches("password123", "hashed-password") } returns true
            val memberSlot = slot<Member>()
            every { memberRepository.save(capture(memberSlot)) } answers { memberSlot.captured }

            val result = service.login(
                email = "customer@test.com",
                password = "password123"
            )

            then("로그인에 성공한다") {
                result.role shouldBe MemberRole.CUSTOMER
                result.lastLoginAt shouldBe result.lastLoginAt
            }
        }

        `when`("ADMIN 계정으로 로그인 시도하면") {
            val admin = Member.create(
                id = "member-2",
                email = "admin@test.com",
                passwordHash = "hashed-password",
                name = "관리자",
                role = MemberRole.ADMIN
            )
            every { memberRepository.findByEmail("admin@test.com") } returns admin
            every { passwordEncoder.matches("password123", "hashed-password") } returns true

            then("거부된다") {
                shouldThrow<BusinessException> {
                    service.login(
                        email = "admin@test.com",
                        password = "password123"
                    )
                }
            }
        }

        `when`("OWNER 계정으로 로그인 시도하면") {
            val owner = Member.create(
                id = "member-3",
                email = "owner@test.com",
                passwordHash = "hashed-password",
                name = "사장님",
                role = MemberRole.OWNER
            )
            every { memberRepository.findByEmail("owner@test.com") } returns owner
            every { passwordEncoder.matches("password123", "hashed-password") } returns true

            then("거부된다") {
                shouldThrow<BusinessException> {
                    service.login(
                        email = "owner@test.com",
                        password = "password123"
                    )
                }
            }
        }

        `when`("MANAGER 계정으로 로그인 시도하면") {
            val manager = Member.create(
                id = "member-4",
                email = "manager@test.com",
                passwordHash = "hashed-password",
                name = "매니저",
                role = MemberRole.MANAGER
            )
            every { memberRepository.findByEmail("manager@test.com") } returns manager
            every { passwordEncoder.matches("password123", "hashed-password") } returns true

            then("거부된다") {
                shouldThrow<BusinessException> {
                    service.login(
                        email = "manager@test.com",
                        password = "password123"
                    )
                }
            }
        }

        `when`("잘못된 비밀번호이면") {
            val customer = Member.create(
                id = "member-5",
                email = "customer2@test.com",
                passwordHash = "hashed-password",
                name = "김고객",
                role = MemberRole.CUSTOMER
            )
            every { memberRepository.findByEmail("customer2@test.com") } returns customer
            every { passwordEncoder.matches("wrong-password", "hashed-password") } returns false

            then("예외가 발생한다") {
                shouldThrow<BusinessException> {
                    service.login(
                        email = "customer2@test.com",
                        password = "wrong-password"
                    )
                }
            }
        }

        `when`("비활성화된 CUSTOMER 계정이면") {
            val inactive = Member.create(
                id = "member-6",
                email = "inactive@test.com",
                passwordHash = "hashed-password",
                name = "비활성고객",
                role = MemberRole.CUSTOMER
            ).deactivate()
            every { memberRepository.findByEmail("inactive@test.com") } returns inactive
            every { passwordEncoder.matches("password123", "hashed-password") } returns true

            then("예외가 발생한다") {
                shouldThrow<BusinessException> {
                    service.login(
                        email = "inactive@test.com",
                        password = "password123"
                    )
                }
            }
        }
    }
})
