package com.stayops.channel

import com.stayops.TestcontainersConfiguration
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URI
import java.time.Clock
import java.time.LocalDate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class WebhookE2ETest @Autowired constructor(
    @LocalServerPort private val port: Int,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock
) {

    private lateinit var checkIn: LocalDate
    private lateinit var checkOut: LocalDate

    @BeforeEach
    fun setUp() {
        (clock as MutableClock).set(FixedTestClockConfig.DEFAULT_INSTANT)
        checkIn = LocalDate.now(clock).plusDays(7)
        checkOut = checkIn.plusDays(1)

        mongoTemplate.collectionNames.forEach { name ->
            mongoTemplate.getCollection(name).deleteMany(org.bson.Document())
        }

        // Property (ACTIVE)
        propertyRepository.save(
            Property.create(
                id = "prop-wh", ownerId = "owner-1", name = "Webhook 호텔",
                type = PropertyType.HOTEL,
                address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
                contactInfo = ContactInfo.of("02-1234-5678", "wh@test.com"),
                description = "웹훅 테스트 호텔", timezone = "Asia/Seoul", currency = "KRW"
            ).activate()
        )

        // RoomType
        roomTypeRepository.save(
            RoomType.create(
                id = "rt-wh", propertyId = "prop-wh", name = "스탠다드룸",
                description = "기본 객실", maxOccupancy = 2,
                basePrice = Money.won(100_000)
            )
        )

        // Room -> inventory sync
        roomRepository.save(Room.create("room-wh-1", "prop-wh", "rt-wh", "101", 1))
        inventoryApplication.syncInventoryForRoomType("prop-wh", "rt-wh")

        // Unblock inventory for test dates
        val processed = inventoryApplication.bulkBlock(
            propertyId = "prop-wh",
            roomTypeId = "rt-wh",
            startDate = checkIn,
            endDate = checkOut.minusDays(1),
            daysOfWeek = null,
            action = "UNBLOCK",
            count = 1
        )
        assertThat(processed).isGreaterThan(0)

        // OTA Channel
        channelRepository.save(
            Channel.createOta(
                id = "ch-wh-ota",
                propertyId = "prop-wh",
                code = "TEST_OTA",
                name = "테스트 OTA",
                commissionRate = BigDecimal("0.10"),
                apiEndpoint = "http://localhost:9999"
            )
        )
    }

    private fun hmacSha256(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun postWebhook(body: String, signature: String): Int {
        val conn = URI("http://localhost:$port/api/v1/properties/prop-wh/channels/webhook/TEST_OTA")
            .toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Webhook-Signature", signature)
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
        val status = conn.responseCode
        conn.disconnect()
        return status
    }

    @Nested
    inner class `OTA_웹훅_예약_생성` {

        @Test
        fun `OTA_웹훅_수신_시_예약이_CONFIRMED로_생성된다`() {
            // Given
            val bodyJson = """
                {
                    "eventId": "evt-1",
                    "eventType": "BOOKING",
                    "booking": {
                        "bookingId": "b-1",
                        "roomTypeCode": "rt-wh",
                        "checkInDate": "$checkIn",
                        "checkOutDate": "$checkOut",
                        "guestName": "테스트고객"
                    }
                }
            """.trimIndent()
            val signature = "sha256=${hmacSha256("TEST_OTA", bodyJson)}"

            // When
            val status = postWebhook(bodyJson, signature)

            // Then
            assertThat(status).isEqualTo(200)

            // Query reservations collection
            val reservations = mongoTemplate.find(
                Query.query(Criteria.where("propertyId").`is`("prop-wh")),
                org.bson.Document::class.java,
                "reservations"
            )
            assertThat(reservations).hasSize(1)
            assertThat(reservations[0].getString("status")).isEqualTo(ReservationStatus.CONFIRMED.name)

            // Query room_inventories for the check-in date → reservedCount increased
            val inventories = mongoTemplate.find(
                Query.query(
                    Criteria.where("propertyId").`is`("prop-wh")
                        .and("roomTypeId").`is`("rt-wh")
                        .and("date").`is`(checkIn.toString())
                ),
                org.bson.Document::class.java,
                "room_inventories"
            )
            assertThat(inventories).hasSize(1)
            assertThat(inventories[0].getInteger("reservedCount")).isGreaterThan(0)
        }
    }

    @Nested
    inner class `중복_웹훅_방지` {

        @Test
        fun `중복_웹훅은_무시된다`() {
            // Given
            val bodyJson = """
                {
                    "eventId": "evt-dup",
                    "eventType": "BOOKING",
                    "booking": {
                        "bookingId": "b-dup",
                        "roomTypeCode": "rt-wh",
                        "checkInDate": "$checkIn",
                        "checkOutDate": "$checkOut",
                        "guestName": "중복테스트고객"
                    }
                }
            """.trimIndent()
            val signature = "sha256=${hmacSha256("TEST_OTA", bodyJson)}"

            // When: send same webhook twice
            val status1 = postWebhook(bodyJson, signature)
            val status2 = postWebhook(bodyJson, signature)

            // Then: both return 200 but only 1 reservation created
            assertThat(status1).isEqualTo(200)
            assertThat(status2).isEqualTo(200)

            val reservations = mongoTemplate.find(
                Query.query(Criteria.where("propertyId").`is`("prop-wh")),
                org.bson.Document::class.java,
                "reservations"
            )
            assertThat(reservations).hasSize(1)
        }
    }
}
