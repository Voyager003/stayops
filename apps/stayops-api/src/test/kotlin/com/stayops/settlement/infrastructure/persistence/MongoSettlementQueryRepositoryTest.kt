package com.stayops.settlement.infrastructure.persistence

import com.stayops.TestcontainersConfiguration
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.infrastructure.persistence.ReservationDocument
import com.stayops.reservation.infrastructure.persistence.dao.ReservationMongoDao
import com.stayops.settlement.application.service.SettlementQueryRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class MongoSettlementQueryRepositoryTest @Autowired constructor(
    private val settlementQueryRepository: SettlementQueryRepository,
    private val reservationMongoDao: ReservationMongoDao,
    private val clock: Clock
) {

    @BeforeEach
    fun setUp() {
        reservationMongoDao.deleteAll()
    }

    private fun reservationDoc(
        id: String,
        propertyId: String = "prop-1",
        status: ReservationStatus,
        channelCode: String = "DIRECT",
        commissionRate: BigDecimal = BigDecimal.ZERO,
        checkOut: String = "2026-04-15",
        totalAmount: BigDecimal = BigDecimal(200_000),
        commissionAmount: BigDecimal = BigDecimal.ZERO,
        netAmount: BigDecimal = totalAmount.subtract(commissionAmount)
    ) = ReservationDocument(
        id = id,
        propertyId = propertyId,
        roomTypeId = "rt-1",
        roomId = "room-101",
        guestId = "guest-1",
        guestInfo = ReservationDocument.GuestInfoData("홍길동", "010-1234-5678", null),
        dateRange = ReservationDocument.DateRangeData("2026-04-10", checkOut),
        nightCount = 2,
        numberOfGuests = 2,
        status = status,
        channel = ReservationDocument.ReservationChannelData(channelCode, null, commissionRate),
        pricing = ReservationDocument.PricingData(
            roomRateAmount = totalAmount,
            roomRateCurrency = "KRW",
            additionalChargesAmount = BigDecimal.ZERO,
            totalAmount = totalAmount,
            commissionAmount = commissionAmount,
            netAmount = netAmount
        ),
        memberId = null,
        expiresAt = null,
        version = 0L,
        createdAt = Instant.now(clock),
        updatedAt = Instant.now(clock)
    )

    @Nested
    inner class `채널별_정산_집계` {

        @Test
        fun `CHECKED_OUT과 NO_SHOW 예약만 집계한다`() {
            reservationMongoDao.save(reservationDoc("rsv-1", status = ReservationStatus.CHECKED_OUT))
            reservationMongoDao.save(reservationDoc("rsv-2", status = ReservationStatus.NO_SHOW))
            reservationMongoDao.save(reservationDoc("rsv-3", status = ReservationStatus.CANCELLED))
            reservationMongoDao.save(reservationDoc("rsv-4", status = ReservationStatus.CONFIRMED))
            reservationMongoDao.save(reservationDoc("rsv-5", status = ReservationStatus.PENDING))

            val results = settlementQueryRepository.findChannelSettlements(
                "prop-1", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)
            )

            assertThat(results).hasSize(1)
            assertThat(results[0].reservationCount).isEqualTo(2)
            assertThat(results[0].totalRevenue.amount).isEqualByComparingTo(BigDecimal(400_000))
        }

        @Test
        fun `채널별로 그룹핑하여 집계한다`() {
            reservationMongoDao.save(reservationDoc("rsv-1", status = ReservationStatus.CHECKED_OUT))
            reservationMongoDao.save(reservationDoc("rsv-2", status = ReservationStatus.CHECKED_OUT))
            reservationMongoDao.save(reservationDoc(
                "rsv-3", status = ReservationStatus.CHECKED_OUT,
                channelCode = "AGODA", commissionRate = BigDecimal("0.15"),
                totalAmount = BigDecimal(200_000),
                commissionAmount = BigDecimal(30_000),
                netAmount = BigDecimal(170_000)
            ))

            val results = settlementQueryRepository.findChannelSettlements(
                "prop-1", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)
            )

            assertThat(results).hasSize(2)

            val direct = results.find { it.channelCode == "DIRECT" }!!
            assertThat(direct.reservationCount).isEqualTo(2)
            assertThat(direct.totalRevenue.amount).isEqualByComparingTo(BigDecimal(400_000))
            assertThat(direct.totalCommission.amount).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(direct.netSettlement.amount).isEqualByComparingTo(BigDecimal(400_000))

            val agoda = results.find { it.channelCode == "AGODA" }!!
            assertThat(agoda.reservationCount).isEqualTo(1)
            assertThat(agoda.totalRevenue.amount).isEqualByComparingTo(BigDecimal(200_000))
            assertThat(agoda.totalCommission.amount).isEqualByComparingTo(BigDecimal(30_000))
            assertThat(agoda.netSettlement.amount).isEqualByComparingTo(BigDecimal(170_000))
        }

        @Test
        fun `기간 외 체크아웃 예약은 제외한다`() {
            reservationMongoDao.save(
                reservationDoc("rsv-1", status = ReservationStatus.CHECKED_OUT, checkOut = "2026-04-15")
            )
            reservationMongoDao.save(
                reservationDoc("rsv-2", status = ReservationStatus.CHECKED_OUT, checkOut = "2026-03-15")
            )
            reservationMongoDao.save(
                reservationDoc("rsv-3", status = ReservationStatus.CHECKED_OUT, checkOut = "2026-05-15")
            )

            val results = settlementQueryRepository.findChannelSettlements(
                "prop-1", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)
            )

            assertThat(results).hasSize(1)
            assertThat(results[0].reservationCount).isEqualTo(1)
        }

        @Test
        fun `다른 propertyId의 예약은 제외한다`() {
            reservationMongoDao.save(
                reservationDoc("rsv-1", propertyId = "prop-1", status = ReservationStatus.CHECKED_OUT)
            )
            reservationMongoDao.save(
                reservationDoc("rsv-2", propertyId = "prop-2", status = ReservationStatus.CHECKED_OUT)
            )

            val results = settlementQueryRepository.findChannelSettlements(
                "prop-1", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)
            )

            assertThat(results).hasSize(1)
            assertThat(results[0].reservationCount).isEqualTo(1)
        }

        @Test
        fun `정산 대상이 없으면 빈 리스트를 반환한다`() {
            val results = settlementQueryRepository.findChannelSettlements(
                "prop-1", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)
            )

            assertThat(results).isEmpty()
        }
    }
}
