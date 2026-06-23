package com.stayops.channel.infrastructure.webhook

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HmacWebhookSignatureVerifierTest : BehaviorSpec({

    val verifier = HmacWebhookSignatureVerifier()

    fun sign(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
        return "sha256=$hash"
    }

    given("HMAC 서명 검증 시") {
        val secret = "test-webhook-secret"
        val payload = """{"eventId":"evt-1","eventType":"BOOKING"}"""

        `when`("유효한 서명이면") {
            val signature = sign(secret, payload)

            then("true를 반환한다") {
                verifier.verify(secret, payload, signature) shouldBe true
            }
        }

        `when`("서명이 다르면") {
            then("false를 반환한다") {
                verifier.verify(secret, payload, "sha256=invalid") shouldBe false
            }
        }

        `when`("sha256= 접두사가 없으면") {
            then("false를 반환한다") {
                verifier.verify(secret, payload, "abc123") shouldBe false
            }
        }

        `when`("다른 시크릿으로 서명하면") {
            val wrongSignature = sign("wrong-secret", payload)

            then("false를 반환한다") {
                verifier.verify(secret, payload, wrongSignature) shouldBe false
            }
        }
    }
})
