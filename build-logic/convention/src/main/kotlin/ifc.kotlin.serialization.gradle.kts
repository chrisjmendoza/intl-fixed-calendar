// Convention for kotlinx.serialization. Every Kotlin plugin must be applied from build-logic's
// classpath: applying one by `alias(libs.plugins...)` in a module resolves a second copy of the Kotlin
// Gradle plugin through the plugin portal, and KGP loaded twice breaks the build (AGP 9 built-in Kotlin
// already loads it once). docs/adr/0001-toolchain.md.

plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}
