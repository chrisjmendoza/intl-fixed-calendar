import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Convention for pure Kotlin/JVM modules (:core:calendar, :core:domain, :core:holidays).
// Enforces the workflow gates from docs/WORKFLOW.md: explicit API, warnings as errors,
// KDoc on every public declaration, ktlint formatting, JUnit 6 + Kotest.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.dokka")
    id("com.diffplug.spotless")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
    "testImplementation"(libs.findLibrary("kotest-assertions-core").get())
    "testImplementation"(libs.findLibrary("kotest-property").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        showStandardStreams = false
    }
}

// KDoc gate: an undocumented public declaration is a build failure.
dokka {
    dokkaSourceSets.configureEach {
        reportUndocumented.set(true)
    }
    dokkaPublications.configureEach {
        failOnWarning.set(true)
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
}

tasks.named("check") {
    dependsOn("dokkaGenerate")
}
