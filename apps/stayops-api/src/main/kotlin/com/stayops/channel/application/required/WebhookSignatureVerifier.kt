package com.stayops.channel.application.required

interface WebhookSignatureVerifier {
    fun verify(secret: String, payload: String, signature: String): Boolean
}
