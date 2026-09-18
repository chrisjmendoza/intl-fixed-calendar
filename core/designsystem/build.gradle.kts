plugins {
    id("ifc.android.library")
    id("ifc.android.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    namespace = "io.github.chrisjmendoza.yearal.core.designsystem"
}

dependencies {
    api(project(":core:calendar"))
}
