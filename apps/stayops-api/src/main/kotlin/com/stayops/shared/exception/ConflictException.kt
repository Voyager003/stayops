package com.stayops.shared.exception

class ConflictException(
    val code: String,
    override val message: String
) : RuntimeException(message)
