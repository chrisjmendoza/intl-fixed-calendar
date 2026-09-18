plugins {
    id("ifc.jvm.library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "api"(project(":core:calendar"))
    "implementation"(libs.findLibrary("kotlinx-coroutines-core").get())

    "testImplementation"(project(":core:testing"))
    "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
}
