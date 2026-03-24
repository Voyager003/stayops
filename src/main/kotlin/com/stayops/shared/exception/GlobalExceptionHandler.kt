package com.stayops.shared.exception

import com.stayops.payment.domain.service.PaymentGatewayException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "INVALID_ARGUMENT", message = ex.message ?: "잘못된 요청입니다", timestamp = Instant.now())
        )
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = ex.code, message = ex.message, timestamp = Instant.now())
        )
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(ex: NotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(code = ex.code, message = ex.message, timestamp = Instant.now())
        )
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflictException(ex: ConflictException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(code = ex.code, message = ex.message, timestamp = Instant.now())
        )
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbiddenException(ex: ForbiddenException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(code = ex.code, message = ex.message, timestamp = Instant.now())
        )
    }

    @ExceptionHandler(PaymentGatewayException::class)
    fun handlePaymentGatewayException(ex: PaymentGatewayException): ResponseEntity<ErrorResponse> {
        val (status, code) = when (ex) {
            is PaymentGatewayException.AlreadyProcessed ->
                HttpStatus.OK to "ALREADY_PROCESSED"
            is PaymentGatewayException.PaymentDeclined ->
                HttpStatus.BAD_REQUEST to "PAYMENT_DECLINED"
            is PaymentGatewayException.ProviderError ->
                HttpStatus.BAD_GATEWAY to "PROVIDER_ERROR"
            is PaymentGatewayException.InvalidRequest ->
                HttpStatus.INTERNAL_SERVER_ERROR to "PAYMENT_INVALID_REQUEST"
            is PaymentGatewayException.UnknownError ->
                HttpStatus.INTERNAL_SERVER_ERROR to "PAYMENT_UNKNOWN_ERROR"
        }
        return ResponseEntity.status(status).body(
            ErrorResponse(code = code, message = ex.message ?: "결제 오류", timestamp = Instant.now())
        )
    }
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant
)
