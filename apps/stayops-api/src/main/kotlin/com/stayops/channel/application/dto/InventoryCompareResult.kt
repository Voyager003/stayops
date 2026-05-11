package com.stayops.channel.application.dto

/**
 * PMS와 OTA 채널 간 재고 비교 결과.
 *
 * Application이 외부 OTA로부터 받은 재고(ExternalInventorySnapshot)와
 * PMS 내부 재고(RoomInventory)를 날짜별로 매칭한 결과를 담는다.
 * Controller는 이 결과를 HTTP 응답 DTO로 매핑해서 반환한다.
 */
data class InventoryCompareResult(
    val channelCode: String,
    val channelName: String,
    val items: List<InventoryCompareItem>
)

data class InventoryCompareItem(
    val date: String,
    val pmsAvailableCount: Int,
    val otaAvailableCount: Int,
    val isSynced: Boolean
)
