package com.stayops.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf {
                ignoringRequestMatchers(
                    "/api/v1/properties/*/channels/webhook/**",
                    "/api/v1/payments/toss/webhooks",
                    "/api/v1/payments/toss/webhooks/**"
                )
            }
            cors { configurationSource = corsConfigurationSource() }
            exceptionHandling {
                authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
            }
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize("/actuator/info", permitAll)
                authorize("/actuator/prometheus", permitAll)
                authorize("/api/v1/auth/**", permitAll)
                authorize("/api/v1/csrf", permitAll)
                authorize("/api/v1/properties/*/channels/webhook/**", permitAll)
                authorize("/api/v1/payments/toss/webhooks", permitAll)
                authorize("/api/v1/payments/toss/webhooks/**", permitAll)
                authorize("/api/v1/customer/auth/**", permitAll)
                authorize("/api/v1/customer/properties/**", permitAll)
                authorize("/api/v1/customer/reservations/**", authenticated)
                authorize(anyRequest, authenticated)
            }
        }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf("http://localhost:5173", "https://stayops-client.vercel.app", "https://learniverse.store")
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
