package com.stayops.auth.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class MemberTest : BehaviorSpec({

    fun createMember(
        role: MemberRole = MemberRole.OWNER,
        propertyAccess: List<PropertyAccess> = emptyList()
    ) = Member.create(
        id = "member-1",
        email = "test@stayops.com",
        passwordHash = "hashed-password",
        name = "홍길동",
        role = role
    ).let { member ->
        propertyAccess.fold(member) { acc, access ->
            acc.grantAccess(access.propertyId, access.role)
        }
    }

    given("Member 생성 시") {

        `when`("유효한 정보로 생성하면") {
            then("ACTIVE 상태의 Member가 생성된다") {
                val member = createMember()

                member.email shouldBe "test@stayops.com"
                member.name shouldBe "홍길동"
                member.role shouldBe MemberRole.OWNER
                member.status shouldBe MemberStatus.ACTIVE
                member.propertyAccess shouldBe emptyList()
                member.lastLoginAt shouldBe null
            }
        }

        `when`("이메일이 유효하지 않으면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Member.create(
                        id = "member-1",
                        email = "invalid-email",
                        passwordHash = "hashed",
                        name = "홍길동",
                        role = MemberRole.OWNER
                    )
                }
            }
        }

        `when`("이름이 공백이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Member.create(
                        id = "member-1",
                        email = "test@stayops.com",
                        passwordHash = "hashed",
                        name = "",
                        role = MemberRole.OWNER
                    )
                }
            }
        }
    }

    given("hasAccessTo 검증 시") {

        `when`("ADMIN 역할이면") {
            then("모든 숙소에 접근 가능하다") {
                val admin = createMember(role = MemberRole.ADMIN)

                admin.hasAccessTo("any-property") shouldBe true
            }
        }

        `when`("propertyAccess에 해당 숙소가 있으면") {
            then("접근 가능하다") {
                val member = createMember(
                    propertyAccess = listOf(PropertyAccess("prop-1", PropertyRole.OWNER))
                )

                member.hasAccessTo("prop-1") shouldBe true
            }
        }

        `when`("propertyAccess에 해당 숙소가 없으면") {
            then("접근 불가하다") {
                val member = createMember(
                    propertyAccess = listOf(PropertyAccess("prop-1", PropertyRole.OWNER))
                )

                member.hasAccessTo("prop-999") shouldBe false
            }
        }

        `when`("propertyAccess가 비어있으면") {
            then("접근 불가하다") {
                val member = createMember()

                member.hasAccessTo("prop-1") shouldBe false
            }
        }
    }

    given("grantAccess 시") {

        `when`("새로운 숙소 접근 권한을 추가하면") {
            then("propertyAccess에 추가된다") {
                val member = createMember()
                val updated = member.grantAccess("prop-1", PropertyRole.MANAGER)

                updated.propertyAccess.size shouldBe 1
                updated.propertyAccess[0].propertyId shouldBe "prop-1"
                updated.propertyAccess[0].role shouldBe PropertyRole.MANAGER
            }
        }

        `when`("이미 접근 권한이 있는 숙소를 추가하면") {
            then("예외가 발생한다") {
                val member = createMember(
                    propertyAccess = listOf(PropertyAccess("prop-1", PropertyRole.OWNER))
                )

                shouldThrow<IllegalArgumentException> {
                    member.grantAccess("prop-1", PropertyRole.MANAGER)
                }
            }
        }
    }

    given("revokeAccess 시") {

        `when`("기존 접근 권한을 제거하면") {
            then("propertyAccess에서 제거된다") {
                val member = createMember(
                    propertyAccess = listOf(PropertyAccess("prop-1", PropertyRole.OWNER))
                )
                val updated = member.revokeAccess("prop-1")

                updated.propertyAccess shouldBe emptyList()
            }
        }

        `when`("접근 권한이 없는 숙소를 제거하면") {
            then("예외가 발생한다") {
                val member = createMember()

                shouldThrow<IllegalArgumentException> {
                    member.revokeAccess("prop-999")
                }
            }
        }
    }

    given("recordLogin 시") {

        `when`("로그인을 기록하면") {
            then("lastLoginAt이 설정된다") {
                val member = createMember()
                val loggedIn = member.recordLogin()

                loggedIn.lastLoginAt shouldBe loggedIn.lastLoginAt
                (loggedIn.lastLoginAt != null) shouldBe true
            }
        }
    }

    given("deactivate 시") {

        `when`("ACTIVE 상태에서 비활성화하면") {
            then("INACTIVE 상태로 변경된다") {
                val member = createMember()
                val deactivated = member.deactivate()

                deactivated.status shouldBe MemberStatus.INACTIVE
            }
        }

        `when`("이미 INACTIVE 상태에서 비활성화하면") {
            then("예외가 발생한다") {
                val member = createMember().deactivate()

                shouldThrow<IllegalStateException> {
                    member.deactivate()
                }
            }
        }
    }
})
