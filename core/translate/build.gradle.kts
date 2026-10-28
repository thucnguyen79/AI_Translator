plugins {
    alias(libs.plugins.aitranslator.android.library)
}

// Bergamot (Mozilla, MPL-2.0) is compiled from the third_party/bergamot-translator submodule
// (`python tools/deps.py bergamot`). Marian needs glob()/iconv() from API 28 (see minSdk).
val bergamotDir = rootProject.file("third_party/bergamot-translator")

android {
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DBERGAMOT_DIR=${bergamotDir.absolutePath.replace('\\', '/')}",
                    // Submodules are initialised by tools/deps.py; don't let CMake clone the rest.
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
}

val checkBergamot by tasks.registering {
    val marker = File(bergamotDir, "3rd_party/marian-dev/CMakeLists.txt")
    doLast { check(marker.isFile) { "Bergamot sources missing. Run: python tools/deps.py bergamot" } }
}
tasks.named("preBuild") { dependsOn(checkBergamot) }

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
}
