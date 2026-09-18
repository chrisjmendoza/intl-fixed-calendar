// Convention for Hilt: the Hilt Gradle plugin, KSP (never kapt — it is incompatible with AGP 9
// built-in Kotlin) and the runtime + compiler dependencies. docs/ARCHITECTURE.md §1 "Stack decisions".

plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    "implementation"(libs.findLibrary("hilt-android").get())
    "ksp"(libs.findLibrary("hilt-compiler").get())
}
