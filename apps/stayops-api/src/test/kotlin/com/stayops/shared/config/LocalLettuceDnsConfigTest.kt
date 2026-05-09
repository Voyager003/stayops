package com.stayops.shared.config

import org.junit.jupiter.api.Test
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.assertj.core.api.Assertions.assertThat

class LocalLettuceDnsConfigTest {

    @Test
    fun `should_register_client_resources_when_local_profile_is_active`() {
        ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=local")
            .withUserConfiguration(LocalLettuceDnsConfig::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(ClientResourcesBuilderCustomizer::class.java)
            }
    }

    @Test
    fun `should_not_register_client_resources_when_local_profile_is_not_active`() {
        ApplicationContextRunner()
            .withUserConfiguration(LocalLettuceDnsConfig::class.java)
            .run { context ->
                assertThat(context).doesNotHaveBean(ClientResourcesBuilderCustomizer::class.java)
            }
    }
}
