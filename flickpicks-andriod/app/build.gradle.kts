import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Environment config, mirroring the iOS Debug/Release xcconfigs.
// Resolution order: -P Gradle property (one-off builds, e.g. against a local mock server)
// -> local.properties -> environment variable (CI) -> default.
//   flyk.apiBaseUrl.debug          / FLYK_API_BASE_URL_DEBUG
//   flyk.apiBaseUrl.release        / FLYK_API_BASE_URL_RELEASE
//   flyk.stripePublishableKey      / FLYK_STRIPE_PUBLISHABLE_KEY
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun flykConfig(key: String, default: String): String {
    val envKey = key.replace(Regex("([a-z])([A-Z])"), "$1_$2").replace('.', '_').uppercase()
    return providers.gradleProperty(key).orNull
        ?: localProperties.getProperty(key)
        ?: providers.environmentVariable(envKey).orNull
        ?: default
}

// Retrofit requires base URLs to end with '/'.
fun String.asBaseUrl(): String = if (endsWith("/")) this else "$this/"

android {
    namespace = "com.flykpicks.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.flykpicks.android"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.flykpicks.android.HiltTestRunner"

        buildConfigField(
            "String",
            "STRIPE_PUBLISHABLE_KEY",
            "\"${flykConfig("flyk.stripePublishableKey", "")}\"",
        )
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the host machine as seen from the emulator.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${flykConfig("flyk.apiBaseUrl.debug", "http://10.0.2.2:8080").asBaseUrl()}\"",
            )
        }
        release {
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${flykConfig("flyk.apiBaseUrl.release", "https://api.flykpicks.com").asBaseUrl()}\"",
            )
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // JVM unit tests exercise code that logs via android.util.Log.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.browser)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Async
    implementation(libs.kotlinx.coroutines.android)

    // Networking / JSON
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.adapters)
    ksp(libs.moshi.codegen)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Camera + barcode scanning
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // TOTP QR rendering
    implementation(libs.zxing.core)

    // Payments
    implementation(libs.stripe.android)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
