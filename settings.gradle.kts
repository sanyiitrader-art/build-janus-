pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ProjectJanus"

// Main Controller application module (Compose UI, ADB client, media pipeline, input engine)
include(":app")

// Pure-Kotlin/JVM module: coordinate transform math, unit-testable on desktop
// without any Android dependency (run with `./gradlew :coordmapping:test`)
include(":coordmapping")

// Target-side payload: pushed via ADB and executed on the Target device via
// app_process. NOT an installed APK. Builds to a plain jar/dex artifact.
include(":targetserver")