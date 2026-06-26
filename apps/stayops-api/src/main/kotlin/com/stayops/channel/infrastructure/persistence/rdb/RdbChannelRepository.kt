package com.stayops.channel.infrastructure.persistence.rdb

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelConnectionInfo
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.model.ChannelType
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.jooq.generated.Tables.CHANNELS
import com.stayops.jooq.generated.tables.records.ChannelsRecord
import com.stayops.shared.config.RdbPersistence
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbChannelRepository(
    private val dsl: DSLContext
) : ChannelRepository {

    override fun save(channel: Channel): Channel {
        dsl.insertInto(CHANNELS)
            .set(CHANNELS.ID, channel.id)
            .set(CHANNELS.PROPERTY_ID, channel.propertyId)
            .set(CHANNELS.CODE, channel.code)
            .set(CHANNELS.NAME, channel.name)
            .set(CHANNELS.TYPE, channel.type.name)
            .set(CHANNELS.COMMISSION_RATE, channel.commissionRate)
            .set(CHANNELS.API_ENDPOINT, channel.connectionInfo?.apiEndpoint)
            .set(CHANNELS.STATUS, channel.status.name)
            .set(CHANNELS.VERSION, channel.version)
            .set(CHANNELS.CREATED_AT, channel.createdAt.toOffsetDateTime())
            .set(CHANNELS.UPDATED_AT, channel.updatedAt.toOffsetDateTime())
            .onConflict(CHANNELS.ID)
            .doUpdate()
            .set(CHANNELS.PROPERTY_ID, channel.propertyId)
            .set(CHANNELS.CODE, channel.code)
            .set(CHANNELS.NAME, channel.name)
            .set(CHANNELS.TYPE, channel.type.name)
            .set(CHANNELS.COMMISSION_RATE, channel.commissionRate)
            .set(CHANNELS.API_ENDPOINT, channel.connectionInfo?.apiEndpoint)
            .set(CHANNELS.STATUS, channel.status.name)
            .set(CHANNELS.VERSION, channel.version)
            .set(CHANNELS.CREATED_AT, channel.createdAt.toOffsetDateTime())
            .set(CHANNELS.UPDATED_AT, channel.updatedAt.toOffsetDateTime())
            .execute()

        return findById(channel.id) ?: channel
    }

    override fun findById(id: String): Channel? =
        dsl.selectFrom(CHANNELS)
            .where(CHANNELS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    override fun findByPropertyId(propertyId: String): List<Channel> =
        findAll(CHANNELS.PROPERTY_ID.eq(propertyId))

    override fun findByPropertyIdAndCode(propertyId: String, code: String): Channel? =
        dsl.selectFrom(CHANNELS)
            .where(CHANNELS.PROPERTY_ID.eq(propertyId))
            .and(CHANNELS.CODE.eq(code))
            .fetchOne()
            ?.toDomain()

    override fun findByPropertyIdAndStatus(propertyId: String, status: ChannelStatus): List<Channel> =
        findAll(
            CHANNELS.PROPERTY_ID.eq(propertyId)
                .and(CHANNELS.STATUS.eq(status.name))
        )

    override fun deleteById(id: String) {
        dsl.deleteFrom(CHANNELS)
            .where(CHANNELS.ID.eq(id))
            .execute()
    }

    private fun findAll(condition: Condition): List<Channel> =
        dsl.selectFrom(CHANNELS)
            .where(condition)
            .orderBy(CHANNELS.CODE.asc(), CHANNELS.ID.asc())
            .fetch { record -> record.toDomain() }

    private fun ChannelsRecord.toDomain(): Channel =
        Channel.reconstitute(
            id = get(CHANNELS.ID),
            propertyId = get(CHANNELS.PROPERTY_ID),
            code = get(CHANNELS.CODE),
            name = get(CHANNELS.NAME),
            type = ChannelType.valueOf(get(CHANNELS.TYPE)),
            commissionRate = get(CHANNELS.COMMISSION_RATE),
            connectionInfo = get(CHANNELS.API_ENDPOINT)?.let { ChannelConnectionInfo(apiEndpoint = it) },
            status = ChannelStatus.valueOf(get(CHANNELS.STATUS)),
            version = get(CHANNELS.VERSION),
            createdAt = get(CHANNELS.CREATED_AT).toInstant(),
            updatedAt = get(CHANNELS.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
