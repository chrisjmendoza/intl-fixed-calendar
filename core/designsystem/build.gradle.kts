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
    // WeekdayDisplay drives the grid's header rows (docs/ARCHITECTURE.md §4); it is part of the
    // public signature of WeekdayHeaders and MonthGrid, hence `api`.
    api(project(":core:domain"))
}
