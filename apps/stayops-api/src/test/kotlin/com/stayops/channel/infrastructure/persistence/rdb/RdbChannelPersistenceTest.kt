package com.stayops.channel.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.model.MappingEntry
import com.stayops.channel.domain.model.MappingType
import com.stayops.channel.domain.model.ProcessedWebhookEvent
import com.stayops.channel.domain.model.SyncTask
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.model.SyncTaskType
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.ProcessedWebhookEventRepository
import com.stayops.channel.domain.repository.SyncTaskRepository
import com.stayops.jooq.generated.Tables.CHANNELS
import com.stayops.jooq.generated.Tables.CHANNEL_MAPPINGS
import com.stayops.jooq.generated.Tables.CHANNEL_MAPPING_ENTRIES
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PROCESSED_WEBHOOK_EVENTS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.SYNC_TASKS
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringJUnitConfig(classes = [RdbChannelPersistenceTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbChannelPersistenceTest @Autowired constructor(
    private val channelRepository: ChannelRepository,
    private val channelMappingRepository: ChannelMappingRepository,
    private val syncTaskRepository: SyncTaskRepository,
    private val processedWebhookEventRepository: ProcessedWebhookEventRepository,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(
        RdbTestcontainersConfiguration::class,
        RdbChannelRepository::class,
        RdbChannelMappingRepository::class,
        RdbSyncTaskRepository::class,
        RdbProcessedWebhookEventRepository::class
    )
    class TestApplication

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(PROCESSED_WEBHOOK_EVENTS).execute()
        dsl.deleteFrom(SYNC_TASKS).execute()
        dsl.deleteFrom(CHANNEL_MAPPING_ENTRIES).execute()
        dsl.deleteFrom(CHANNEL_MAPPINGS).execute()
        dsl.deleteFrom(CHANNELS).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
    }

    @Nested
    inner class `Channel 저장소` {

        @Test
        fun `Channel을 저장하고 조건별로 조회한다`() {
            insertProperty("prop-1")
            val channel = Channel.createOta(
                id = "ch-1",
                propertyId = "prop-1",
                code = "BOOKING",
                name = "Booking.com",
                commissionRate = java.math.BigDecimal("0.15000"),
                apiEndpoint = "https://ota.example.com"
            )

            channelRepository.save(channel)

            assertThat(channelRepository.findById("ch-1")!!.code).isEqualTo("BOOKING")
            assertThat(channelRepository.findByPropertyIdAndCode("prop-1", "BOOKING")!!.name).isEqualTo("Booking.com")
            assertThat(channelRepository.findByPropertyIdAndStatus("prop-1", ChannelStatus.ACTIVE).map { it.id })
                .containsExactly("ch-1")
        }
    }

    @Nested
    inner class `ChannelMapping 저장소` {

        @Test
        fun `Mapping과 entry를 함께 저장하고 삭제한다`() {
            insertProperty("prop-1")
            val mapping = com.stayops.channel.domain.model.ChannelMapping.create("map-1", "prop-1", "BOOKING")
                .addMapping(MappingEntry("rt-1", "EXT-RT-1", MappingType.ROOM_TYPE))
                .addMapping(MappingEntry("rp-1", "EXT-RP-1", MappingType.RATE_PLAN))

            channelMappingRepository.save(mapping)

            val found = channelMappingRepository.findByPropertyIdAndChannelCode("prop-1", "BOOKING")!!
            assertThat(found.findExternalCode("rt-1", MappingType.ROOM_TYPE)).isEqualTo("EXT-RT-1")
            assertThat(found.findInternalId("EXT-RP-1", MappingType.RATE_PLAN)).isEqualTo("rp-1")

            channelMappingRepository.deleteByPropertyIdAndChannelCode("prop-1", "BOOKING")

            assertThat(channelMappingRepository.findByPropertyIdAndChannelCode("prop-1", "BOOKING")).isNull()
        }
    }

    @Nested
    inner class `SyncTask 저장소` {

        @Test
        fun `처리 가능한 SyncTask를 claim하고 상태별 집계를 반환한다`() {
            insertProperty("prop-1")
            val now = Instant.parse("2026-07-01T00:00:00Z")
            val task = SyncTask.create(
                id = "task-1",
                propertyId = "prop-1",
                channelCode = "BOOKING",
                type = SyncTaskType.AVAILABILITY_UPDATE,
                payload = mapOf("roomTypeId" to "rt-1", "available" to 3),
                now = now
            )
            syncTaskRepository.save(task)

            val ready = syncTaskRepository.findPendingTasksReadyForProcessing(now)
            val claimed = syncTaskRepository.claimReadyForProcessing(
                workerId = "worker-1",
                now = now,
                lockedUntil = now.plusSeconds(60)
            )
            val counts = syncTaskRepository.countByPropertyIdAndChannelCodeGroupByStatus("prop-1", "BOOKING")

            assertThat(ready.map { it.id }).containsExactly("task-1")
            assertThat(claimed!!.status).isEqualTo(SyncTaskStatus.IN_PROGRESS)
            assertThat(claimed.lockedBy).isEqualTo("worker-1")
            assertThat(claimed.payload["roomTypeId"]).isEqualTo("rt-1")
            assertThat(counts[SyncTaskStatus.IN_PROGRESS]).isEqualTo(1)
        }
    }

    @Nested
    inner class `ProcessedWebhookEvent 저장소` {

        @Test
        fun `같은 eventId는 한 번만 저장한다`() {
            insertProperty("prop-1")
            val event = ProcessedWebhookEvent(
                id = "processed-1",
                eventId = "event-1",
                channelCode = "BOOKING",
                propertyId = "prop-1",
                processedAt = Instant.parse("2026-07-01T00:00:00Z")
            )

            assertThat(processedWebhookEventRepository.saveIfAbsent(event)).isTrue()
            assertThat(processedWebhookEventRepository.saveIfAbsent(event.copy(id = "processed-2"))).isFalse()
        }
    }

    private fun insertProperty(propertyId: String) {
        val ownerId = "owner-$propertyId"
        insertMember(ownerId)
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dsl.insertInto(PROPERTIES)
            .set(PROPERTIES.ID, propertyId)
            .set(PROPERTIES.OWNER_ID, ownerId)
            .set(PROPERTIES.NAME, "Stay Ops Hotel")
            .set(PROPERTIES.TYPE, "HOTEL")
            .set(PROPERTIES.ADDRESS_STREET, "1 Test Street")
            .set(PROPERTIES.ADDRESS_CITY, "Seoul")
            .set(PROPERTIES.ADDRESS_STATE, "Seoul")
            .set(PROPERTIES.ADDRESS_ZIP_CODE, "00000")
            .set(PROPERTIES.ADDRESS_COUNTRY, "KR")
            .set(PROPERTIES.CONTACT_PHONE, "010-0000-0000")
            .set(PROPERTIES.CONTACT_EMAIL, "property@stayops.com")
            .set(PROPERTIES.DESCRIPTION, "test property")
            .set(PROPERTIES.STATUS, "ACTIVE")
            .set(PROPERTIES.TIMEZONE, "Asia/Seoul")
            .set(PROPERTIES.CURRENCY, "KRW")
            .set(PROPERTIES.VERSION, 0L)
            .set(PROPERTIES.CREATED_AT, now)
            .set(PROPERTIES.UPDATED_AT, now)
            .execute()
    }

    private fun insertMember(memberId: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(MEMBERS)
            .set(MEMBERS.ID, memberId)
            .set(MEMBERS.EMAIL, "$memberId@stayops.com")
            .set(MEMBERS.PASSWORD_HASH, "hashed-password")
            .set(MEMBERS.NAME, memberId)
            .set(MEMBERS.ROLE, MemberRole.OWNER.name)
            .set(MEMBERS.STATUS, MemberStatus.ACTIVE.name)
            .set(MEMBERS.VERSION, 0L)
            .set(MEMBERS.CREATED_AT, now)
            .set(MEMBERS.UPDATED_AT, now)
            .execute()
    }
}
