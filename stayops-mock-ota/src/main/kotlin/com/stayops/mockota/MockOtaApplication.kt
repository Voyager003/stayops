package com.stayops.mockota

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MockOtaApplication

fun main(args: Array<String>) {
    runApplication<MockOtaApplication>(*args)
}
