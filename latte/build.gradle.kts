plugins {
    `java-library`
    kotlin("jvm") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.25"
}

group = "gg.beemo.latte"
version = "1.0.0"

dependencies {

    // Kotlin
    val kotlinCoroutinesVersion = "1.9.0"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinCoroutinesVersion")

    // Kafka
    val kafkaVersion = "3.2.3"
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")

    // RabbitMQ
    val rabbitVersion = "5.20.0"
    implementation("com.rabbitmq:amqp-client:$rabbitVersion")

    // JSON
    val moshiVersion = "1.15.1"
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")

    // Misc
    implementation("org.jetbrains:annotations:24.1.0")
    val log4jVersion = "2.24.1"
    compileOnly("org.apache.logging.log4j:log4j-api:$log4jVersion")
    testImplementation("org.apache.logging.log4j:log4j-core:$log4jVersion")

    // JUnit testing framework
    val junitVersion = "5.11.2"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

defaultTasks("build")
