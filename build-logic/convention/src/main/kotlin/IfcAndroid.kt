import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** The `libs` version catalog, for use from the precompiled convention scripts. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * Configuration shared by every Android module (application and library): SDK levels from the version
 * catalog, Java 17 bytecode, Kotlin warnings as errors, Robolectric-ready unit tests and Android Lint
 * as a CI error gate. See docs/ARCHITECTURE.md §1 ("Build and platform") and docs/adr/0001-toolchain.md.
 *
 * AGP 9 compiles Kotlin itself (built-in Kotlin, new DSL); modules never apply
 * `org.jetbrains.kotlin.android`. Under the new DSL, `CommonExtension` exposes its sub-blocks as
 * properties only (the `block { }` lambdas moved to the application/library extensions), hence `.apply`.
 */
fun Project.configureIfcAndroid(extension: CommonExtension) {
    extension.compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()

    extension.defaultConfig.apply {
        minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    extension.compileOptions.apply {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    extension.testOptions.unitTests.apply {
        // Robolectric and Roborazzi need the merged resources and manifest on the JVM.
        isIncludeAndroidResources = true
        all { test ->
            // Robolectric 4.17's API 37 runtime reaches into jdk.internal.access, which JDK 17+
            // encapsulates; without this every Robolectric test fails in setUpApplicationState.
            test.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    extension.lint.apply {
        warningsAsErrors = true
        abortOnError = true
        // "A newer version is available" must never turn the build red on its own; dependency
        // updates are Dependabot's job (docs/ARCHITECTURE.md §7). OldTargetApi: targetSdk 36 below
        // compileSdk 37 is deliberate until the Android 17 behaviour changes are reviewed
        // (docs/ARCHITECTURE.md §1, "Beyond 1.0" in ROADMAP.md).
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }
}
