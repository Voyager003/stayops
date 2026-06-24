package com.stayops.shared.domain

import com.stayops.channel.domain.model.SyncTask
import com.stayops.guest.domain.model.Guest
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.member.domain.model.Member
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.reservation.domain.model.Reservation
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DomainEntityShapeTest : BehaviorSpec({

    given("상태 전이와 식별자를 가진 핵심 도메인 객체") {
        then("data class가 아니다") {
            listOf(
                Reservation::class,
                Payment::class,
                RoomInventory::class,
                Guest::class,
                Member::class,
                PaymentOutboxMessage::class,
                SyncTask::class
            ).forEach { klass ->
                klass.isData shouldBe false
                klass.java.declaredMethods.any {
                    it.name.matches(Regex("component\\d+")) ||
                        it.name == "copy" ||
                        it.name == "copy\$default"
                } shouldBe false
            }
        }
    }

    given("값 자체가 동일성인 Value Object") {
        then("data class로 유지한다") {
            listOf(
                Money::class,
                DateRange::class
            ).forEach { klass ->
                klass.isData shouldBe true
            }
        }
    }
})
