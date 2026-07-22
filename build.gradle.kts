// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Single gate for the whole unit-test suite; hook/CI call this so module/task renames stay local.
tasks.register("unitTests") {
    group = "verification"
    description = "Runs all JVM unit tests: androidApp prod-debug + shared host tests."
    dependsOn(":androidApp:testProdDebugUnitTest", ":shared:testAndroidHostTest")
}
