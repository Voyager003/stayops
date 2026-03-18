package com.stayops.settlement.api

import com.stayops.settlement.api.dto.SettlementResponse
import com.stayops.settlement.application.service.SettlementQueryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/settlements")
class SettlementApi(
    private val settlementQueryService: SettlementQueryService
) {

    @GetMapping
    fun getSettlement(
        @PathVariable propertyId: String,
        @RequestParam startDate: LocalDate,
        @RequestParam endDate: LocalDate
    ): ResponseEntity<SettlementResponse> {
        val summary = settlementQueryService.getSettlementSummary(propertyId, startDate, endDate)
        return ResponseEntity.ok(SettlementResponse.from(summary))
    }
}
