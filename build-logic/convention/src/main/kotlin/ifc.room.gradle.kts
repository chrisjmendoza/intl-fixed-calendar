import androidx.room3.gradle.RoomExtension

// Convention for Room 3 (package androidx.room3, KSP-only, coroutines-only, SQLiteDriver —
// docs/ARCHITECTURE.md §1 "Stack decisions"). Exported schemas are committed under <module>/schemas so
// migrations can be tested (docs/ARCHITECTURE.md §6). Verified by the M0 spike, docs/adr/0001-toolchain.md.

plugins {
    id("com.google.devtools.ksp")
    id("androidx.room3")
}

extensions.configure<RoomExtension> {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    "implementation"(libs.findLibrary("androidx-room3-runtime").get())
    "implementation"(libs.findLibrary("androidx-sqlite-bundled").get())
    "ksp"(libs.findLibrary("androidx-room3-compiler").get())
    // Robolectric DAO tests run on the desktop JVM, whose sqliteJni natives ship only in the JVM variant.
    "testImplementation"(libs.findLibrary("androidx-sqlite-bundled-jvm").get())
}
