# Project Janus — R8/ProGuard rules for the :app module (release builds only)

# ---- Kotlin coroutines ----
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---- Room ----
# Room generates DAO implementations at compile time via KSP; keep entities
# and generated classes intact so reflection-free generated code still resolves.
-keep class com.janus.app.data.local.** { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.**

# ---- DataStore ----
-dontwarn androidx.datastore.**

# ---- Credential Manager / Google Identity (optional auth module) ----
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.an
































djust if serialization strategy changes.
-keepclassmembers class com.janus.app.domain.model.** {
    <fields>;
}

# ---- General Android/Compose ----
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---- Keep JanusApplication entry point and MainActivity ----
-keep class com.janus.app.JanusApplication { *; }
-keep class com.janus.app.MainActivity { *; }

# ---- Line numbers for readable crash stack traces in release ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile