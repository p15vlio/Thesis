@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)

    jvm()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            // No external dependencies — domain is framework-agnostic by design
        }
    }
}
