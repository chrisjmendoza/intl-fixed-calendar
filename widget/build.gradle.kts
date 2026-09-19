// The Today home-screen widget (docs/ARCHITECTURE.md §5 "Widget architecture", ROADMAP M5 T1). Glance
// widgets are @Composable, so this module needs the Compose compiler (ifc.android.compose) even though
// it renders no `androidx.compose.ui` tree of its own; Hilt reaches the app's Clock/ZoneProvider
// bindings through an EntryPoint (docs/ARCHITECTURE.md §2 "Dependency direction": only :app depends on
// :widget, so nothing here may depend back on :app).

plugins {
    id("ifc.android.library")
    id("ifc.android.compose")
    id("ifc.hilt")
}

android {
    namespace = "io.github.chrisjmendoza.yearal.widget"
}

dependencies {
    implementation(project(":core:calendar"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))

    implementation(libs.findLibrary("androidx-glance-appwidget").get())
    implementation(libs.findLibrary("androidx-glance-material3").get())
    implementation(libs.findLibrary("kotlinx-coroutines-android").get())

    testImplementation(project(":core:testing"))
    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
    testImplementation(libs.findLibrary("androidx-glance-appwidget-testing").get())
}
