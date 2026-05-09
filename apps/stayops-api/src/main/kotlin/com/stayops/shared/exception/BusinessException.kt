package com.stayops.shared.exception

class BusinessException(
    val code: String,
    override val message: String
) : RuntimeException(message)
