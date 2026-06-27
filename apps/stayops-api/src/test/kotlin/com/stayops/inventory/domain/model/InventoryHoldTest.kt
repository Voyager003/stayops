package com.stayops.inventory.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate

class InventoryHoldTest : BehaviorSpec({

    val now = Instant.parse("2026-04-01T01:00:00Z")
    val expiresAt = Instant.parse("2026-04-01T01:15:00Z")
    val dates = listOf(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2))

    fun hold() = InventoryHold.create(
        id = "hold-1",
        reservationIntentId = "intent-1",
        propertyId = "property-1",
        roomTypeId = "room-type-1",
        dates = dates,
        quantity = 1,
        expiresAt = expiresAt,
        now = now
    )

    given("InventoryHold 생성 시") {
        `when`("유효한 날짜와 수량이면") {
            val created = hold()

            then("HELD 상태로 생성된다") {
                created.status shouldBe InventoryHoldStatus.HELD
            }
            then("날짜별 점유 수량을 가진다") {
                created.dates shouldBe dates
                created.quantity shouldBe 1
            }
        }

        `when`("날짜가 비어 있으면") {
            then("생성할 수 없다") {
                shouldThrow<IllegalArgumentException> {
                    InventoryHold.create(
                        id = "hold-2",
                        reservationIntentId = "intent-1",
                        propertyId = "property-1",
                        roomTypeId = "room-type-1",
                        dates = emptyList(),
                        quantity = 1,
                        expiresAt = expiresAt,
                        now = now
                    )
                }
            }
        }

        `when`("수량이 0이면") {
            then("생성할 수 없다") {
                shouldThrow<IllegalArgumentException> {
                    InventoryHold.create(
                        id = "hold-3",
                        reservationIntentId = "intent-1",
                        propertyId = "property-1",
                        roomTypeId = "room-type-1",
                        dates = dates,
                        quantity = 0,
                        expiresAt = expiresAt,
                        now = now
                    )
                }
            }
        }
    }

    given("결제 승인 요청이 시작될 때") {
        `when`("hold가 만료되지 않은 HELD 상태이면") {
            val processing = hold().startPaymentProcessing(now.plusSeconds(60))

            then("PAYMENT_PROCESSING 상태가 된다") {
                processing.status shouldBe InventoryHoldStatus.PAYMENT_PROCESSING
            }
        }

        `when`("hold가 이미 만료되었으면") {
            then("결제 처리 상태로 전환할 수 없다") {
                shouldThrow<IllegalStateException> {
                    hold().startPaymentProcessing(expiresAt.plusSeconds(1))
                }
            }
        }
    }

    given("hold를 소비할 때") {
        `when`("결제 처리 상태이면") {
            val consumed = hold()
                .startPaymentProcessing(now.plusSeconds(60))
                .consume()

            then("CONSUMED 상태가 된다") {
                consumed.status shouldBe InventoryHoldStatus.CONSUMED
            }
        }

        `when`("HELD 상태이면") {
            then("바로 소비할 수 없다") {
                shouldThrow<IllegalStateException> {
                    hold().consume()
                }
            }
        }
    }

    given("hold를 해제할 때") {
        `when`("HELD 상태이면") {
            val released = hold().release()

            then("RELEASED 상태가 된다") {
                released.status shouldBe InventoryHoldStatus.RELEASED
            }
        }
    }
})
