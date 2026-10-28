package io.github.thucnguyen79.aitranslator.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Compose dependencies; callers apply `org.jetbrains.kotlin.plugin.compose` and set `buildFeatures.compose`. */
internal fun Project.addComposeDependencies() {
    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("implementation", libs.findLibrary("androidx-compose-foundation").get())
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}
