package com.stayops.shared.config

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.auth.domain.model.PropertyRole
import com.stayops.auth.domain.repository.MemberRepository
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
@Profile("local")
class LocalDummyDataInitializer(
    private val memberRepository: MemberRepository,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val roomInventoryRepository: RoomInventoryRepository,
    private val channelRepository: ChannelRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (propertyRepository.findById(PROPERTY_ID) != null) {
            return // already initialized
        }

        initMembers()
        initProperty()
        initRoomTypes()
        initRooms()
        initInventory()
        initChannel()

        println("""

            ============================================
            [LOCAL] 더미 데이터 초기화 완료
            운영자: owner@dummy.com / password123
            고객:   guest@dummy.com / password123
            숙소:   스테이옵스 호텔 (ACTIVE)
            객실:   스탠다드 5실, 디럭스 3실
            재고:   오늘부터 30일간
            채널:   자사 예약 (DIRECT)
            ============================================

        """.trimIndent())
    }

    private fun initMembers() {
        val hash = passwordEncoder.encode("password123")!!

        val owner = Member.create(
            id = OWNER_ID,
            email = "owner@dummy.com",
            passwordHash = hash,
            name = "김사장",
            role = MemberRole.OWNER
        ).grantAccess(PROPERTY_ID, PropertyRole.OWNER)
        memberRepository.save(owner)

        val customer = Member.create(
            id = CUSTOMER_ID,
            email = "guest@dummy.com",
            passwordHash = hash,
            name = "이고객",
            role = MemberRole.CUSTOMER
        )
        memberRepository.save(customer)
    }

    private fun initProperty() {
        val property = Property.create(
            id = PROPERTY_ID,
            ownerId = OWNER_ID,
            name = "스테이옵스 호텔",
            type = PropertyType.HOTEL,
            address = Address.of("테헤란로 123", "서울", "강남구", "06234", "KR"),
            contactInfo = ContactInfo.of("02-1234-5678", "info@stayops-hotel.com", "https://stayops-hotel.com"),
            description = "강남 중심의 비즈니스 호텔",
            timezone = "Asia/Seoul",
            currency = "KRW"
        ).activate()
        propertyRepository.save(property)
    }

    private fun initRoomTypes() {
        val standard = RoomType.create(
            id = RT_STANDARD,
            propertyId = PROPERTY_ID,
            name = "스탠다드",
            description = "기본 객실 (퀸 베드 1개)",
            maxOccupancy = 2,
            basePrice = Money.of(BigDecimal.valueOf(80000), "KRW"),
            amenities = listOf("Wi-Fi", "TV", "에어컨", "미니바")
        )
        roomTypeRepository.save(standard)

        val deluxe = RoomType.create(
            id = RT_DELUXE,
            propertyId = PROPERTY_ID,
            name = "디럭스",
            description = "넓은 객실 (킹 베드 1개, 시티뷰)",
            maxOccupancy = 3,
            basePrice = Money.of(BigDecimal.valueOf(150000), "KRW"),
            amenities = listOf("Wi-Fi", "TV", "에어컨", "미니바", "욕조", "시티뷰")
        )
        roomTypeRepository.save(deluxe)
    }

    private fun initRooms() {
        for (i in 1..5) {
            val room = Room.create(
                id = "room-dummy-10$i",
                propertyId = PROPERTY_ID,
                roomTypeId = RT_STANDARD,
                roomNumber = "10$i",
                floor = 1
            )
            roomRepository.save(room)
        }
        for (i in 1..3) {
            val room = Room.create(
                id = "room-dummy-20$i",
                propertyId = PROPERTY_ID,
                roomTypeId = RT_DELUXE,
                roomNumber = "20$i",
                floor = 2
            )
            roomRepository.save(room)
        }
    }

    private fun initInventory() {
        val today = LocalDate.now()
        for (d in 0L until 30L) {
            val date = today.plusDays(d)
            val stdInv = RoomInventory.create(
                id = "inv-dummy-std-$date",
                propertyId = PROPERTY_ID,
                roomTypeId = RT_STANDARD,
                date = date,
                totalCount = 5
            )
            roomInventoryRepository.save(stdInv)

            val dlxInv = RoomInventory.create(
                id = "inv-dummy-dlx-$date",
                propertyId = PROPERTY_ID,
                roomTypeId = RT_DELUXE,
                date = date,
                totalCount = 3
            )
            roomInventoryRepository.save(dlxInv)
        }
    }

    private fun initChannel() {
        val channel = Channel.createDirect(
            id = "ch-dummy-direct",
            propertyId = PROPERTY_ID
        )
        channelRepository.save(channel)
    }

    companion object {
        private const val OWNER_ID = "owner-dummy-001"
        private const val CUSTOMER_ID = "customer-dummy-001"
        private const val PROPERTY_ID = "property-dummy-001"
        private const val RT_STANDARD = "rt-dummy-standard"
        private const val RT_DELUXE = "rt-dummy-deluxe"
    }
}
