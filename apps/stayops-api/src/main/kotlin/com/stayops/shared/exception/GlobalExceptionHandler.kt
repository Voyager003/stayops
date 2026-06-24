package com.stayops.shared.exception

import com.mongodb.MongoException
import com.mongodb.MongoNodeIsRecoveringException
import com.mongodb.MongoNotPrimaryException
import com.mongodb.MongoSocketException
import com.mongodb.MongoTimeoutException
import com.stayops.payment.application.required.PaymentGatewayException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    private val retryAfterSeconds = "3"

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("잘못된 요청: code=INVALID_ARGUMENT, message={}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "INVALID_ARGUMENT", message = ex.message ?: "잘못된 요청입니다", timestamp = Instant.now())
        )
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ResponseEntity<ErrorResponse> {
        log.warn("비즈니스 규칙 위반: code={}, message={}", ex.code, ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = ex.code, message = ex.message, timestamp = Instant.now())
        )
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFoundException(ex: NotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("리소스 미발견: code={}, message={}", ex.code, ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(code = ex.code, message = ex.message, timestamp = Instant.now())
        )
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflictException(ex: ConflictException): ResponseEntity<ErrorResponse> {
        log.warn("충돌: code={}, message={}", ex.code, ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(code = ex.code, message = ex.message, timestamp = Instant.now())
        )
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbiddenException(ex: ForbiddenException): ResponseEntity<ErrorResponse> {
        log.warn("접근 거부: code={}, message={}", ex.code, ex.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(code = ex.code, message = ex.message, timestamp = Instant.now())
        )
    }

    @ExceptionHandler(PaymentGatewayException::class)
    fun handlePaymentGatewayException(ex: PaymentGatewayException): ResponseEntity<ErrorResponse> {
        when (ex) {
            is PaymentGatewayException.AlreadyProcessed ->
                log.warn("이미 처리된 결제: paymentKey={}", ex.paymentKey)
            is PaymentGatewayException.PaymentDeclined ->
                log.warn("결제 거절: code={}, message={}", ex.code, ex.message)
            is PaymentGatewayException.ProviderError ->
                log.error("PG사 시스템 오류: code={}, message={}", ex.code, ex.message)
            is PaymentGatewayException.InvalidRequest ->
                log.error("PG 잘못된 요청: code={}, message={}", ex.code, ex.message)
            is PaymentGatewayException.UnknownError ->
                log.error("알 수 없는 결제 오류: code={}, message={}", ex.code, ex.message)
        }

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

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "유효성 검증 실패"
        log.warn("유효성 검증 실패: field={}, message={}", ex.bindingResult.fieldErrors.firstOrNull()?.field, message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "VALIDATION_ERROR", message = message, timestamp = Instant.now())
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(ex: Exception): ResponseEntity<ErrorResponse> {
        if (ex.isTransientMongoFailure()) {
            log.warn("일시적 MongoDB 장애: type={}, message={}", ex.javaClass.simpleName, ex.message)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds)
                .body(
                    ErrorResponse(
                        code = "TEMPORARILY_UNAVAILABLE",
                        message = "일시적으로 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
                        timestamp = Instant.now()
                    )
                )
        }

        log.error("예상치 못한 오류: message={}", ex.message, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(code = "INTERNAL_ERROR", message = "서버 내부 오류가 발생했습니다", timestamp = Instant.now())
        )
    }

    private fun Throwable.isTransientMongoFailure(): Boolean =
        generateSequence(this) { it.cause }
            .filterIsInstance<MongoException>()
            .any { mongoException ->
                mongoException is MongoTimeoutException ||
                    mongoException is MongoSocketException ||
                    mongoException is MongoNotPrimaryException ||
                    mongoException is MongoNodeIsRecoveringException ||
                    mongoException.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL) ||
                    mongoException.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
            }
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant
)
