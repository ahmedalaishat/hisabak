import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "com.hisabak.shared"
        compileSdk = 36
        minSdk = 29
        // CMP resources ride into the app as Android assets; without this the variant has no
        // assets source and Res lookups crash at runtime with MissingResourceException.
        androidResources.enable = true
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        withHostTest { }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            // api: androidApp builds the database from the shared builder, so it needs Room types.
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            // api: androidApp Koin modules construct the platform DataStore instances.
            api(libs.androidx.datastore.preferences.core)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            // api: androidApp screens read Res.string/Res.font accessors + the CMP resource APIs.
            api(compose.components.resources)
            api(libs.jetbrains.compose.material3)
            api(libs.jetbrains.lifecycle.runtime.compose)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":testutil"))
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            // Retained only to decrypt databases created by <=1.8.x (one-time migration); drop once
            // the upgrade window closes.
            implementation(libs.sqlcipher.android)
            implementation(libs.androidx.sqlite)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.hisabak.shared.resources"
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
