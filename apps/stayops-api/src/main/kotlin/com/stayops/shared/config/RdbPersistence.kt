package com.stayops.shared.config

import org.springframework.context.annotation.Profile

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Profile("rdb")
annotation class RdbPersistence
