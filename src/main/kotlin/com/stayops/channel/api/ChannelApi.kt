package com.stayops.channel.api

import com.stayops.channel.api.dto.*
import com.stayops.channel.application.service.ChannelApplication
import com.stayops.shared.security.PropertyAccessChecker
import com.stayops.channel.domain.model.ChannelConnectionInfo
import com.stayops.channel.domain.model.MappingEntry
import com.stayops.channel.domain.model.MappingType
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
        @RequestBody request: CreateChannelRequest
    ): ResponseEntity<ChannelResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val channel = channelApplication.createOtaChannel(
            propertyId = propertyId,
            code = request.code,
            name = request.name,
            commissionRate = request.commissionRate,
            connectionInfo = ChannelConnectionInfo(
                apiEndpoint = request.apiEndpoint,
                apiKey = request.apiKey,
                apiSecret = request.apiSecret,
                webhookSecret = request.webhookSecret
            )
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
        @RequestBody request: UpdateChannelRequest
    ): ResponseEntity<ChannelResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val channel = channelApplication.updateChannel(
            propertyId = propertyId,
            channelId = channelId,
            name = request.name,
            commissionRate = request.commissionRate,
            apiEndpoint = request.apiEndpoint,
            apiKey = request.apiKey,
            apiSecret = request.apiSecret,
            webhookSecret = request.webhookSecret
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

    // -- Mapping API --

    @GetMapping("/{channelCode}/mappings")
    fun getMappings(
        @PathVariable propertyId: String,
        @PathVariable channelCode: String
    ): ResponseEntity<ChannelMappingResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val mapping = channelApplication.getMappings(propertyId, channelCode)
            ?: return ResponseEntity.ok(
                ChannelMappingResponse(propertyId = propertyId, channelCode = channelCode, mappings = emptyList())
            )
        return ResponseEntity.ok(ChannelMappingResponse.from(mapping))
    }

    @PostMapping("/{channelCode}/mappings")
    fun addMapping(
        @PathVariable propertyId: String,
        @PathVariable channelCode: String,
        @RequestBody request: CreateMappingRequest
    ): ResponseEntity<ChannelMappingResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val entry = MappingEntry(
            internalId = request.internalId,
            externalCode = request.externalCode,
            type = request.type
        )
        val mapping = channelApplication.addMapping(propertyId, channelCode, entry)
        return ResponseEntity.status(HttpStatus.CREATED).body(ChannelMappingResponse.from(mapping))
    }

    @DeleteMapping("/{channelCode}/mappings/{internalId}")
    fun removeMapping(
        @PathVariable propertyId: String,
        @PathVariable channelCode: String,
        @PathVariable internalId: String,
        @RequestParam(defaultValue = "ROOM_TYPE") type: MappingType
    ): ResponseEntity<ChannelMappingResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val mapping = channelApplication.removeMapping(propertyId, channelCode, internalId, type)
        return ResponseEntity.ok(ChannelMappingResponse.from(mapping))
    }
}
