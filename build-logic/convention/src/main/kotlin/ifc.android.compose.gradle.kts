import com.android.build.api.dsl.CommonExtension

// Convention for Android modules that contain Compose UI. Applies the Compose compiler plugin, turns
// on the build feature, and adds the BOM-managed Compose dependencies plus the Roborazzi screenshot
// stack (docs/ARCHITECTURE.md §1 "UI and AndroidX" and §6 "Screenshots"). Apply after
// ifc.android.library or ifc.android.application.

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

extensions.configure<CommonExtension> {
    // `buildFeatures.compose` written out in full: inside a precompiled script the bare name `compose`
    // resolves to the Kotlin Compose plugin accessor instead.
    buildFeatures.compose = true
}

dependencies {
    val bom = libs.findLibrary("androidx-compose-bom").get()
    "implementation"(platform(bom))
    "implementation"(libs.findLibrary("androidx-compose-ui").get())
    "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
    "implementation"(libs.findLibrary("androidx-compose-material3").get())
    "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
    "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
    "debugImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())

    "testImplementation"(platform(bom))
    "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
    "testImplementation"(libs.findLibrary("roborazzi").get())
    "testImplementation"(libs.findLibrary("roborazzi-compose").get())
    "testImplementation"(libs.findLibrary("roborazzi-junit-rule").get())
}
