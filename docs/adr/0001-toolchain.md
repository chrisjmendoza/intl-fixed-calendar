# 0001. Toolchain: AGP 9.3.3 with built-in Kotlin 2.3.21, KSP 2.3.12, Hilt 2.60.1, Room 3.0.3

- Status: accepted
- Date: 2026-09-17
- Owning doc updated: [ARCHITECTURE.md](../ARCHITECTURE.md) §1 ("Flagged as unverified"), §2 (build-logic), "Development environment"

## Context

[ROADMAP.md](../ROADMAP.md) M0 T3 asked for a spike before any Android module was written, because the
stack in ARCHITECTURE.md §1 sits on lines newer than most agent training data: AGP 9 (new DSL, built-in
Kotlin), Room 3 (`androidx.room3`), Navigation 3, Robolectric 4.17 with SDK 37. ARCHITECTURE.md §1
listed eight unverified points. This ADR records what the spike found on 2026-09-17 and the rules that
follow from it. Everything below was verified by a real build on the development machine (Windows 11,
Android Studio Quail 2 JBR 21.0.10, Gradle 9.7.1).

## Decision

1. **Versions** (all in [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml), the single
   authority): AGP 9.3.3, Kotlin 2.3.21, KSP 2.3.12, Hilt 2.60.1, Room 3.0.3 with `sqlite-bundled` 2.7.1,
   Compose BOM 2026.09.00, Navigation 3 1.1.7, lifecycle 2.11.0 (including
   `lifecycle-viewmodel-navigation3` — it exists at that stable version), Robolectric 4.17, Roborazzi 1.74.0.
   AGP 9.4.0 is out but stays off until Android Studio is on Quail 4 (open decision #9).
2. **Built-in Kotlin, new DSL, no opt-outs.** Android modules apply only `com.android.application` /
   `com.android.library` (through the `ifc.android.*` convention plugins); nobody applies
   `org.jetbrains.kotlin.android`. `android.newDsl` and `android.builtInKotlin` stay at their defaults
   (`true`). The KGP version is raised above AGP's bundled 2.2.10 by putting `kotlin-gradle-plugin` on the
   `build-logic` classpath, which is the documented mechanism.
3. **Every Kotlin compiler plugin is applied from `build-logic`'s classpath** (Compose compiler,
   kotlinx.serialization, KSP, Hilt, Room, Roborazzi) via a convention plugin. Applying one from a module
   with `alias(libs.plugins…)` loads a second copy of KGP through the plugin portal; Gradle then warns
   "The Kotlin Gradle plugin was loaded multiple times", and AGP's built-in Kotlin breaks. The version
   catalog therefore has no `[plugins]` table on purpose.
4. **New-DSL API shape in convention plugins.** `CommonExtension` is no longer parameterised and exposes
   its blocks as properties only (`defaultConfig`, `compileOptions`, `testOptions`, `lint`,
   `buildFeatures`); the `block { }` lambdas exist only on `ApplicationExtension` / `LibraryExtension`.
   Shared configuration lives in `configureIfcAndroid(CommonExtension)` and uses `.apply`. Inside a
   precompiled script `buildFeatures.compose` must be written in full because the bare name `compose`
   resolves to the Compose plugin accessor.
5. **Kotlin compiler options** for Android modules are set on `KotlinJvmCompile` tasks
   (`jvmTarget = 17`, `allWarningsAsErrors = true`), not through a `kotlin {}` extension whose type differs
   between JVM and Android modules. Android modules do **not** use `explicitApi()`: KSP-generated Kotlin
   (Room 3) would fail it. The KDoc gate for Android modules is review-enforced until Dokka is added there.
6. **Robolectric 4.17 with the SDK 37 runtime needs
   `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED`** on the test JVM; without it every test
   fails in `setUpApplicationState`. `configureIfcAndroid` adds it to all unit-test tasks.
7. **Room 3 DAO tests under Robolectric need the JVM variant of the bundled driver** on the test
   classpath (`androidx.sqlite:sqlite-bundled-jvm`): the Android AAR ships only Android ABI `.so` files, so
   `BundledSQLiteDriver` throws `UnsatisfiedLinkError: no sqliteJni` on the desktop JVM. The `ifc.room`
   convention plugin adds it as `testImplementation`. Schema export to `<module>/schemas` works.
8. **Kotlin 2.4.20 is not adopted yet.** In the spike worktree, Kotlin 2.4.20 with KSP 2.3.12, AGP 9.3.3
   and Room 3 compiled and passed the JVM and Robolectric tests; the Compose compiler and Hilt were not
   exercised on 2.4. A follow-up task bumps the whole project and keeps 2.4.x only if the full gate is
   green (ARCHITECTURE.md "Beyond 1.0").
9. **SDK Platform 37 is installed by AGP itself** when missing, because the SDK licence is accepted on
   the machine; no manual SDK Manager step is needed. `cmdline-tools` are still absent and still not
   required by the build.
10. `lint` runs with `warningsAsErrors` and `abortOnError`; the version-nag checks (`GradleDependency`,
    `AndroidGradlePluginVersion`, `NewerVersionAvailable`) are disabled so a library release cannot turn
    the build red by itself.

## Consequences

- The five Android convention plugins (`ifc.android.library`, `ifc.android.compose`, `ifc.hilt`,
  `ifc.android.application`, `ifc.android.feature`) plus `ifc.kotlin.serialization` and `ifc.room` exist
  and `:app:assembleDebug` produces an installable APK.
- Unverified items 1–5 in ARCHITECTURE.md §1 are settled (1 partly: see decision 8); item 4's
  `adaptive-navigation3` and item 6 (lib-recur) remain open until M3/M4 need them.
- Bumping Kotlin, AGP or KSP is a toolchain change and needs a new ADR that supersedes the relevant
  decision here (CLAUDE.md "API generations").
- When Android Studio moves to Quail 4, revisit decision 1 (AGP 9.4) and decision 8 together.
