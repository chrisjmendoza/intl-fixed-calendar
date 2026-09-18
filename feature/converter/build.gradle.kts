plugins {
    id("ifc.android.feature")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    namespace = "io.github.chrisjmendoza.yearal.feature.converter"
}

dependencies {
    // Generated dates for the round-trip test (docs/ROADMAP.md M3 exit; docs/ARCHITECTURE.md §6). Kotest
    // is used as a library under JUnit4, as everywhere else; the catalog entry already exists.
    testImplementation(libs.findLibrary("kotest-property").get())
}
