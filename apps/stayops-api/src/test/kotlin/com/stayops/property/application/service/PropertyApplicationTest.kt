package com.stayops.property.application.service

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

class PropertyApplicationTest : BehaviorSpec({

    val propertyRepository = mockk<PropertyRepository>()
    val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun generate() = "id-${++counter}"
    }
    val propertyApplication = PropertyApplication(propertyRepository, idGenerator)

    fun sampleAddress() = Address.of("해운대로 123", "부산", "부산광역시", "48099", "KR")
    fun sampleContactInfo() = ContactInfo.of("051-123-4567", "test@pension.com")

    given("숙소 생성 시") {
        `when`("유효한 요청이면") {
            every { propertyRepository.save(any()) } answers { firstArg() }

            val result = propertyApplication.createProperty(
                ownerId = "owner-1",
                name = "해운대 펜션",
                type = PropertyType.PENSION,
                address = sampleAddress(),
                contactInfo = sampleContactInfo(),
                description = "아름다운 펜션"
            )

            then("Property만 생성하고 저장된 숙소를 반환한다") {
                result.name shouldBe "해운대 펜션"
                result.ownerId shouldBe "owner-1"
                verify {
                    propertyRepository.save(match<Property> {
                        it.name == "해운대 펜션" && it.ownerId == "owner-1"
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
