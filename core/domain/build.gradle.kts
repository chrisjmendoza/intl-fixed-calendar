plugins {
    id("ifc.jvm.library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "api"(project(":core:calendar"))
    "implementation"(libs.findLibrary("kotlinx-coroutines-core").get())
    // RRULE expansion for Recurrence.Gregorian, behind RecurrenceExpander.supports (ADR 0005 decision 11).
    "implementation"(libs.findLibrary("dmfs-lib-recur").get())

    "testImplementation"(project(":core:testing"))
    "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
}
