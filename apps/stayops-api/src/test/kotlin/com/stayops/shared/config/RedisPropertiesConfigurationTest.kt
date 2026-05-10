package com.stayops.shared.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.env.PropertySourcesPropertyResolver
import org.springframework.core.io.ClassPathResource

class RedisPropertiesConfigurationTest {

    @Test
    fun `should_configure_session_defaults_in_application_yml`() {
        val properties = loadYaml("application.yml")

        assertThat(properties.getProperty("spring.session.redis.repository-type")).isEqualTo("default")
        assertThat(properties.getProperty("spring.session.timeout")).isEqualTo("30m")
    }

    @Test
    fun `should_configure_loopback_redis_for_local_profile`() {
        val properties = loadYaml("application-local.yml")

        assertThat(properties.getProperty("spring.data.redis.host")).isEqualTo("127.0.0.1")
        assertThat(properties.getProperty("spring.data.redis.port")).isEqualTo(6379)
    }

    @Test
    fun `should_configure_redis_under_spring_data_in_prod_profile`() {
        val properties = loadYaml("application-prod.yml")
        val resolver = PropertySourcesPropertyResolver(MutablePropertySources().apply {
            addLast(properties)
        })

        assertThat(properties.getProperty("spring.data.redis.host")).isEqualTo("\${SPRING_DATA_REDIS_HOST:redis}")
        assertThat(resolver.getProperty("spring.data.redis.host")).isEqualTo("redis")
        assertThat(properties.getProperty("spring.data.redis.port")).isEqualTo(6379)
    }

    private fun loadYaml(path: String): PropertySource<*> {
        val resource = ClassPathResource(path)
        return YamlPropertySourceLoader().load(path, resource).single()
    }
}
