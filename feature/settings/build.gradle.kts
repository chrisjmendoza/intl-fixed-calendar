plugins {
    id("ifc.android.feature")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    namespace = "io.github.chrisjmendoza.yearal.feature.settings"
}

dependencies {
    // The bundled holiday packs, to list one toggle per pack (FEATURES H5). :core:holidays is a pure-JVM
    // core module exposing HolidaySet values, not a data layer, so the feature boundary rule allows it.
    implementation(project(":core:holidays"))
    implementation(libs.findLibrary("androidx-compose-material-icons-core").get())
}
