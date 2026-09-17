plugins {
    id("ifc.jvm.library")
}

// The acceptance vectors live in docs/calendar-spec.md §6 and are parsed by SpecVectorsTest,
// so the spec and the implementation cannot drift apart silently.
tasks.withType<Test>().configureEach {
    val spec = rootProject.layout.projectDirectory.file("docs/calendar-spec.md")
    inputs.file(spec).withPropertyName("calendarSpec")
    systemProperty("ifc.calendarSpec", spec.asFile.absolutePath)
}
