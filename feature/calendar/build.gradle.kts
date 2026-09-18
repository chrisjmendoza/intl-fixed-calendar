plugins {
    id("ifc.android.feature")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    namespace = "io.github.chrisjmendoza.yearal.feature.calendar"
}

dependencies {
    // The bundled holiday packs, evaluated per month for the grid's holiday marks and the day detail's
    // holiday list (FEATURES C5, H2, H3; docs/ARCHITECTURE.md §3.3). :core:holidays is a pure-JVM core
    // module exposing HolidaySet values, not a data layer, so the feature boundary rule allows it.
    implementation(project(":core:holidays"))
    implementation(libs.findLibrary("androidx-compose-material-icons-core").get())
}
