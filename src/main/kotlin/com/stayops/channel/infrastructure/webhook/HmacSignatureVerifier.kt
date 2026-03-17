package com.stayops.channel.infrastructure.webhook

import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class HmacSignatureVerifier {

    fun verify(secret: String, payload: String, signature: String): Boolean {
        val prefix = "sha256="
        if (!signature.startsWith(prefix)) return false

        val expectedHash = signature.removePrefix(prefix)
        val actualHash = hmacSha256(secret, payload)
        return constantTimeEquals(expectedHash, actualHash)
    }

    private fun hmacSha256(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
