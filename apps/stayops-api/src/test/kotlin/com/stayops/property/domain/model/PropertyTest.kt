package com.stayops.property.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PropertyTest : BehaviorSpec({

    fun sampleAddress() = Address.of(
        street = "해운대로 123",
        city = "부산",
        state = "부산광역시",
        zipCode = "48099",
        country = "KR"
    )

    fun sampleContactInfo() = ContactInfo.of(
        phone = "051-123-4567",
        email = "test@pension.com"
    )

    fun newProperty() = Property.create(
        id = "prop-1",
        ownerId = "owner-1",
        name = "해운대 펜션",
        type = PropertyType.PENSION,
        address = sampleAddress(),
        contactInfo = sampleContactInfo(),
        description = "아름다운 펜션"
    )

    given("숙소 생성 시") {
        `when`("유효한 정보로 생성하면") {
            val property = newProperty()
            then("INACTIVE 상태로 생성된다") {
                property.status shouldBe PropertyStatus.INACTIVE
            }
        }
        `when`("이름이 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Property.create(
                        id = "prop-1",
                        ownerId = "owner-1",
                        name = "",
                        type = PropertyType.PENSION,
                        address = sampleAddress(),
                        contactInfo = sampleContactInfo(),
                        description = "설명"
                    )
                }
            }
        }
    }

    given("INACTIVE 상태의 숙소") {
        val property = newProperty()

        `when`("activate() 호출 시") {
            val activated = property.activate()
            then("상태가 ACTIVE로 변경된다") {
                activated.status shouldBe PropertyStatus.ACTIVE
            }
            then("원본 숙소 상태는 변경되지 않는다") {
                property.status shouldBe PropertyStatus.INACTIVE
            }
        }
        `when`("deactivate() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    property.deactivate()
                }
            }
        }
        `when`("suspend() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    property.suspend()
                }
            }
        }
        `when`("isBookable() 호출 시") {
            then("false를 반환한다") {
                property.isBookable() shouldBe false
            }
        }
    }

    given("ACTIVE 상태의 숙소") {
        val property = newProperty().activate()

        `when`("deactivate() 호출 시") {
            val deactivated = property.deactivate()
            then("상태가 INACTIVE로 변경된다") {
                deactivated.status shouldBe PropertyStatus.INACTIVE
            }
        }
        `when`("suspend() 호출 시") {
            val suspended = property.suspend()
            then("상태가 SUSPENDED로 변경된다") {
                suspended.status shouldBe PropertyStatus.SUSPENDED
            }
        }
        `when`("activate() 재호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    property.activate()
                }
            }
        }
        `when`("isBookable() 호출 시") {
            then("true를 반환한다") {
                property.isBookable() shouldBe true
            }
        }
    }

    given("SUSPENDED 상태의 숙소") {
        val property = newProperty().activate().suspend()

        `when`("activate() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    property.activate()
                }
            }
        }
        `when`("suspend() 재호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    property.suspend()
                }
            }
        }
        `when`("deactivate() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    property.deactivate()
                }
            }
        }
        `when`("isBookable() 호출 시") {
            then("false를 반환한다") {
                property.isBookable() shouldBe false
            }
        }
    }

    given("Address 생성 시") {
        `when`("street이 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Address.of(street = "", city = "부산", state = "부산광역시", zipCode = "48099", country = "KR")
                }
            }
        }
        `when`("city가 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Address.of(street = "해운대로 123", city = "", state = "부산광역시", zipCode = "48099", country = "KR")
                }
            }
        }
        `when`("zipCode가 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Address.of(street = "해운대로 123", city = "부산", state = "부산광역시", zipCode = "", country = "KR")
                }
            }
        }
    }

    given("숙소 정보 수정 시") {
        val property = newProperty()

        `when`("유효한 값으로 수정하면") {
            val updated = property.updateInfo(name = "광안리 펜션", description = "새 설명")
            then("이름과 설명이 변경된다") {
                updated.name shouldBe "광안리 펜션"
                updated.description shouldBe "새 설명"
            }
        }
        `when`("이름을 빈 문자열로 수정하면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    property.updateInfo(name = "")
                }
            }
        }
    }

    given("숙소 동일성 비교 시") {
        `when`("같은 id를 가진 숙소의 상태가 서로 다르면") {
            val property = newProperty()
            val activated = property.activate()

            then("같은 숙소로 판단한다") {
                activated shouldBe property
            }
        }

        `when`("같은 id를 가진 숙소의 저장 상태가 서로 다르면") {
            val property = newProperty()
            val reconstituted = Property.reconstitute(
                id = property.id,
                ownerId = property.ownerId,
                name = "다른 이름",
                type = property.type,
                address = property.address,
                contactInfo = property.contactInfo,
                description = "다른 설명",
                status = PropertyStatus.ACTIVE,
                timezone = property.timezone,
                currency = property.currency,
                version = property.version + 1,
                createdAt = property.createdAt,
                updatedAt = property.updatedAt.plusSeconds(60)
            )

            then("id 기준으로 같은 숙소로 판단한다") {
                reconstituted shouldBe property
            }
        }
    }

    given("ContactInfo 생성 시") {
        `when`("phone이 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    ContactInfo.of(phone = "", email = "test@test.com")
                }
            }
        }
        `when`("email이 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    ContactInfo.of(phone = "051-123-4567", email = "")
                }
            }
        }
    }
})
