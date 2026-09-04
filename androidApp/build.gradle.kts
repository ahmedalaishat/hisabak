import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is read from a gitignored keystore.properties (local) or env vars (CI).
// When neither is present, release builds fall back to the debug key so local/CI builds
// still work without any secrets. The prod → Play pipeline opts into strict signing
// (-PrequireReleaseSigning) so a publishable build can never silently use the debug key.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}
fun signingProp(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)
// Real release signing requires a keystore path that actually resolves to a file — a bare env var
// pointing at a missing/empty keystore must not count as "signed".
val hasReleaseSigning = signingProp("storeFile", "RELEASE_KEYSTORE_PATH")
    ?.let { rootProject.file(it).exists() } == true

// When the prod → Play pipeline passes -PrequireReleaseSigning, a missing release keystore is a
// hard error rather than a silent fall back to the debug key: a debug-signed AAB must never reach
// Play. Local builds and the staging (Firebase) build omit the flag and keep the debug-key
// fallback, so they still work without any secrets.
val requireReleaseSigning = providers.gradleProperty("requireReleaseSigning").isPresent
if (requireReleaseSigning && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is required (-PrequireReleaseSigning) but no usable keystore was found. " +
            "Set keystore.properties or the RELEASE_KEYSTORE_* env vars (and verify the keystore " +
            "file exists) before building a publishable release.",
    )
}

// Parse-service endpoint. Supplied via ~/.gradle/gradle.properties or CI env so the token is
// never committed; blank disables the remote parser entirely (AiParserAvailability.Unavailable).
val parseServiceUrl = (project.findProperty("parseServiceUrl") as String?)
    ?: System.getenv("HISABAK_PARSE_URL") ?: ""
val parseServiceToken = (project.findProperty("parseServiceToken") as String?)
    ?: System.getenv("HISABAK_PARSE_TOKEN") ?: ""

android {
    namespace = "com.hisabak"
    // 37: Vico's multiplatform 2.5.x android artifacts require compiling against API 37.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.hisabak"
        minSdk = 29
        targetSdk = 36
        versionCode = 16
        versionName = "2.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PARSE_SERVICE_URL", "\"$parseServiceUrl\"")
        buildConfigField("String", "PARSE_SERVICE_TOKEN", "\"$parseServiceToken\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(signingProp("storeFile", "RELEASE_KEYSTORE_PATH")!!)
                storePassword = signingProp("storePassword", "RELEASE_KEYSTORE_PASSWORD")
                keyAlias = signingProp("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingProp("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
            // applicationId stays com.hisabak (from defaultConfig)
            resValue("string", "app_name", "Hisabak")
            buildConfigField("boolean", "SEED_DATA", "false")
            // Google Play disallows the SMS restricted permission for this use case, so the
            // Play/prod build captures via share + select-text + manual paste only.
            buildConfigField("boolean", "SMS_AUTO_CAPTURE", "false")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "Hisabak STG")
            buildConfigField("boolean", "SEED_DATA", "true")
            // RECEIVE_SMS auto-capture lives in staging only (distributed off-Play via Firebase).
            buildConfigField("boolean", "SMS_AUTO_CAPTURE", "true")
        }
    }

    buildTypes {
        debug {
            // Sign debug with the app's release key when one is configured, so its SHA-1 matches the
            // registered Google OAuth client (Drive backup) and Drive auth works in debug builds.
            // Falls back to the default debug key when no keystore is present (CI / fresh checkouts),
            // preserving the no-secrets build.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // Crashlytics collection is disabled at runtime in debug (see HisabakApp); also skip the
            // mapping-file upload so debug builds don't touch Crashlytics tooling.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real release key when configured (keystore.properties / CI secrets); otherwise the
            // debug key so the build still works locally and in CI without secrets.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Backport java.time (and other Java 8+ APIs) to all minSdk-29 devices. Without this,
        // API 34+ additions like LocalDate.ofInstant throw NoSuchMethodError on older devices.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(project(":shared"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    testImplementation(project(":testutil"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
