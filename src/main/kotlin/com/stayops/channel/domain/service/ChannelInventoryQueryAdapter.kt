package com.stayops.channel.domain.service

import java.time.LocalDate

/**
 * 외부 OTA 채널로부터 재고를 조회하기 위한 포트.
 *
 * ChannelSyncAdapter가 PMS → OTA 쓰기(pushAvailability)를 담당하는 것과 대칭적으로,
 * 이 포트는 OTA → PMS 읽기(fetchInventory)를 담당한다.
 * 구현체는 실패 시 도메인 예외(BusinessException 등)로 변환하여 던진다.
 */
interface ChannelInventoryQueryAdapter {
    fun fetchInventory(
        apiEndpoint: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ExternalInventorySnapshot>
}

/**
 * 외부 채널이 보고한 특정 날짜의 재고 스냅샷.
 */
data class ExternalInventorySnapshot(
    val roomTypeId: String,
    val date: LocalDate,
    val availableCount: Int
)
