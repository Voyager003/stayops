package com.stayops.channel.domain.service

interface SignatureVerifier {
    fun verify(secret: String, payload: String, signature: String): Boolean
}
