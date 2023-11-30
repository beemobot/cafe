plugins {
    `java-library`
    kotlin("jvm") version "1.9.20"
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
}

group = "gg.beemo.latte"
version = "1.0.0"

dependencies {

    // Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Kafka
    val kafkaVersion = "3.2.3"
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")

    // JSON
    val moshiVersion = "1.14.0"
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")

    // Misc
    implementation("org.jetbrains:annotations:24.1.0")
    compileOnly("org.apache.logging.log4j:log4j-api:2.22.0")

}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

defaultTasks("build")
