package io.github.thucnguyen79.aitranslator.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

fun VersionCatalog.versionInt(alias: String): Int = version(alias).toInt()

/** `:core:speech` -> `io.github.thucnguyen79.aitranslator.core.speech`. */
val Project.derivedNamespace: String
    get() {
        val suffix = path.trim(':').split(':').joinToString(".") { it.replace('-', '_') }
        return if (suffix.isEmpty()) BASE_NAMESPACE else "$BASE_NAMESPACE.$suffix"
    }

const val BASE_NAMESPACE = "io.github.thucnguyen79.aitranslator"

/** AGP 9 ships built-in Kotlin, so no `org.jetbrains.kotlin.android` plugin is applied. */
internal fun Project.configureKotlinAndroid(extension: ApplicationExtension) {
    extension.apply {
        compileSdk = libs.versionInt("compileSdk")
        defaultConfig {
            minSdk = libs.versionInt("minSdk")
            targetSdk = libs.versionInt("targetSdk")
            // Real phones only: on-device models are tuned and measured for arm64.
            ndk { abiFilters += "arm64-v8a" }
        }
        compileOptions { configureJava() }
        lint { abortOnError = false }
        packaging {
            // The sherpa-onnx AAR also carries other ABIs; drop them before merging.
            jniLibs.excludes += listOf("lib/x86/**", "lib/x86_64/**", "lib/armeabi-v7a/**")
        }
    }
    configureCommon()
}

internal fun Project.configureKotlinAndroid(extension: LibraryExtension) {
    extension.apply {
        compileSdk = libs.versionInt("compileSdk")
        defaultConfig {
            minSdk = libs.versionInt("minSdk")
            ndk { abiFilters += "arm64-v8a" }
        }
        compileOptions { configureJava() }
        lint { abortOnError = false }
    }
    configureCommon()
}

private fun CompileOptions.configureJava() {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

private fun Project.configureCommon() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    dependencies {
        add("testImplementation", libs.findLibrary("junit4").get())
        add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    }
}
