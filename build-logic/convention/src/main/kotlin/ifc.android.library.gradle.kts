import com.android.build.api.dsl.LibraryExtension

// Convention for Android library modules (:core:designsystem, :core:navigation, :core:data, ...).
// Shared Android configuration lives in IfcAndroid.kt; this adds the JUnit4 + Robolectric test
// stack that every Android module uses (docs/ARCHITECTURE.md §6) and ktlint via Spotless.

plugins {
    id("com.android.library")
    id("com.diffplug.spotless")
}

extensions.configure<LibraryExtension> {
    configureIfcAndroid(this)
}

dependencies {
    "testImplementation"(libs.findLibrary("junit4").get())
    "testImplementation"(libs.findLibrary("kotest-assertions-core").get())
    "testImplementation"(libs.findLibrary("robolectric").get())
    "testImplementation"(libs.findLibrary("androidx-test-core").get())
    "testImplementation"(libs.findLibrary("androidx-test-ext-junit").get())
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
}
