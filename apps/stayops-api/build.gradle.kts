plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "stayops"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

repositories {
    mavenCentral()
}

val jooqGenerator by configurations.creating

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
    runtimeOnly("org.postgresql:postgresql")
    jooqGenerator("org.jooq:jooq-codegen")
    jooqGenerator("org.jooq:jooq-meta-extensions")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.kotest:kotest-runner-junit5:6.1.0")
    testImplementation("io.kotest:kotest-assertions-core:6.1.0")
    testImplementation("io.mockk:mockk:1.14.2")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mongodb")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

val generatedJooqDir = layout.buildDirectory.dir("generated-src/jooq/main")

sourceSets {
    main {
        java.srcDir(generatedJooqDir)
    }
}

tasks.register<JavaExec>("generateJooq") {
    group = "jooq"
    description = "Generate jOOQ metadata from Flyway SQL migrations."
    classpath = jooqGenerator
    mainClass.set("org.jooq.codegen.GenerationTool")
    workingDir = projectDir
    args("src/main/resources/jooq/jooq-codegen.xml")
    inputs.files(fileTree("src/main/resources/db/migration") { include("*.sql") })
    inputs.file("src/main/resources/jooq/jooq-codegen.xml")
    outputs.dir(generatedJooqDir)
}

tasks.named("compileJava") {
    dependsOn("generateJooq")
}

tasks.named("compileKotlin") {
    dependsOn("generateJooq")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName.set("stayops-0.0.1-SNAPSHOT.jar")
}
