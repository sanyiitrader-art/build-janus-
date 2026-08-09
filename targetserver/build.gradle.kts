// :targetserver — the payload pushed to the Target device via ADB and run
// there via app_process (NOT installed as an APK; see spec #19, #48).
//
// Built as an Android LIBRARY module (not "application") because it needs
// android.* APIs (MediaCodec for encoding, InputManager for input
// injection, MediaProjection for screen capture) but has no Activity/UI/
// manifest-launched entry point of its own — Main.kt's main() function is
// invoked directly by app_process on the Target, not through an installed
// app's launcher.
//
// Phase 1 scope: module skeleton + a Main.kt stub only. Real capture/
// encode/input/transport logic lands in Phases 5-8.

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.janus.targetserver"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":coordmapping"))
}