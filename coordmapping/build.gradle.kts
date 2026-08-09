// :coordmapping — pure Kotlin/JVM module, zero Android dependencies.
//
// This is deliberate (see spec #54 / requirement #55): the coordinate
// transform math is the most safety-critical piece of the whole app (a bug
// here means every tap lands on the wrong pixel), so it lives somewhere it
// can be unit-tested with a plain `./gradlew :coordmapping:test` — no
// emulator, no Android framework, runs on any machine with a JDK, including
// directly in GitHub Actions without an Android SDK image.

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}