package com.stayops.shared.config

import org.springframework.context.annotation.Profile

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Profile("mongo")
annotation class MongoPersistence
