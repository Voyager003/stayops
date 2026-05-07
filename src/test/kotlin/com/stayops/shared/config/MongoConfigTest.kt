package com.stayops.shared.config

import com.mongodb.MongoClientSettings
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.util.concurrent.TimeUnit

class MongoConfigTest : BehaviorSpec({

    given("MongoDB timeout customizer") {
        val properties = MongoTimeoutProperties(
            serverSelectionTimeout = Duration.ofSeconds(5),
            connectTimeout = Duration.ofSeconds(2),
            readTimeout = Duration.ofSeconds(5)
        )
        val customizer = MongoConfig().mongoClientTimeoutCustomizer(properties)

        `when`("MongoClientSettings에 적용하면") {
            val builder = MongoClientSettings.builder()
            customizer.customize(builder)
            val settings = builder.build()

            then("server selection timeout을 5초로 제한한다") {
                settings.clusterSettings.getServerSelectionTimeout(TimeUnit.MILLISECONDS) shouldBe 5_000L
            }
            then("connect timeout을 2초로 제한한다") {
                settings.socketSettings.getConnectTimeout(TimeUnit.MILLISECONDS) shouldBe 2_000
            }
            then("read timeout을 5초로 제한한다") {
                settings.socketSettings.getReadTimeout(TimeUnit.MILLISECONDS) shouldBe 5_000
            }
        }
    }
})
