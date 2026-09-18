plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.compiler.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.room3.gradle.plugin)
    implementation(libs.roborazzi.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
}
