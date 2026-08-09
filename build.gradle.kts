// Root build.gradle.kts — declares plugin versions ONCE here (apply false),
// so every module below references the same, mutually-compatible versions.
// Do not redeclare these version numbers in app/build.gradle.kts or
// targetserver/build.gradle.kts — just apply the plugin id without a version there.

plugins {
    // Android application plugin (used by :app)
    id("com.android.application") version "8.5.2" apply false

    // Android library plugin (used by :targetserver, since it has no Activity/UI,
    // but still needs android.* APIs like MediaCodec/InputManager — built as a
    // library module, then packaged into a runnable jar/dex for ADB push)
    id("com.android.library") version "8.5.2" apply false

    // Kotlin Android plugin — pinned to a version compatible with the Compose
    // Compiler Gradle plugin below and with AGP 8.5.2
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false

    // Kotlin JVM plugin — used ONLY by :coordmapping (pure Kotlin, no Android)
    id("org.jetbrains.kotlin.jvm") version "2.0.20" apply false

    // Compose Compiler is now a separate Gradle plugin as of Kotlin 2.0+
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false

    // KSP (Kotlin Symbol Processing) — required by Room to generate DAO
    // implementations. Version format is <kotlin-version>-<ksp-build>, so this
    // must be updated in lockstep if the Kotlin version above ever changes.
    id("com.google.devtools.ksp") version "2.0.20-1.0.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}