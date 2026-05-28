package com.stayops.shared.ratelimit

data class RateLimitRule(
    val id: String,
    val method: String,
    val pathPattern: String,
    val limit: Long,
    val windowSeconds: Long,
    val identityType: RateLimitIdentityType
) {
    private val pathRegex = pathPattern.toPathRegex()

    init {
        require(id.isNotBlank()) { "Rate limit rule id must not be blank" }
        require(method.isNotBlank()) { "Rate limit rule method must not be blank" }
        require(pathPattern.startsWith("/")) { "Rate limit path pattern must start with /" }
        require(limit > 0) { "Rate limit must be greater than 0" }
        require(windowSeconds > 0) { "Rate limit window seconds must be greater than 0" }
    }

    fun matches(requestMethod: String, requestPath: String): Boolean =
        method.equals(requestMethod, ignoreCase = true) && pathRegex.matches(requestPath)

    private fun String.toPathRegex(): Regex {
        if (endsWith("/**")) {
            val prefix = removeSuffix("/**")
            return Regex("^${Regex.escape(prefix)}(?:/.*)?$")
        }

        val regex = split("/")
            .joinToString("/") { segment ->
                when {
                    segment.isEmpty() -> ""
                    segment.startsWith("{") && segment.endsWith("}") -> "[^/]+"
                    else -> Regex.escape(segment)
                }
            }
        return Regex("^$regex$")
    }
}

enum class RateLimitIdentityType {
    IP,
    MEMBER
}
