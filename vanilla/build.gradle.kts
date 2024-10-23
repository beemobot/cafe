plugins {
    application
    java
    kotlin("jvm") version "2.0.21"
    id("com.google.protobuf") version "0.9.4"
}

group = "gg.beemo.vanilla"
version = "1.0.0"

dependencies {
    // Kotlin
    val kotlinCoroutinesVersion = "1.9.0"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")

    // Beemo shared code
    implementation("gg.beemo.latte:latte")

    // gRPC
    val grpcVersion = "1.68.0"
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("com.google.protobuf:protobuf-kotlin:4.28.2")

    // Logging
    val log4jVersion = "2.24.1"
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.2"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
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
