plugins {
    id("ifc.android.library")
    id("ifc.hilt")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    namespace = "io.github.chrisjmendoza.yearal.core.scheduling"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.findLibrary("kotlinx-coroutines-core").get())

    testImplementation(project(":core:testing"))
    testImplementation(libs.findLibrary("kotlinx-coroutines-test").get())
}
