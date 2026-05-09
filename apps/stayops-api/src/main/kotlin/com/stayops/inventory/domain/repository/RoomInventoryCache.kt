package com.stayops.inventory.domain.repository

import com.stayops.inventory.domain.model.RoomInventory
import java.time.LocalDate

/**
 * 재고 조회 성능 향상을 위한 캐시 포트.
 *
 * Application 레이어가 특정 캐시 구현(Redis 등)에 의존하지 않도록 도메인 레이어에서
 * 인터페이스를 정의한다. 캐시 일관성은 write-through/evict-on-write 전략으로
 * Application이 책임진다.
 */
interface RoomInventoryCache {
    fun get(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory?
    fun put(inventory: RoomInventory)
    fun evict(propertyId: String, roomTypeId: String, date: LocalDate)
}
