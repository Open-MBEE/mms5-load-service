val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val s3_version: String by project
val tika_version: String by project
val testcontainers_version: String by project
val minio_version: String by project

plugins {
    application
    kotlin("jvm") version "1.9.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
    jacoco
}

group = "org.openmbee.flexo.mms"
version = "0.1.1"
application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    implementation("io.ktor:ktor-server-auto-head-response:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-conditional-headers:$ktor_version")
    implementation("io.ktor:ktor-server-default-headers:$ktor_version")
    implementation("io.ktor:ktor-server-forwarded-header:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-serialization-jackson:$ktor_version")

    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("com.amazonaws:aws-java-sdk-s3:$s3_version")
    implementation("software.amazon.awssdk:s3-transfer-manager:2.29.50")
    implementation("software.amazon.awssdk.crt:aws-crt:0.33.7")

    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlin_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")

    testImplementation("org.testcontainers:testcontainers:$testcontainers_version")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainers_version")
    testImplementation("org.testcontainers:minio:$minio_version")

    testImplementation(kotlin("test"))

    val junitVersion = "5.10.1"
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}
tasks {
    test {
        useJUnitPlatform()
    }
}
tasks.test {
    finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}
tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
    }
}
