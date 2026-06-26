package com.stayops.channel.infrastructure.persistence.rdb

import com.stayops.channel.domain.model.ChannelMapping
import com.stayops.channel.domain.model.MappingEntry
import com.stayops.channel.domain.model.MappingType
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.jooq.generated.Tables.CHANNEL_MAPPINGS
import com.stayops.jooq.generated.Tables.CHANNEL_MAPPING_ENTRIES
import com.stayops.shared.config.RdbPersistence
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbChannelMappingRepository(
    private val dsl: DSLContext
) : ChannelMappingRepository {

    override fun save(mapping: ChannelMapping): ChannelMapping =
        dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)

            tx.insertInto(CHANNEL_MAPPINGS)
                .set(CHANNEL_MAPPINGS.ID, mapping.id)
                .set(CHANNEL_MAPPINGS.PROPERTY_ID, mapping.propertyId)
                .set(CHANNEL_MAPPINGS.CHANNEL_CODE, mapping.channelCode)
                .set(CHANNEL_MAPPINGS.VERSION, mapping.version)
                .set(CHANNEL_MAPPINGS.CREATED_AT, mapping.createdAt.toOffsetDateTime())
                .set(CHANNEL_MAPPINGS.UPDATED_AT, mapping.updatedAt.toOffsetDateTime())
                .onConflict(CHANNEL_MAPPINGS.ID)
                .doUpdate()
                .set(CHANNEL_MAPPINGS.PROPERTY_ID, mapping.propertyId)
                .set(CHANNEL_MAPPINGS.CHANNEL_CODE, mapping.channelCode)
                .set(CHANNEL_MAPPINGS.VERSION, mapping.version)
                .set(CHANNEL_MAPPINGS.CREATED_AT, mapping.createdAt.toOffsetDateTime())
                .set(CHANNEL_MAPPINGS.UPDATED_AT, mapping.updatedAt.toOffsetDateTime())
                .execute()

            tx.deleteFrom(CHANNEL_MAPPING_ENTRIES)
                .where(CHANNEL_MAPPING_ENTRIES.CHANNEL_MAPPING_ID.eq(mapping.id))
                .execute()

            mapping.mappings.forEach { entry ->
                tx.insertInto(CHANNEL_MAPPING_ENTRIES)
                    .set(CHANNEL_MAPPING_ENTRIES.CHANNEL_MAPPING_ID, mapping.id)
                    .set(CHANNEL_MAPPING_ENTRIES.INTERNAL_ID, entry.internalId)
                    .set(CHANNEL_MAPPING_ENTRIES.EXTERNAL_CODE, entry.externalCode)
                    .set(CHANNEL_MAPPING_ENTRIES.TYPE, entry.type.name)
                    .execute()
            }

            tx.findMappingById(mapping.id) ?: mapping
        }

    override fun findByPropertyIdAndChannelCode(propertyId: String, channelCode: String): ChannelMapping? {
        val mapping = dsl.selectFrom(CHANNEL_MAPPINGS)
            .where(CHANNEL_MAPPINGS.PROPERTY_ID.eq(propertyId))
            .and(CHANNEL_MAPPINGS.CHANNEL_CODE.eq(channelCode))
            .fetchOne()
            ?: return null

        return dsl.toDomain(mapping.get(CHANNEL_MAPPINGS.ID))
    }

    override fun deleteByPropertyIdAndChannelCode(propertyId: String, channelCode: String) {
        val mappingId = dsl.select(CHANNEL_MAPPINGS.ID)
            .from(CHANNEL_MAPPINGS)
            .where(CHANNEL_MAPPINGS.PROPERTY_ID.eq(propertyId))
            .and(CHANNEL_MAPPINGS.CHANNEL_CODE.eq(channelCode))
            .fetchOne(CHANNEL_MAPPINGS.ID)
            ?: return

        dsl.transaction { configuration ->
            val tx = DSL.using(configuration)
            tx.deleteFrom(CHANNEL_MAPPING_ENTRIES)
                .where(CHANNEL_MAPPING_ENTRIES.CHANNEL_MAPPING_ID.eq(mappingId))
                .execute()
            tx.deleteFrom(CHANNEL_MAPPINGS)
                .where(CHANNEL_MAPPINGS.ID.eq(mappingId))
                .execute()
        }
    }

    private fun DSLContext.findMappingById(id: String): ChannelMapping? =
        selectFrom(CHANNEL_MAPPINGS)
            .where(CHANNEL_MAPPINGS.ID.eq(id))
            .fetchOne()
            ?.let { toDomain(id) }

    private fun DSLContext.toDomain(mappingId: String): ChannelMapping {
        val mapping = selectFrom(CHANNEL_MAPPINGS)
            .where(CHANNEL_MAPPINGS.ID.eq(mappingId))
            .fetchOne()!!
        val entries = selectFrom(CHANNEL_MAPPING_ENTRIES)
            .where(CHANNEL_MAPPING_ENTRIES.CHANNEL_MAPPING_ID.eq(mappingId))
            .orderBy(CHANNEL_MAPPING_ENTRIES.TYPE.asc(), CHANNEL_MAPPING_ENTRIES.INTERNAL_ID.asc())
            .fetch { entry ->
                MappingEntry(
                    internalId = entry.get(CHANNEL_MAPPING_ENTRIES.INTERNAL_ID),
                    externalCode = entry.get(CHANNEL_MAPPING_ENTRIES.EXTERNAL_CODE),
                    type = MappingType.valueOf(entry.get(CHANNEL_MAPPING_ENTRIES.TYPE))
                )
            }

        return ChannelMapping.reconstitute(
            id = mapping.get(CHANNEL_MAPPINGS.ID),
            propertyId = mapping.get(CHANNEL_MAPPINGS.PROPERTY_ID),
            channelCode = mapping.get(CHANNEL_MAPPINGS.CHANNEL_CODE),
            mappings = entries,
            version = mapping.get(CHANNEL_MAPPINGS.VERSION),
            createdAt = mapping.get(CHANNEL_MAPPINGS.CREATED_AT).toInstant(),
            updatedAt = mapping.get(CHANNEL_MAPPINGS.UPDATED_AT).toInstant()
        )
    }

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
