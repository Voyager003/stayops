package com.stayops.channel.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class ChannelMappingTest : BehaviorSpec({

    fun roomTypeEntry(internalId: String = "rt-1", externalCode: String = "OTA-DELUXE") =
        MappingEntry(internalId = internalId, externalCode = externalCode, type = MappingType.ROOM_TYPE)

    fun emptyMapping(
        id: String = "map-1",
        propertyId: String = "prop-1",
        channelCode: String = "AGODA"
    ) = ChannelMapping.create(id = id, propertyId = propertyId, channelCode = channelCode)

    // -- 생성 --

    given("ChannelMapping 생성 시") {
        `when`("create() 호출하면") {
            val mapping = emptyMapping()

            then("빈 매핑 리스트로 생성된다") {
                mapping.mappings shouldBe emptyList()
            }
            then("propertyId와 channelCode가 설정된다") {
                mapping.propertyId shouldBe "prop-1"
                mapping.channelCode shouldBe "AGODA"
            }
            then("version이 0이다") {
                mapping.version shouldBe 0L
            }
        }
    }

    // -- 매핑 추가 --

    given("빈 ChannelMapping에") {
        val mapping = emptyMapping()

        `when`("매핑을 추가하면") {
            val updated = mapping.addMapping(roomTypeEntry())

            then("매핑 리스트에 추가된다") {
                updated.mappings.size shouldBe 1
                updated.mappings[0].internalId shouldBe "rt-1"
                updated.mappings[0].externalCode shouldBe "OTA-DELUXE"
            }
        }
    }

    given("매핑이 1개 존재하는 ChannelMapping에") {
        val mapping = emptyMapping().addMapping(roomTypeEntry())

        `when`("같은 internalId와 type으로 중복 추가하면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    mapping.addMapping(roomTypeEntry(internalId = "rt-1", externalCode = "OTHER-CODE"))
                }
            }
        }

        `when`("같은 externalCode와 type으로 중복 추가하면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    mapping.addMapping(roomTypeEntry(internalId = "rt-2", externalCode = "OTA-DELUXE"))
                }
            }
        }

        `when`("다른 internalId와 externalCode로 추가하면") {
            val updated = mapping.addMapping(roomTypeEntry(internalId = "rt-2", externalCode = "OTA-STANDARD"))

            then("매핑 리스트에 2개가 된다") {
                updated.mappings.size shouldBe 2
            }
        }
    }

    // -- 매핑 제거 --

    given("매핑이 존재하는 ChannelMapping에서") {
        val mapping = emptyMapping()
            .addMapping(roomTypeEntry(internalId = "rt-1", externalCode = "OTA-DELUXE"))
            .addMapping(roomTypeEntry(internalId = "rt-2", externalCode = "OTA-STANDARD"))

        `when`("존재하는 매핑을 제거하면") {
            val updated = mapping.removeMapping("rt-1", MappingType.ROOM_TYPE)

            then("해당 매핑이 제거된다") {
                updated.mappings.size shouldBe 1
                updated.mappings[0].internalId shouldBe "rt-2"
            }
        }

        `when`("존재하지 않는 매핑을 제거하면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    mapping.removeMapping("rt-999", MappingType.ROOM_TYPE)
                }
            }
        }
    }

    // -- 양방향 조회 --

    given("매핑이 등록된 ChannelMapping에서") {
        val mapping = emptyMapping()
            .addMapping(roomTypeEntry(internalId = "rt-1", externalCode = "OTA-DELUXE"))

        `when`("internalId로 externalCode를 조회하면") {
            val externalCode = mapping.findExternalCode("rt-1", MappingType.ROOM_TYPE)

            then("매핑된 externalCode를 반환한다") {
                externalCode.shouldNotBeNull()
                externalCode shouldBe "OTA-DELUXE"
            }
        }

        `when`("externalCode로 internalId를 조회하면") {
            val internalId = mapping.findInternalId("OTA-DELUXE", MappingType.ROOM_TYPE)

            then("매핑된 internalId를 반환한다") {
                internalId.shouldNotBeNull()
                internalId shouldBe "rt-1"
            }
        }

        `when`("존재하지 않는 internalId로 조회하면") {
            then("null을 반환한다") {
                mapping.findExternalCode("rt-999", MappingType.ROOM_TYPE).shouldBeNull()
            }
        }

        `when`("존재하지 않는 externalCode로 조회하면") {
            then("null을 반환한다") {
                mapping.findInternalId("UNKNOWN", MappingType.ROOM_TYPE).shouldBeNull()
            }
        }
    }
})
