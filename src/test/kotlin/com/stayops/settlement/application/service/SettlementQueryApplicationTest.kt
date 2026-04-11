package com.stayops.settlement.application.service

import com.stayops.settlement.application.dto.ChannelSettlement
import com.stayops.settlement.application.dto.DailySettlement
import com.stayops.settlement.application.dto.MonthlySettlement
import com.stayops.shared.domain.Money
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate

class SettlementQueryApplicationTest : BehaviorSpec({

    val repository = mockk<SettlementQueryRepository>()
    val sut = SettlementQueryApplication(repository)

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
                every { repository.countReservations("prop-1", startDate, endDate) } returns 5

                val result = sut.getSettlementSummary("prop-1", startDate, endDate)

                result.startDate shouldBe startDate
                result.endDate shouldBe endDate
                result.totalReservations shouldBe 5
                result.totalRevenue shouldBe Money.won(1_000_000)
                result.totalCommission shouldBe Money.won(60_000)
                result.netSettlement shouldBe Money.won(940_000)
                result.documentCount shouldBe 5
                result.byChannel shouldBe channelSettlements
            }
        }

        `when`("정산 대상이 없으면") {
            then("모든 금액이 0인 요약을 반환한다") {
                every { repository.findChannelSettlements("prop-1", startDate, endDate) } returns emptyList()
                every { repository.countReservations("prop-1", startDate, endDate) } returns 0

                val result = sut.getSettlementSummary("prop-1", startDate, endDate)

                result.totalReservations shouldBe 0
                result.totalRevenue shouldBe Money.ZERO
                result.totalCommission shouldBe Money.ZERO
                result.netSettlement shouldBe Money.ZERO
                result.documentCount shouldBe 0
                result.byChannel shouldBe emptyList()
            }
        }
    }

    given("일별 추이 조회 시") {
        `when`("기간 내 데이터가 있으면") {
            then("날짜별 정산 목록을 반환한다") {
                val dailyData = listOf(
                    DailySettlement(startDate, 2, Money.won(300_000), Money.won(30_000), Money.won(270_000)),
                    DailySettlement(startDate.plusDays(1), 1, Money.won(150_000), Money.won(15_000), Money.won(135_000))
                )
                every { repository.findDailyTrend("prop-1", startDate, endDate) } returns dailyData

                val result = sut.getDailyTrend("prop-1", startDate, endDate)

                result.size shouldBe 2
                result[0].date shouldBe startDate
                result[0].reservationCount shouldBe 2
            }
        }
    }

    given("월별 추이 조회 시") {
        `when`("연도 내 데이터가 있으면") {
            then("월별 정산 목록을 반환한다") {
                val monthlyData = listOf(
                    MonthlySettlement(2026, 1, 10, Money.won(2_000_000), Money.won(200_000), Money.won(1_800_000)),
                    MonthlySettlement(2026, 2, 8, Money.won(1_500_000), Money.won(150_000), Money.won(1_350_000))
                )
                every { repository.findMonthlyTrend("prop-1", 2026) } returns monthlyData

                val result = sut.getMonthlyTrend("prop-1", 2026)

                result.size shouldBe 2
                result[0].month shouldBe 1
                result[1].month shouldBe 2
            }
        }
    }
})
