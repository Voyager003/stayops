package com.stayops.member.application.service

import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.PropertyRole
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.exception.ForbiddenException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class MemberAccessApplicationTest : BehaviorSpec({

    val propertyRepository = mockk<PropertyRepository>()
    val sut = MemberAccessApplication(propertyRepository)

    given("숙소 접근 권한을 확인할 때") {
        `when`("인증된 회원이 숙소 접근 권한을 가지고 있으면") {
            then("예외 없이 통과한다") {
                val owner = ownerWithAccess("prop-1")

                sut.requirePropertyAccess(owner, "prop-1")
            }
        }

        `when`("인증 정보가 없으면") {
            then("NOT_AUTHENTICATED 예외가 발생한다") {
                val exception = shouldThrow<ForbiddenException> {
                    sut.requirePropertyAccess(null, "prop-1")
                }

                exception.code shouldBe "NOT_AUTHENTICATED"
            }
        }

        `when`("숙소 접근 권한이 없으면") {
            then("ACCESS_DENIED 예외가 발생한다") {
                val owner = ownerWithAccess("prop-2")

                val exception = shouldThrow<ForbiddenException> {
                    sut.requirePropertyAccess(owner, "prop-1")
                }

                exception.code shouldBe "ACCESS_DENIED"
            }
        }
    }

    given("접근 가능한 숙소 목록을 계산할 때") {
        `when`("ADMIN이면") {
            then("전체 숙소 ID를 반환한다") {
                every { propertyRepository.findAll() } returns listOf(
                    mockProperty("prop-1"),
                    mockProperty("prop-2")
                )

                val admin = Member.create("admin-1", "admin@stayops.com", "hash", "관리자", MemberRole.ADMIN)

                sut.resolveAccessiblePropertyIds(admin) shouldContainExactly listOf("prop-1", "prop-2")
            }
        }

        `when`("OWNER이면") {
            then("회원에게 부여된 숙소 ID를 반환한다") {
                val owner = ownerWithAccess("prop-1").grantAccess("prop-2", PropertyRole.MANAGER)

                sut.resolveAccessiblePropertyIds(owner) shouldContainExactly listOf("prop-1", "prop-2")
            }
        }
    }

    given("고객 권한을 확인할 때") {
        `when`("인증된 회원이면") {
            then("회원 정보를 반환한다") {
                val owner = ownerWithAccess("prop-1")

                sut.requireAuthenticatedMember(owner) shouldBe owner
            }
        }

        `when`("CUSTOMER 회원이면") {
            then("회원 정보를 반환한다") {
                val customer = Member.create("customer-1", "customer@stayops.com", "hash", "고객", MemberRole.CUSTOMER)

                sut.requireCustomer(customer) shouldBe customer
            }
        }

        `when`("CUSTOMER가 아니면") {
            then("NOT_CUSTOMER 예외가 발생한다") {
                val owner = ownerWithAccess("prop-1")

                val exception = shouldThrow<ForbiddenException> {
                    sut.requireCustomer(owner)
                }

                exception.code shouldBe "NOT_CUSTOMER"
            }
        }
    }
})

private fun ownerWithAccess(propertyId: String): Member =
    Member.create("owner-1", "owner@stayops.com", "hash", "소유자", MemberRole.OWNER)
        .grantAccess(propertyId, PropertyRole.OWNER)

private fun mockProperty(id: String): Property =
    mockk {
        every { this@mockk.id } returns id
    }
