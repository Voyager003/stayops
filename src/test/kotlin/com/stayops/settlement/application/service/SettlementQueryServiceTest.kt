package com.stayops.settlement.application.service

import com.stayops.settlement.application.dto.ChannelSettlement
import com.stayops.shared.domain.Money
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate

class SettlementQueryServiceTest : BehaviorSpec({

    val repository = mockk<SettlementQueryRepository>()
    val sut = SettlementQueryService(repository)

    val startDate = LocalDate.of(2026, 4, 1)
    val endDate = LocalDate.of(2026, 4, 30)

    given("정산 요약 조회 시") {

        `when`("채널별 정산 데이터가 있으면") {
            then("전체 합계와 채널별 내역을 반환한다") {
                val channelSettlements = listOf(
                    ChannelSettlement("DIRECT", 3, Money.won(600_000), Money.won(0), Money.won(600_000)),
                    ChannelSettlement("AGODA", 2, Money.won(400_000), Money.won(60_000), Money.won(340_000))
                )
                every { repository.findChannelSettlements("prop-1", startDate, endDate) } returns channelSettlements

                val result = sut.getSettlementSummary("prop-1", startDate, endDate)

                result.startDate shouldBe startDate
                result.endDate shouldBe endDate
                result.totalReservations shouldBe 5
                result.totalRevenue shouldBe Money.won(1_000_000)
                result.totalCommission shouldBe Money.won(60_000)
                result.netSettlement shouldBe Money.won(940_000)
                result.byChannel shouldBe channelSettlements
            }
        }

        `when`("정산 대상이 없으면") {
            then("모든 금액이 0인 요약을 반환한다") {
                every { repository.findChannelSettlements("prop-1", startDate, endDate) } returns emptyList()

                val result = sut.getSettlementSummary("prop-1", startDate, endDate)

                result.totalReservations shouldBe 0
                result.totalRevenue shouldBe Money.ZERO
                result.totalCommission shouldBe Money.ZERO
                result.netSettlement shouldBe Money.ZERO
                result.byChannel shouldBe emptyList()
            }
        }
    }
})
