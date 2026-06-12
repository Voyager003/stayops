package com.stayops.channel.api

import com.stayops.channel.application.dto.InventoryCompareResult
import com.stayops.channel.application.service.ChannelApplication
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.domain.model.Member
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/channels/{channelId}/inventory")
class ChannelInventoryApi(
    private val memberAccessApplication: MemberAccessApplication,
    private val channelApplication: ChannelApplication
) {

    @GetMapping
    fun compareInventory(
        @PathVariable propertyId: String,
        @PathVariable channelId: String,
        @RequestParam roomTypeId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<InventoryCompareResult> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val result = channelApplication.compareInventory(
            propertyId = propertyId,
            channelId = channelId,
            roomTypeId = roomTypeId,
            startDate = startDate,
            endDate = endDate
        )
        return ResponseEntity.ok(result)
    }
}
