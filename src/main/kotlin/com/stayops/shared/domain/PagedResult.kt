package com.stayops.shared.domain

data class PagedResult<T>(
    val content: List<T>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
)
