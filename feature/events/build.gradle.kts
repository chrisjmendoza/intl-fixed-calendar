plugins {
    id("ifc.android.feature")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    namespace = "io.github.chrisjmendoza.yearal.feature.events"
}

dependencies {
    // The editor's back guard (BackHandler) and the list/editor icons (Add, Search, Clear, ArrowBack,
    // Delete, Check — all in the small curated "core" set, so material-icons-extended is not needed).
    implementation(libs.findLibrary("androidx-activity-compose").get())
    implementation(libs.findLibrary("androidx-compose-material-icons-core").get())
}
