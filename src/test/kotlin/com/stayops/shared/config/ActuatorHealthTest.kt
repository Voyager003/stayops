package com.stayops.shared.config

import com.stayops.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ActuatorHealthTest @Autowired constructor(
    private val context: WebApplicationContext
) {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @Nested
    inner class `공개_엔드포인트` {

        @Test
        fun `actuator health는 인증 없이 200을 반환한다`() {
            mockMvc.get("/actuator/health")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value("UP") }
                }
        }

        @Test
        fun `actuator info는 인증 없이 200을 반환한다`() {
            mockMvc.get("/actuator/info")
                .andExpect {
                    status { isOk() }
                }
        }

        @Test
        fun `actuator prometheus는 인증 없이 200을 반환한다`() {
            mockMvc.get("/actuator/prometheus")
                .andExpect {
                    status { isOk() }
                }
        }
    }

    @Nested
    inner class `비공개_엔드포인트` {

        @Test
        fun `actuator env는 인증 없이 접근할 수 없다`() {
            mockMvc.get("/actuator/env")
                .andExpect {
                    status { is4xxClientError() }
                }
        }

        @Test
        fun `actuator beans는 인증 없이 접근할 수 없다`() {
            mockMvc.get("/actuator/beans")
                .andExpect {
                    status { is4xxClientError() }
                }
        }
    }
}
