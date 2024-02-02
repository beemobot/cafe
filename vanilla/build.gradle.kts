plugins {
    application
    java
    kotlin("jvm") version "1.9.20"
}

group = "gg.beemo.vanilla"
version = "1.0.0"

dependencies {
    // Kotlin
    val kotlinCoroutinesVersion = "1.7.3"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")

    // Beemo shared code
    implementation("gg.beemo.latte:latte")

    // Logging
    val log4jVersion = "2.22.0"
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("gg.beemo.vanilla.Vanilla")
    applicationDefaultJvmArgs = listOf(
        "-XX:+AlwaysPreTouch",
        "-XX:+PerfDisableSharedMem",
        "-XX:+UseG1GC",
        "-XX:-OmitStackTraceInFastThrow",
        "-XX:MaxRAMPercentage=80",
        "-XX:MinRAMPercentage=80",
    )
}

// Like `installDist`, but with a stable main jar file name for local development
tasks.register("installDev") {
    tasks.jar.get().apply {
        archiveFileName.set("vanilla.jar")
        manifest {
            attributes["Main-Class"] = application.mainClass.get()
            attributes["Class-Path"] = configurations.runtimeClasspath.get().files.joinToString(" ") { it.name }
        }
    }
    dependsOn("installDist")
}
