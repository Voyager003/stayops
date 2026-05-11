package com.stayops.shared.exception

class NotFoundException(
    val code: String,
    override val message: String
) : RuntimeException(message)
