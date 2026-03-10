package com.stayops

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StayopsApplication

fun main(args: Array<String>) {
    runApplication<StayopsApplication>(*args)
}
