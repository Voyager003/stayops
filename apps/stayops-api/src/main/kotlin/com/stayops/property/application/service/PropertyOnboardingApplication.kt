package com.stayops.property.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.PropertyRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.property.application.dto.CreatePropertyCommand
import com.stayops.property.application.dto.PropertyView
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.exception.NotFoundException
import com.stayops.shared.time.TimeZonePolicy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class PropertyOnboardingResult(
    val property: PropertyView,
    val owner: Member
)

@Service
class PropertyOnboardingApplication(
    private val propertyRepository: PropertyRepository,
    private val memberRepository: MemberRepository,
    private val channelRepository: ChannelRepository,
    private val idGenerator: IdGenerator
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun onboardProperty(command: CreatePropertyCommand): PropertyOnboardingResult {
        return onboardProperty(
            ownerId = command.ownerId,
            name = command.name,
            type = parsePropertyType(command.type),
            address = Address.of(
                street = command.street,
                city = command.city,
                state = command.state,
                zipCode = command.zipCode,
                country = command.country,
                latitude = command.latitude,
                longitude = command.longitude
            ),
            contactInfo = ContactInfo.of(
                phone = command.phone,
                email = command.email,
                website = command.website
            ),
            description = command.description,
            timezone = command.timezone,
            currency = command.currency
        )
    }

    @Transactional
    fun onboardProperty(
        ownerId: String,
        name: String,
        type: PropertyType,
        address: Address,
        contactInfo: ContactInfo,
        description: String,
        timezone: String = TimeZonePolicy.DEFAULT_ZONE_ID,
        currency: String = "KRW"
    ): PropertyOnboardingResult {
        val owner = memberRepository.findById(ownerId)
            ?: throw NotFoundException("OWNER_NOT_FOUND", "숙소 소유자를 찾을 수 없습니다: $ownerId")
        val property = Property.create(
            id = idGenerator.generate(),
            ownerId = ownerId,
            name = name,
            type = type,
            address = address,
            contactInfo = contactInfo,
            description = description,
            timezone = timezone,
            currency = currency
        )
        val savedProperty = propertyRepository.save(property)

        channelRepository.save(Channel.createDirect(idGenerator.generate(), savedProperty.id))

        val savedOwner = memberRepository.save(owner.grantAccess(savedProperty.id, PropertyRole.OWNER))

        log.info("숙소 온보딩 완료: propertyId={}, ownerId={}, name={}", savedProperty.id, ownerId, name)
        return PropertyOnboardingResult(property = PropertyView.from(savedProperty), owner = savedOwner)
    }

    private fun parsePropertyType(type: String): PropertyType =
        runCatching { PropertyType.valueOf(type.uppercase()) }
            .getOrElse { throw IllegalArgumentException("지원하지 않는 숙소 타입입니다: $type") }
}
