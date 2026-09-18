plugins {
    id("ifc.android.application")
    id("ifc.android.compose")
    id("ifc.hilt")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    // Base package = applicationId (docs/ARCHITECTURE.md §2 "Package naming"; ROADMAP.md decisions #1
    // and #2, 2026-09-18). Permanent once uploaded to Play.
    namespace = "io.github.chrisjmendoza.yearal"
    defaultConfig {
        applicationId = "io.github.chrisjmendoza.yearal"
    }
    buildFeatures {
        // BuildConfig.VERSION_NAME feeds the About row; AGP 9 turns BuildConfig off by default.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:calendar"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:navigation"))
    implementation(project(":core:scheduling"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:converter"))
    implementation(project(":feature:settings"))

    implementation(libs.findLibrary("androidx-core-ktx").get())
    implementation(libs.findLibrary("androidx-activity-compose").get())
    implementation(libs.findLibrary("androidx-compose-material3-adaptive-navigation-suite").get())
    implementation(libs.findLibrary("androidx-compose-material-icons-core").get())
    implementation(libs.findLibrary("androidx-navigation3-runtime").get())
    implementation(libs.findLibrary("androidx-navigation3-ui").get())
    implementation(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    implementation(libs.findLibrary("androidx-lifecycle-viewmodel-navigation3").get())
    implementation(libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
    implementation(libs.findLibrary("kotlinx-coroutines-android").get())
    implementation(libs.findLibrary("kotlinx-serialization-json").get())

    testImplementation(project(":core:testing"))
    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
}
