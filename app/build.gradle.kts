plugins {
    alias(libs.plugins.aitranslator.android.application)
}

android {
    namespace = "io.github.thucnguyen79.aitranslator"

    defaultConfig {
        applicationId = "io.github.thucnguyen79.aitranslator"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Until a release keystore exists, release builds are signed with the debug key for testing.
            signingConfig = signingConfigs.getByName("debug")
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
}
