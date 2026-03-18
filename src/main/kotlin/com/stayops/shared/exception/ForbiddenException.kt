package com.stayops.shared.exception

class ForbiddenException(
    val code: String,
    override val message: String
) : RuntimeException(message)
