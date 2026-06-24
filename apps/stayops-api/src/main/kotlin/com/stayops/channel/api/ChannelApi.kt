package com.stayops.channel.api

import com.stayops.channel.api.dto.ChannelResponse
import com.stayops.channel.api.dto.CreateChannelRequest
import com.stayops.channel.api.dto.RandomBookingSimulationResponse
import com.stayops.channel.api.dto.UpdateChannelRequest
import com.stayops.channel.application.service.ChannelApplication
import com.stayops.channel.application.service.MockOtaSimulationApplication
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.domain.model.Member
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/channels")
class ChannelApi(
    private val channelApplication: ChannelApplication,
    private val mockOtaSimulationApplication: MockOtaSimulationApplication,
    private val memberAccessApplication: MemberAccessApplication
) {

    @PostMapping
    fun createChannel(
        @PathVariable propertyId: String,
        @Valid @RequestBody request: CreateChannelRequest,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<ChannelResponse> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val channel = channelApplication.createOtaChannel(
            propertyId = propertyId,
            code = request.code,
            name = request.name,
            commissionRate = request.commissionRate
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ChannelResponse.from(channel))
    }

    @GetMapping
    fun getChannels(
        @PathVariable propertyId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<List<ChannelResponse>> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val channels = channelApplication.findChannels(propertyId)
        return ResponseEntity.ok(channels.map { ChannelResponse.from(it) })
    }

    @GetMapping("/{channelId}")
    fun getChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<ChannelResponse> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val channel = channelApplication.findChannel(propertyId, channelId)
        return ResponseEntity.ok(ChannelResponse.from(channel))
    }

    @PutMapping("/{channelId}")
    fun updateChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String,
        @Valid @RequestBody request: UpdateChannelRequest,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<ChannelResponse> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val channel = channelApplication.updateChannel(
            propertyId = propertyId,
            channelId = channelId,
            name = request.name,
            commissionRate = request.commissionRate
        )
        return ResponseEntity.ok(ChannelResponse.from(channel))
    }

    @PatchMapping("/{channelId}/activate")
    fun activateChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<ChannelResponse> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val channel = channelApplication.activateChannel(propertyId, channelId)
        return ResponseEntity.ok(ChannelResponse.from(channel))
    }

    @PatchMapping("/{channelId}/deactivate")
    fun deactivateChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<ChannelResponse> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val channel = channelApplication.deactivateChannel(propertyId, channelId)
        return ResponseEntity.ok(ChannelResponse.from(channel))
    }

    @PatchMapping("/{channelId}/suspend")
    fun suspendChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<ChannelResponse> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val channel = channelApplication.suspendChannel(propertyId, channelId)
        return ResponseEntity.ok(ChannelResponse.from(channel))
    }

    @DeleteMapping("/{channelId}")
    fun deleteChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<Void> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        channelApplication.deleteChannel(propertyId, channelId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{channelId}/simulate-random-booking")
    fun simulateRandomBooking(
        @PathVariable propertyId: String,
        @PathVariable channelId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<RandomBookingSimulationResponse> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val result = mockOtaSimulationApplication.simulateRandomBooking(propertyId, channelId)
        return ResponseEntity.ok(RandomBookingSimulationResponse.from(result))
    }

}
