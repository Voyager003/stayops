package com.stayops

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(IntegrationTestSupport.LettuceConfig::class)
@SpringBootTest
class StayopsApplicationTests : IntegrationTestSupport() {

    @Test
    fun contextLoads() {
    }

}
