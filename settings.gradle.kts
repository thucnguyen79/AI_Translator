pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // sherpa-onnx AAR (Apache-2.0) fetched from GitHub Releases by tools/deps.py;
        // the binary is git-ignored, only the POM is committed.
        maven { url = uri("third_party/m2") }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "AITranslator"

include(":app")
include(":core:model")
include(":core:speech")
include(":core:translate")
include(":core:tts")
include(":core:packs")
include(":core:engine")
