package com.stayops

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<StayopsApplication>().with(TestcontainersConfiguration::class).run(*args)
}
