package com.stayops.property.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelType
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.PropertyRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class PropertyOnboardingApplicationTest : BehaviorSpec({

    val propertyRepository = mockk<PropertyRepository>()
    val memberRepository = mockk<MemberRepository>()
    val channelRepository = mockk<ChannelRepository>()
    val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun generate() = "id-${++counter}"
    }
    val propertyOnboardingApplication = PropertyOnboardingApplication(
        propertyRepository = propertyRepository,
        memberRepository = memberRepository,
        channelRepository = channelRepository,
        idGenerator = idGenerator
    )

    beforeTest {
        clearMocks(propertyRepository, memberRepository, channelRepository)
    }

    fun sampleAddress() = Address.of("해운대로 123", "부산", "부산광역시", "48099", "KR")
    fun sampleContactInfo() = ContactInfo.of("051-123-4567", "test@pension.com")

    given("숙소 온보딩 시") {
        `when`("유효한 owner와 숙소 정보이면") {
            then("저장된 숙소와 접근 권한이 갱신된 owner를 반환한다") {
                val owner = Member.create(
                    id = "owner-1",
                    email = "owner@test.com",
                    passwordHash = "hashed",
                    name = "홍길동",
                    role = MemberRole.OWNER
                )
                every { memberRepository.findById("owner-1") } returns owner
                every { propertyRepository.save(any()) } answers { firstArg() }
                every { channelRepository.save(any()) } answers { firstArg() }
                every { memberRepository.save(any()) } answers { firstArg() }

                val result = propertyOnboardingApplication.onboardProperty(
                    ownerId = "owner-1",
                    name = "해운대 펜션",
                    type = PropertyType.PENSION,
                    address = sampleAddress(),
                    contactInfo = sampleContactInfo(),
                    description = "아름다운 펜션"
                )

                result.property.name shouldBe "해운대 펜션"
                result.property.ownerId shouldBe "owner-1"
                result.owner.propertyAccess.any { it.propertyId == result.property.id && it.role == PropertyRole.OWNER } shouldBe true
            }

            then("DIRECT 채널이 자동 생성된다") {
                val owner = Member.create(
                    id = "owner-1",
                    email = "owner@test.com",
                    passwordHash = "hashed",
                    name = "홍길동",
                    role = MemberRole.OWNER
                )
                every { memberRepository.findById("owner-1") } returns owner
                every { propertyRepository.save(any()) } answers { firstArg() }
                every { channelRepository.save(any()) } answers { firstArg() }
                every { memberRepository.save(any()) } answers { firstArg() }

                val result = propertyOnboardingApplication.onboardProperty(
                    ownerId = "owner-1",
                    name = "해운대 펜션",
                    type = PropertyType.PENSION,
                    address = sampleAddress(),
                    contactInfo = sampleContactInfo(),
                    description = "아름다운 펜션"
                )

                verify {
                    channelRepository.save(match<Channel> {
                        it.propertyId == result.property.id && it.code == "DIRECT" && it.type == ChannelType.DIRECT
                    })
                }
            }
        }

        `when`("owner가 존재하지 않으면") {
            then("NotFoundException이 발생하고 숙소를 생성하지 않는다") {
                every { memberRepository.findById("missing-owner") } returns null

                shouldThrow<NotFoundException> {
                    propertyOnboardingApplication.onboardProperty(
                        ownerId = "missing-owner",
                        name = "해운대 펜션",
                        type = PropertyType.PENSION,
                        address = sampleAddress(),
                        contactInfo = sampleContactInfo(),
                        description = "아름다운 펜션"
                    )
                }
                verify(exactly = 0) { propertyRepository.save(any<Property>()) }
            }
        }
    }
})
