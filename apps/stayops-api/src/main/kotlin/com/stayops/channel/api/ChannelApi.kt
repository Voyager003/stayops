package com.stayops.channel.api

import com.stayops.channel.api.dto.*
import com.stayops.channel.application.service.ChannelApplication
import com.stayops.member.infrastructure.security.PropertyAccessChecker
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/channels")
class ChannelApi(
    private val channelApplication: ChannelApplication,
    private val propertyAccessChecker: PropertyAccessChecker
) {

    @PostMapping
    fun createChannel(
        @PathVariable propertyId: String,
        @Valid @RequestBody request: CreateChannelRequest
    ): ResponseEntity<ChannelResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val channel = channelApplication.createOtaChannel(
            propertyId = propertyId,
            code = request.code,
            name = request.name,
            commissionRate = request.commissionRate
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ChannelResponse.from(channel))
    }

    @GetMapping
    fun getChannels(@PathVariable propertyId: String): ResponseEntity<List<ChannelResponse>> {
        propertyAccessChecker.requireAccess(propertyId)
        val channels = channelApplication.findChannels(propertyId)
        return ResponseEntity.ok(channels.map { ChannelResponse.from(it) })
    }

    @GetMapping("/{channelId}")
    fun getChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String
    ): ResponseEntity<ChannelResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val channel = channelApplication.findChannel(propertyId, channelId)
        return ResponseEntity.ok(ChannelResponse.from(channel))
    }

    @PutMapping("/{channelId}")
    fun updateChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String,
        @Valid @RequestBody request: UpdateChannelRequest
    ): ResponseEntity<ChannelResponse> {
        propertyAccessChecker.requireAccess(propertyId)
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
        @PathVariable channelId: String
    ): ResponseEntity<ChannelResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val channel = channelApplication.activateChannel(propertyId, channelId)
        return ResponseEntity.ok(ChannelResponse.from(channel))
    }

    @PatchMapping("/{channelId}/deactivate")
    fun deactivateChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String
    ): ResponseEntity<ChannelResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val channel = channelApplication.deactivateChannel(propertyId, channelId)
        return ResponseEntity.ok(ChannelResponse.from(channel))
    }

    @PatchMapping("/{channelId}/suspend")
    fun suspendChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String
    ): ResponseEntity<ChannelResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val channel = channelApplication.suspendChannel(propertyId, channelId)
        return ResponseEntity.ok(ChannelResponse.from(channel))
    }

    @DeleteMapping("/{channelId}")
    fun deleteChannel(
        @PathVariable propertyId: String,
        @PathVariable channelId: String
    ): ResponseEntity<Void> {
        propertyAccessChecker.requireAccess(propertyId)
        channelApplication.deleteChannel(propertyId, channelId)
        return ResponseEntity.noContent().build()
    }

}
