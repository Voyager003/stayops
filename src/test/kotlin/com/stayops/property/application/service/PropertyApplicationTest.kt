package com.stayops.property.application.service

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.auth.domain.model.PropertyAccess
import com.stayops.auth.domain.model.PropertyRole
import com.stayops.auth.domain.repository.MemberRepository
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelType
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class PropertyApplicationTest : BehaviorSpec({

    val propertyRepository = mockk<PropertyRepository>()
    val memberRepository = mockk<MemberRepository>()
    val channelRepository = mockk<ChannelRepository>()
    val propertyApplication = PropertyApplication(propertyRepository, memberRepository, channelRepository)

    fun sampleAddress() = Address.of("해운대로 123", "부산", "부산광역시", "48099", "KR")
    fun sampleContactInfo() = ContactInfo.of("051-123-4567", "test@pension.com")

    given("숙소 생성 시") {
        `when`("유효한 요청이면") {
            val owner = Member.create(
                id = "owner-1",
                email = "owner@test.com",
                passwordHash = "hashed",
                name = "홍길동",
                role = MemberRole.OWNER
            )

            every { propertyRepository.save(any()) } answers { firstArg() }
            every { channelRepository.save(any()) } answers { firstArg() }
            every { memberRepository.findById("owner-1") } returns owner
            every { memberRepository.save(any()) } answers { firstArg() }

            val result = propertyApplication.createProperty(
                ownerId = "owner-1",
                name = "해운대 펜션",
                type = PropertyType.PENSION,
                address = sampleAddress(),
                contactInfo = sampleContactInfo(),
                description = "아름다운 펜션"
            )

            then("저장된 숙소를 반환한다") {
                result.name shouldBe "해운대 펜션"
                result.ownerId shouldBe "owner-1"
            }
            then("OWNER에게 숙소 접근 권한이 부여된다") {
                verify {
                    memberRepository.save(match<Member> {
                        it.propertyAccess.any { access ->
                            access.propertyId == result.id && access.role == PropertyRole.OWNER
                        }
                    })
                }
            }
            then("DIRECT 채널이 자동 생성된다") {
                verify {
                    channelRepository.save(match<Channel> {
                        it.propertyId == result.id && it.code == "DIRECT" && it.type == ChannelType.DIRECT
                    })
                }
            }
        }
    }

    given("숙소 단건 조회 시") {
        val property = Property.create(
            id = "prop-1",
            ownerId = "owner-1",
            name = "해운대 펜션",
            type = PropertyType.PENSION,
            address = sampleAddress(),
            contactInfo = sampleContactInfo(),
            description = "아름다운 펜션"
        )

        `when`("존재하는 id이면") {
            every { propertyRepository.findById("prop-1") } returns property

            val result = propertyApplication.getProperty("prop-1")

            then("해당 숙소를 반환한다") {
                result.id shouldBe "prop-1"
                result.name shouldBe "해운대 펜션"
            }
        }

        `when`("존재하지 않는 id이면") {
            every { propertyRepository.findById("not-exist") } returns null

            then("NotFoundException이 발생한다") {
                shouldThrow<NotFoundException> {
                    propertyApplication.getProperty("not-exist")
                }
            }
        }
    }

    given("전체 숙소 조회 시") {
        `when`("저장된 숙소가 있으면") {
            val properties = listOf(
                Property.create(id = "prop-1", ownerId = "owner-1", name = "펜션A", type = PropertyType.PENSION, address = sampleAddress(), contactInfo = sampleContactInfo(), description = "설명"),
                Property.create(id = "prop-2", ownerId = "owner-2", name = "호텔B", type = PropertyType.HOTEL, address = sampleAddress(), contactInfo = sampleContactInfo(), description = "설명")
            )
            every { propertyRepository.findAll() } returns properties

            val result = propertyApplication.getAllProperties()

            then("전체 목록을 반환한다") {
                result.size shouldBe 2
            }
        }
    }

    given("숙소 수정 시") {
        val property = Property.create(
            id = "prop-1",
            ownerId = "owner-1",
            name = "해운대 펜션",
            type = PropertyType.PENSION,
            address = sampleAddress(),
            contactInfo = sampleContactInfo(),
            description = "기존 설명"
        )

        `when`("존재하는 숙소를 수정하면") {
            every { propertyRepository.findById("prop-1") } returns property
            every { propertyRepository.save(any()) } answers { firstArg() }

            val result = propertyApplication.updateProperty(
                id = "prop-1",
                name = "광안리 펜션",
                description = "새 설명",
                address = sampleAddress(),
                contactInfo = sampleContactInfo()
            )

            then("수정된 숙소를 반환한다") {
                result.name shouldBe "광안리 펜션"
                result.description shouldBe "새 설명"
            }
        }

        `when`("존재하지 않는 숙소를 수정하려 하면") {
            every { propertyRepository.findById("not-exist") } returns null

            then("NotFoundException이 발생한다") {
                shouldThrow<NotFoundException> {
                    propertyApplication.updateProperty(
                        id = "not-exist",
                        name = "새 이름",
                        description = "새 설명",
                        address = sampleAddress(),
                        contactInfo = sampleContactInfo()
                    )
                }
            }
        }
    }
})
