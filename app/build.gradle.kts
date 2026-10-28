import java.util.Properties

plugins {
    alias(libs.plugins.aitranslator.android.application)
}

// Release signing: keystore.properties (never committed, see docs/release.md) or the same keys as
// environment variables in CI. Without them, release builds fall back to the debug key for testing.
val keystore = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { key ->
        System.getenv("RELEASE_" + key.uppercase())?.let { setProperty(key, it) }
    }
}

android {
    namespace = "io.github.thucnguyen79.aitranslator"

    defaultConfig {
        applicationId = "io.github.thucnguyen79.aitranslator"
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystore.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 stays off: sherpa-onnx and the Bergamot bridge look up Kotlin classes from native code.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:speech"))
    implementation(project(":core:translate"))
    implementation(project(":core:tts"))
    implementation(project(":core:packs"))
    implementation(project(":core:engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.org.json)
}
