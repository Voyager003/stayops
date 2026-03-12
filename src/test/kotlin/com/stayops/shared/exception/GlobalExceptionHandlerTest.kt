package com.stayops.shared.exception

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GlobalExceptionHandlerTest : BehaviorSpec({

    val handler = GlobalExceptionHandler()

    given("BusinessException 발생 시") {
        val exception = BusinessException("INVALID_DATE_RANGE", "체크아웃은 체크인보다 이후여야 합니다.")

        `when`("핸들러가 처리하면") {
            val response = handler.handleBusinessException(exception)

            then("HTTP 400을 반환한다") {
                response.statusCode.value() shouldBe 400
            }
            then("에러 코드와 메시지가 포함된다") {
                response.body!!.code shouldBe "INVALID_DATE_RANGE"
                response.body!!.message shouldBe "체크아웃은 체크인보다 이후여야 합니다."
            }
            then("timestamp가 포함된다") {
                response.body!!.timestamp shouldNotBe null
            }
        }
    }

    given("NotFoundException 발생 시") {
        val exception = NotFoundException("PROPERTY_NOT_FOUND", "숙소를 찾을 수 없습니다.")

        `when`("핸들러가 처리하면") {
            val response = handler.handleNotFoundException(exception)

            then("HTTP 404를 반환한다") {
                response.statusCode.value() shouldBe 404
            }
            then("에러 코드와 메시지가 포함된다") {
                response.body!!.code shouldBe "PROPERTY_NOT_FOUND"
                response.body!!.message shouldBe "숙소를 찾을 수 없습니다."
            }
        }
    }

    given("ConflictException 발생 시") {
        val exception = ConflictException("INVENTORY_CONFLICT", "재고 충돌이 발생했습니다. 다시 시도해 주세요.")

        `when`("핸들러가 처리하면") {
            val response = handler.handleConflictException(exception)

            then("HTTP 409를 반환한다") {
                response.statusCode.value() shouldBe 409
            }
            then("에러 코드와 메시지가 포함된다") {
                response.body!!.code shouldBe "INVENTORY_CONFLICT"
                response.body!!.message shouldBe "재고 충돌이 발생했습니다. 다시 시도해 주세요."
            }
        }
    }
})
