package com.stayops.reservation.application.service

import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.domain.model.ReservationIntentRequest
import com.stayops.reservation.domain.model.ReservationIntentRequestAlreadyExistsException
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.reservation.domain.repository.ReservationIntentRequestRepository
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.LocalDate

@Service
class CustomerReservationIntentIdempotencyApplication(
    private val creationApplication: CustomerReservationIntentCreationApplication,
    private val requestRepository: ReservationIntentRequestRepository,
    private val intentRepository: ReservationIntentRepository,
    private val paymentService: ReservationPaymentService
) {
    fun createReservationIntent(
        idempotencyKey: String,
        command: CustomerReservationIntentCommand
    ): CustomerReservationIntentResult {
        validateIdempotencyKey(idempotencyKey)
        val requestHash = command.fingerprint()
        val existing = requestRepository.findByMemberIdAndIdempotencyKey(command.memberId, idempotencyKey)
        if (existing != null) return resolveExisting(existing, requestHash)

        return try {
            creationApplication.createReservationIntent(idempotencyKey, requestHash, command)
        } catch (_: ReservationIntentRequestAlreadyExistsException) {
            val concurrentlyCreated = findConcurrentlyCreated(command.memberId, idempotencyKey)
                ?: throw ConflictException(
                    "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                    "동일한 예약 요청이 처리 중입니다"
                )
            resolveExisting(concurrentlyCreated, requestHash)
        }
    }

    private fun findConcurrentlyCreated(memberId: String, idempotencyKey: String): ReservationIntentRequest? {
        repeat(CONCURRENT_RESULT_LOOKUP_ATTEMPTS) { attempt ->
            requestRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey)?.let { return it }
            try {
                Thread.sleep(CONCURRENT_RESULT_LOOKUP_DELAY_MILLIS * (attempt + 1))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
        return null
    }

    private fun resolveExisting(
        request: ReservationIntentRequest,
        requestHash: String
    ): CustomerReservationIntentResult {
        if (request.requestHash != requestHash) {
            throw ConflictException(
                "IDEMPOTENCY_KEY_REUSED",
                "동일한 멱등성 키를 다른 예약 요청에 사용할 수 없습니다"
            )
        }
        val intent = intentRepository.findById(request.reservationIntentId)
            ?: throw NotFoundException(
                "RESERVATION_INTENT_NOT_FOUND",
                "예약 intent를 찾을 수 없습니다: ${request.reservationIntentId}"
            )
        val payment = paymentService.findByReservationIntentId(request.reservationIntentId)
            ?: throw NotFoundException(
                "PAYMENT_NOT_FOUND",
                "결제 정보를 찾을 수 없습니다: ${request.reservationIntentId}"
            )
        return CustomerReservationIntentResult(intent, payment)
    }

    private fun validateIdempotencyKey(idempotencyKey: String) {
        require(idempotencyKey.length in 1..160) { "Idempotency-Key는 1자 이상 160자 이하여야 합니다" }
        require(idempotencyKey.all { it.isLetterOrDigit() || it in "-_.:" }) {
            "Idempotency-Key에 허용되지 않은 문자가 포함되어 있습니다"
        }
    }

    companion object {
        private const val CONCURRENT_RESULT_LOOKUP_ATTEMPTS = 5
        private const val CONCURRENT_RESULT_LOOKUP_DELAY_MILLIS = 10L
    }
}

data class CustomerReservationIntentCommand(
    val memberId: String,
    val propertyId: String,
    val roomTypeId: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val numberOfGuests: Int,
    val guestName: String,
    val guestPhone: String,
    val guestEmail: String?
) {
    fun fingerprint(): String {
        return hashFields(
            listOf(
                propertyId,
                roomTypeId,
                checkIn.toString(),
                checkOut.toString(),
                numberOfGuests.toString(),
                guestName,
                guestPhone,
                guestEmail.orEmpty()
            )
        )
    }

    fun activeBookingKey(): String = hashFields(
        listOf(
            memberId,
            propertyId,
            roomTypeId,
            checkIn.toString(),
            checkOut.toString()
        )
    )

    private fun hashFields(fields: List<String>): String {
        val canonical = fields.joinToString(separator = "") { value -> "${value.length}:$value" }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
