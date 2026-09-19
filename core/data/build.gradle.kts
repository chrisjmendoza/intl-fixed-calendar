plugins {
    id("ifc.android.library")
    id("ifc.kotlin.serialization")
    id("ifc.hilt")
    id("ifc.room")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    namespace = "io.github.chrisjmendoza.yearal.core.data"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.findLibrary("androidx-datastore").get())
    implementation(libs.findLibrary("kotlinx-coroutines-core").get())
    implementation(libs.findLibrary("kotlinx-serialization-json").get())

    testImplementation(project(":core:testing"))
    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
    testImplementation(libs.findLibrary("turbine").get())
}
