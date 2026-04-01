package com.stayops.channel.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ChannelTest : BehaviorSpec({

    val otaEndpoint = "https://mock-ota.stayops.com/api/v1/ari"

    // -- DIRECT 채널 생성 --

    given("DIRECT 채널 생성 시") {
        `when`("createDirect() 호출하면") {
            val channel = Channel.createDirect(
                id = "ch-1",
                propertyId = "prop-1"
            )

            then("ACTIVE 상태로 생성된다") {
                channel.status shouldBe ChannelStatus.ACTIVE
            }
            then("type이 DIRECT이다") {
                channel.type shouldBe ChannelType.DIRECT
            }
            then("commissionRate이 0이다") {
                channel.commissionRate shouldBe BigDecimal.ZERO
            }
            then("connectionInfo가 null이다") {
                channel.connectionInfo shouldBe null
            }
            then("code가 DIRECT이다") {
                channel.code shouldBe "DIRECT"
            }
            then("version이 0이다") {
                channel.version shouldBe 0L
            }
        }
    }

    // -- OTA 채널 생성 --

    given("OTA 채널 생성 시") {
        `when`("유효한 정보로 생성하면") {
            val channel = Channel.createOta(
                id = "ch-2",
                propertyId = "prop-1",
                code = "AGODA",
                name = "Agoda",
                commissionRate = BigDecimal("0.15"),
                apiEndpoint = otaEndpoint
            )

            then("ACTIVE 상태로 생성된다") {
                channel.status shouldBe ChannelStatus.ACTIVE
            }
            then("type이 OTA이다") {
                channel.type shouldBe ChannelType.OTA
            }
            then("commissionRate이 설정된 값이다") {
                channel.commissionRate shouldBe BigDecimal("0.15")
            }
            then("connectionInfo의 apiEndpoint가 설정된 값이다") {
                channel.connectionInfo shouldBe ChannelConnectionInfo(apiEndpoint = otaEndpoint)
            }
        }

        `when`("code가 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Channel.createOta(
                        id = "ch-3",
                        propertyId = "prop-1",
                        code = "",
                        name = "Agoda",
                        commissionRate = BigDecimal("0.15"),
                        apiEndpoint = otaEndpoint
                    )
                }
            }
        }

        `when`("name이 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Channel.createOta(
                        id = "ch-4",
                        propertyId = "prop-1",
                        code = "AGODA",
                        name = "",
                        commissionRate = BigDecimal("0.15"),
                        apiEndpoint = otaEndpoint
                    )
                }
            }
        }

        `when`("commissionRate이 0이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Channel.createOta(
                        id = "ch-5",
                        propertyId = "prop-1",
                        code = "AGODA",
                        name = "Agoda",
                        commissionRate = BigDecimal.ZERO,
                        apiEndpoint = otaEndpoint
                    )
                }
            }
        }

        `when`("commissionRate이 1보다 크면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Channel.createOta(
                        id = "ch-6",
                        propertyId = "prop-1",
                        code = "AGODA",
                        name = "Agoda",
                        commissionRate = BigDecimal("1.01"),
                        apiEndpoint = otaEndpoint
                    )
                }
            }
        }

    }

    // -- 상태 전이: ACTIVE --

    given("ACTIVE 상태의 채널") {
        val channel = Channel.createOta(
            id = "ch-10",
            propertyId = "prop-1",
            code = "AGODA",
            name = "Agoda",
            commissionRate = BigDecimal("0.15"),
            apiEndpoint = otaEndpoint
        )

        `when`("deactivate() 호출 시") {
            val deactivated = channel.deactivate()

            then("상태가 INACTIVE로 변경된다") {
                deactivated.status shouldBe ChannelStatus.INACTIVE
            }
        }

        `when`("suspend() 호출 시") {
            val suspended = channel.suspend()

            then("상태가 SUSPENDED로 변경된다") {
                suspended.status shouldBe ChannelStatus.SUSPENDED
            }
        }

        `when`("activate() 호출 시") {
            then("이미 ACTIVE이므로 예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    channel.activate()
                }
            }
        }
    }

    // -- 상태 전이: INACTIVE --

    given("INACTIVE 상태의 채널") {
        val channel = Channel.createOta(
            id = "ch-11",
            propertyId = "prop-1",
            code = "BOOKING",
            name = "Booking.com",
            commissionRate = BigDecimal("0.18"),
            apiEndpoint = otaEndpoint
        ).deactivate()

        `when`("activate() 호출 시") {
            val activated = channel.activate()

            then("상태가 ACTIVE로 변경된다") {
                activated.status shouldBe ChannelStatus.ACTIVE
            }
        }

        `when`("deactivate() 호출 시") {
            then("이미 INACTIVE이므로 예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    channel.deactivate()
                }
            }
        }

        `when`("suspend() 호출 시") {
            then("INACTIVE에서는 정지할 수 없으므로 예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    channel.suspend()
                }
            }
        }
    }

    // -- 상태 전이: SUSPENDED --

    given("SUSPENDED 상태의 채널") {
        val channel = Channel.createOta(
            id = "ch-12",
            propertyId = "prop-1",
            code = "EXPEDIA",
            name = "Expedia",
            commissionRate = BigDecimal("0.20"),
            apiEndpoint = otaEndpoint
        ).suspend()

        `when`("activate() 호출 시") {
            val activated = channel.activate()

            then("상태가 ACTIVE로 변경된다") {
                activated.status shouldBe ChannelStatus.ACTIVE
            }
        }

        `when`("deactivate() 호출 시") {
            then("SUSPENDED에서는 비활성화할 수 없으므로 예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    channel.deactivate()
                }
            }
        }

        `when`("suspend() 호출 시") {
            then("이미 SUSPENDED이므로 예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    channel.suspend()
                }
            }
        }
    }
})
