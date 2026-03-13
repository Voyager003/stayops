package com.stayops.channel.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal

class ChannelTest : BehaviorSpec({

    // -----------------------------------------
    // DIRECT 채널 생성
    // -----------------------------------------

    given("DIRECT(FineStay) 채널을 생성할 때") {
        `when`("유효한 정보로 생성하면") {
            val channel = Channel.createDirect(
                id = "ch-1",
                propertyId = "prop-1"
            )

            then("type이 DIRECT이다") {
                channel.type shouldBe ChannelType.DIRECT
            }
            then("code가 FINESTAY이다") {
                channel.code shouldBe "FINESTAY"
            }
            then("policy가 DirectPolicy이다") {
                channel.policy.shouldBeInstanceOf<ChannelPolicy.DirectPolicy>()
            }
            then("수수료율이 0이다") {
                channel.policy.commissionRate shouldBe BigDecimal.ZERO
            }
            then("상태가 ACTIVE이다") {
                channel.status shouldBe ChannelStatus.ACTIVE
            }
        }
    }

    // -----------------------------------------
    // OTA 채널 생성
    // -----------------------------------------

    given("OTA 채널을 생성할 때") {
        `when`("유효한 정보로 생성하면") {
            val channel = Channel.createOta(
                id = "ch-2",
                propertyId = "prop-1",
                code = "AGODA",
                name = "아고다",
                commissionRate = BigDecimal("0.15"),
                webhookSecret = "secret-key-123"
            )

            then("type이 OTA이다") {
                channel.type shouldBe ChannelType.OTA
            }
            then("code가 AGODA이다") {
                channel.code shouldBe "AGODA"
            }
            then("policy가 OtaPolicy이다") {
                channel.policy.shouldBeInstanceOf<ChannelPolicy.OtaPolicy>()
            }
            then("수수료율이 0.15이다") {
                channel.policy.commissionRate shouldBe BigDecimal("0.15")
            }
            then("webhookSecret이 설정된다") {
                val otaPolicy = channel.policy as ChannelPolicy.OtaPolicy
                otaPolicy.webhookSecret shouldBe "secret-key-123"
            }
            then("상태가 ACTIVE이다") {
                channel.status shouldBe ChannelStatus.ACTIVE
            }
        }

        `when`("수수료율이 0이면") {
            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Channel.createOta(
                        id = "ch-3",
                        propertyId = "prop-1",
                        code = "AGODA",
                        name = "아고다",
                        commissionRate = BigDecimal.ZERO,
                        webhookSecret = "secret"
                    )
                }
            }
        }

        `when`("webhookSecret이 공백이면") {
            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Channel.createOta(
                        id = "ch-4",
                        propertyId = "prop-1",
                        code = "AGODA",
                        name = "아고다",
                        commissionRate = BigDecimal("0.15"),
                        webhookSecret = "  "
                    )
                }
            }
        }

        `when`("code가 공백이면") {
            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Channel.createOta(
                        id = "ch-5",
                        propertyId = "prop-1",
                        code = "  ",
                        name = "아고다",
                        commissionRate = BigDecimal("0.15"),
                        webhookSecret = "secret"
                    )
                }
            }
        }

        `when`("name이 공백이면") {
            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Channel.createOta(
                        id = "ch-6",
                        propertyId = "prop-1",
                        code = "AGODA",
                        name = "  ",
                        commissionRate = BigDecimal("0.15"),
                        webhookSecret = "secret"
                    )
                }
            }
        }

        `when`("수수료율이 1을 초과하면") {
            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Channel.createOta(
                        id = "ch-7",
                        propertyId = "prop-1",
                        code = "AGODA",
                        name = "아고다",
                        commissionRate = BigDecimal("1.01"),
                        webhookSecret = "secret"
                    )
                }
            }
        }
    }

    // -----------------------------------------
    // 상태 전이
    // -----------------------------------------

    given("ACTIVE 상태의 채널이") {
        val channel = Channel.createOta(
            id = "ch-1",
            propertyId = "prop-1",
            code = "AGODA",
            name = "아고다",
            commissionRate = BigDecimal("0.15"),
            webhookSecret = "secret"
        )

        `when`("비활성화하면") {
            val deactivated = channel.deactivate()
            then("상태가 INACTIVE로 변경된다") {
                deactivated.status shouldBe ChannelStatus.INACTIVE
            }
        }

        `when`("일시 중지하면") {
            val suspended = channel.suspend()
            then("상태가 SUSPENDED로 변경된다") {
                suspended.status shouldBe ChannelStatus.SUSPENDED
            }
        }
    }

    given("INACTIVE 상태의 채널이") {
        val channel = Channel.createOta(
            id = "ch-1",
            propertyId = "prop-1",
            code = "AGODA",
            name = "아고다",
            commissionRate = BigDecimal("0.15"),
            webhookSecret = "secret"
        ).deactivate()

        `when`("활성화하면") {
            val activated = channel.activate()
            then("상태가 ACTIVE로 변경된다") {
                activated.status shouldBe ChannelStatus.ACTIVE
            }
        }
    }

    given("SUSPENDED 상태의 채널이") {
        val channel = Channel.createOta(
            id = "ch-1",
            propertyId = "prop-1",
            code = "AGODA",
            name = "아고다",
            commissionRate = BigDecimal("0.15"),
            webhookSecret = "secret"
        ).suspend()

        `when`("활성화하면") {
            val activated = channel.activate()
            then("상태가 ACTIVE로 변경된다") {
                activated.status shouldBe ChannelStatus.ACTIVE
            }
        }
    }
})
