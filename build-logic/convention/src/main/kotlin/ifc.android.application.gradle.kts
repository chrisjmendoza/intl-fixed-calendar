import com.android.build.api.dsl.ApplicationExtension

// Convention for the :app module. Shared Android configuration lives in IfcAndroid.kt; this adds the
// target SDK, the SemVer-derived versionCode (docs/ARCHITECTURE.md §7 "Versioning") and the JUnit4 +
// Robolectric test stack.

plugins {
    id("com.android.application")
    id("com.diffplug.spotless")
}

extensions.configure<ApplicationExtension> {
    configureIfcAndroid(this)

    defaultConfig {
        targetSdk = libs.findVersion("targetSdk").get().requiredVersion.toInt()

        // VERSION_NAME lives in gradle.properties; VERSION_BUILD may be passed by a release job.
        val versionName = providers.gradleProperty("VERSION_NAME").get()
        val build = providers.gradleProperty("VERSION_BUILD").orNull?.toInt() ?: 0
        val (major, minor, patch) = versionName.split('.').map { it.toInt() }
        require(minor < 100 && patch < 100 && build < 100) {
            "VERSION_NAME $versionName / VERSION_BUILD $build overflow the versionCode scheme"
        }
        this.versionName = versionName
        versionCode = major * 1_000_000 + minor * 10_000 + patch * 100 + build
    }
}

dependencies {
    "testImplementation"(libs.findLibrary("junit4").get())
    "testImplementation"(libs.findLibrary("kotest-assertions-core").get())
    "testImplementation"(libs.findLibrary("robolectric").get())
    "testImplementation"(libs.findLibrary("androidx-test-core").get())
    "testImplementation"(libs.findLibrary("androidx-test-ext-junit").get())
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
}
