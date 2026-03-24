package com.stayops.payment.infrastructure.external

import com.stayops.payment.domain.service.PaymentGatewayException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Duration

class TossPaymentsClientTest : BehaviorSpec({

    val objectMapper = JsonMapper.builder().findAndAddModules().build()
    val server = MockWebServer()

    beforeSpec { server.start() }
    afterSpec { server.shutdown() }

    fun createClient(): TossPaymentsClient {
        val properties = TossPaymentsProperties(
            secretKey = "test_sk_123",
            baseUrl = server.url("/").toString(),
            connectTimeout = Duration.ofSeconds(5),
            readTimeout = Duration.ofSeconds(5)
        )
        val restClient = TossPaymentsConfig().tossRestClient(properties, objectMapper)
        return TossPaymentsClient(restClient)
    }

    // -- 결제 승인: 성공 --

    given("결제 승인 시") {

        `when`("Toss가 카드 결제 성공 응답을 반환하면") {
            server.enqueue(
                MockResponse()
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""
                        {
                            "paymentKey": "toss_pk_123",
                            "orderId": "STAYOPS-rsv-1-123",
                            "status": "DONE",
                            "method": "카드",
                            "totalAmount": 200000,
                            "approvedAt": "2026-04-01T12:00:00+09:00",
                            "receipt": {"url": "https://receipt.toss.im/abc"},
                            "card": {
                                "number": "4330****1234",
                                "company": "현대"
                            }
                        }
                    """.trimIndent())
            )

            val client = createClient()
            val result = client.confirm("toss_pk_123", "STAYOPS-rsv-1-123", BigDecimal(200_000))

            then("기본 필드가 매핑된다") {
                result.paymentKey shouldBe "toss_pk_123"
                result.orderId shouldBe "STAYOPS-rsv-1-123"
                result.method shouldBe "카드"
                result.approvedAt shouldNotBe null
            }
            then("totalAmount가 매핑된다") {
                result.totalAmount shouldBe BigDecimal(200_000)
            }
            then("카드 정보가 매핑된다") {
                result.cardNumber shouldBe "4330****1234"
                result.cardCompany shouldBe "현대"
            }
            then("영수증 URL이 매핑된다") {
                result.receiptUrl shouldBe "https://receipt.toss.im/abc"
            }
        }

        `when`("Toss가 카드 정보 없이 성공 응답을 반환하면") {
            server.enqueue(
                MockResponse()
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""
                        {
                            "paymentKey": "toss_pk_simple",
                            "orderId": "STAYOPS-rsv-5-555",
                            "status": "DONE",
                            "method": "계좌이체",
                            "totalAmount": 100000,
                            "approvedAt": "2026-04-01T13:00:00+09:00"
                        }
                    """.trimIndent())
            )

            val client = createClient()
            val result = client.confirm("toss_pk_simple", "STAYOPS-rsv-5-555", BigDecimal(100_000))

            then("카드/영수증 필드는 null이다") {
                result.cardNumber shouldBe null
                result.cardCompany shouldBe null
                result.receiptUrl shouldBe null
            }
        }

        `when`("Toss가 카드 거절 에러를 반환하면 (400)") {
            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"code": "REJECT_CARD_PAYMENT", "message": "카드가 거절되었습니다"}""")
            )

            val client = createClient()

            then("PaymentDeclined 예외가 발생한다") {
                val ex = shouldThrow<PaymentGatewayException.PaymentDeclined> {
                    client.confirm("toss_pk_456", "STAYOPS-rsv-2-456", BigDecimal(100_000))
                }
                ex.code shouldBe "REJECT_CARD_PAYMENT"
            }
        }

        `when`("Toss가 이미 처리된 결제 에러를 반환하면 (400)") {
            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"code": "ALREADY_PROCESSED_PAYMENT", "message": "이미 처리된 결제입니다"}""")
            )

            val client = createClient()

            then("AlreadyProcessed 예외가 발생한다") {
                shouldThrow<PaymentGatewayException.AlreadyProcessed> {
                    client.confirm("toss_pk_789", "STAYOPS-rsv-3-789", BigDecimal(50_000))
                }
            }
        }

        `when`("Toss가 PG 시스템 오류를 반환하면 (500)") {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"code": "PROVIDER_ERROR", "message": "PG사 시스템 오류"}""")
            )

            val client = createClient()

            then("ProviderError 예외가 발생한다") {
                shouldThrow<PaymentGatewayException.ProviderError> {
                    client.confirm("toss_pk_err", "STAYOPS-rsv-4-000", BigDecimal(100_000))
                }
            }
        }
    }

    // -- 결제 취소: 성공 --

    given("결제 취소 시") {

        `when`("Toss가 성공 응답을 반환하면") {
            server.enqueue(
                MockResponse()
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"paymentKey": "toss_pk_123", "status": "CANCELED"}""")
            )

            val client = createClient()
            val result = client.cancel("toss_pk_123", "고객 요청")

            then("PaymentCancelResult로 변환하여 반환한다") {
                result.paymentKey shouldBe "toss_pk_123"
            }
        }

        `when`("Toss가 취소 실패 에러를 반환하면 (500)") {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"code": "FAILED_INTERNAL_SYSTEM_PROCESSING", "message": "내부 시스템 오류"}""")
            )

            val client = createClient()

            then("ProviderError 예외가 발생한다") {
                shouldThrow<PaymentGatewayException.ProviderError> {
                    client.cancel("toss_pk_456", "고객 요청")
                }
            }
        }
    }

    // -- 결제 조회 --

    given("결제 조회 시") {

        `when`("Toss가 DONE 상태 응답을 반환하면") {
            server.enqueue(
                MockResponse()
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""
                        {
                            "paymentKey": "toss_pk_123",
                            "orderId": "STAYOPS-rsv-1-123",
                            "status": "DONE",
                            "method": "카드",
                            "totalAmount": 200000,
                            "approvedAt": "2026-04-01T12:00:00+09:00"
                        }
                    """.trimIndent())
            )

            val client = createClient()
            val result = client.inquire("toss_pk_123")

            then("PaymentInquiryResult로 변환하여 반환한다") {
                result.paymentKey shouldBe "toss_pk_123"
                result.orderId shouldBe "STAYOPS-rsv-1-123"
                result.status shouldBe "DONE"
                result.totalAmount shouldBe BigDecimal(200_000)
            }
        }

        `when`("Toss가 에러를 반환하면") {
            server.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"code": "INVALID_PAYMENT_KEY", "message": "유효하지 않은 paymentKey"}""")
            )

            val client = createClient()

            then("InvalidRequest 예외가 발생한다") {
                shouldThrow<PaymentGatewayException.InvalidRequest> {
                    client.inquire("invalid_pk")
                }
            }
        }
    }
})
