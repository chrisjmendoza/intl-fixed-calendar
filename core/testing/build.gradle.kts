plugins {
    id("ifc.jvm.library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "api"(project(":core:domain"))
    "api"(libs.findLibrary("kotlinx-coroutines-core").get())

    "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
}
