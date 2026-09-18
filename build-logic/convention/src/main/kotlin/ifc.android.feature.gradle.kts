// Convention for :feature:* modules: an Android library with Compose and Hilt, the standard core
// dependencies, and the module-boundary check from docs/ARCHITECTURE.md §2 "Dependency direction":
// a feature may depend on :core:domain, :core:designsystem, :core:navigation, :core:calendar and
// :core:testing, never on :core:data or on another feature (CLAUDE.md rule 10).

plugins {
    id("ifc.android.library")
    id("ifc.android.compose")
    id("ifc.hilt")
}

dependencies {
    "implementation"(project(":core:calendar"))
    "implementation"(project(":core:designsystem"))
    "implementation"(project(":core:domain"))
    "implementation"(project(":core:navigation"))

    "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    "implementation"(libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
    "implementation"(libs.findLibrary("kotlinx-coroutines-android").get())

    "testImplementation"(project(":core:testing"))
    "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
    "testImplementation"(libs.findLibrary("turbine").get())
}

val forbiddenPrefixes = listOf(":feature:", ":core:data")

afterEvaluate {
    configurations
        .filter { it.name.endsWith("implementation", ignoreCase = true) || it.name.endsWith("api", ignoreCase = true) }
        .forEach { configuration ->
            configuration.dependencies
                .filterIsInstance<ProjectDependency>()
                .map { it.path }
                .filter { path -> forbiddenPrefixes.any { path.startsWith(it) } }
                .forEach { path ->
                    throw GradleException(
                        "${project.path} may not depend on $path via ${configuration.name}: features depend only on " +
                            ":core:* interfaces, never on :core:data or another feature (CLAUDE.md rule 10).",
                    )
                }
        }
}
