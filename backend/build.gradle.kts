import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "3.0.1"
}

group = "com.mentora"
version = "0.1.0"

application {
    mainClass.set("com.mentora.backend.ApplicationKt")
}

tasks.register<JavaExec>("seedDemoData") {
    group = "application"
    description = "Seeds realistic local demo data without removing existing records."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mentora.backend.SeedDataKt")
}

// Phase 7 T3 finding (execution/DECISIONS_LOG.md D151): shadowJar's default merge strategy keeps
// only ONE arbitrary META-INF/services/io.ktor.server.config.ConfigLoader entry when multiple
// dependency jars provide one, instead of merging them - so the fat jar silently ended up only able
// to load YAML config, never our own HOCON application.conf, and EngineMain failed at startup with
// "Neither port nor sslPort specified" (application.conf was never even read). mergeServiceFiles()
// combines every META-INF/services/* provider-configuration file instead of overwriting.
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.0.1"
val koinVersion = "4.1.0"
val mongoDriverVersion = "5.2.0"
val kotlinxSerializationVersion = "1.7.3"
val logbackVersion = "1.5.12"
val bcryptVersion = "0.4"
val jwtVersion = "4.4.0"

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-request-validation:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-partial-content:$ktorVersion")
    implementation("io.ktor:ktor-server-auto-head-response:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")

    // Ktor client (AI Tutor: real Anthropic provider — PHASE_6_SYSTEM_DESIGN.md § 3.2)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    // Kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // MongoDB official Kotlin coroutine driver
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:$mongoDriverVersion")
    implementation("org.mongodb:bson-kotlinx:$mongoDriverVersion")

    // DI
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // Auth
    implementation("org.mindrot:jbcrypt:$bcryptVersion")
    implementation("com.auth0:java-jwt:$jwtVersion")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

tasks.test {
    useJUnitPlatform()
}

ktor {
    fatJar {
        archiveFileName.set("mentora-backend.jar")
    }
}
