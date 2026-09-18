plugins {
    id("ifc.jvm.library")
    id("ifc.kotlin.serialization")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "api"(project(":core:domain"))
    "implementation"(libs.findLibrary("kotlinx-serialization-json").get())
}
