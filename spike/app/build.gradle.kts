import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// sherpa-onnx (Apache-2.0) is not on Maven Central: `python tools/spike.py deps` downloads the
// official AAR from GitHub Releases into app/libs (git-ignored) and checks its SHA-256.
val sherpaAar = file("libs/sherpa-onnx-${libs.versions.sherpaOnnx.get()}.aar")

// Bergamot (Mozilla, MPL-2.0) is built from source only when its sources are present in
// src/main/cpp/bergamot (see spike/README.md). Without it the app falls back to ML Kit for MT.
val withBergamot = file("src/main/cpp/bergamot/CMakeLists.txt").exists()

android {
    namespace = "io.github.thucnguyen79.aitranslator.spike"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.thucnguyen79.aitranslator.spike"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-spike"
        // Real phones only; the spike measures on-device performance.
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("boolean", "WITH_BERGAMOT", withBergamot.toString())

        if (withBergamot) {
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        // Submodules are initialised by hand (see spike/README.md); don't let CMake clone the rest.
                        "-DGIT_SUBMODULE=OFF",
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_CPP_FEATURES=exceptions",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DBUILD_SHARED_LIBS=OFF",
                        "-DUSE_STATIC_LIBS=ON",
                        "-DCOMPILE_CUDA=OFF",
                        "-DUSE_WASM_COMPATIBLE_SOURCE=OFF",
                        "-DUSE_TCMALLOC=OFF",
                        "-DSSPLIT_USE_INTERNAL_PCRE2=ON",
                        "-DBUILD_ARCH=armv8-a",
                        "-DCMAKE_CXX_FLAGS=-Wno-enum-constexpr-conversion",
                    )
                    cppFlags += "-std=c++17"
                }
            }
        }
    }

    if (withBergamot) {
        ndkVersion = "28.2.13676358"
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Spike builds are side-loaded for measurements only.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

val checkSherpaAar by tasks.registering {
    val aar = sherpaAar
    doLast {
        check(aar.isFile) { "Missing ${aar.name}. Run: python tools/spike.py deps" }
    }
}
tasks.named("preBuild") { dependsOn(checkSherpaAar) }

dependencies {
    implementation(files(sherpaAar))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    // Closed-source baseline for MT quality comparison only; not planned for the product.
    implementation(libs.mlkit.translate)

    testImplementation(libs.junit4)
}
