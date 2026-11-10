plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass = "net.kustax.opendelivery.backend.ApplicationKt"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.domain)
    implementation(projects.data)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)

    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.bcrypt)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
    implementation(libs.jakarta.mail)

    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.logback)
}
