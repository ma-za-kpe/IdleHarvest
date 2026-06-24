import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinSerialization)
    application
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("${rootProject.projectDir}/config/detekt/detekt.yml")
}

application {
    mainClass.set("com.maku.idleharvest.buyer.BuyerBackendKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.shared)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:${libs.versions.ktor.get()}")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
