plugins {
    id("ifc.android.library")
    id("ifc.kotlin.serialization")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    namespace = "io.github.chrisjmendoza.yearal.core.navigation"
}

dependencies {
    api(libs.findLibrary("androidx-navigation3-runtime").get())
    implementation(libs.findLibrary("kotlinx-serialization-json").get())
}
