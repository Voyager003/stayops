package com.stayops.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager
import java.time.Duration
import java.util.concurrent.TimeUnit

@MongoPersistence
@Configuration
@EnableConfigurationProperties(MongoTimeoutProperties::class)
class MongoConfig {

    @Bean
    fun transactionManager(dbFactory: MongoDatabaseFactory): MongoTransactionManager {
        return MongoTransactionManager(dbFactory)
    }

    @Bean
    fun mongoClientTimeoutCustomizer(properties: MongoTimeoutProperties): MongoClientSettingsBuilderCustomizer {
        return MongoClientSettingsBuilderCustomizer { builder ->
            builder.applyToClusterSettings {
                it.serverSelectionTimeout(properties.serverSelectionTimeout.toMillis(), TimeUnit.MILLISECONDS)
            }
            builder.applyToSocketSettings {
                it.connectTimeout(properties.connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .readTimeout(properties.readTimeout.toMillis(), TimeUnit.MILLISECONDS)
            }
        }
    }
}

@ConfigurationProperties(prefix = "stayops.mongodb.timeout")
data class MongoTimeoutProperties(
    val serverSelectionTimeout: Duration = Duration.ofSeconds(5),
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(5)
)
