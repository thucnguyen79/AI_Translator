import com.android.build.api.dsl.LibraryExtension
import io.github.thucnguyen79.aitranslator.buildlogic.addComposeDependencies
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("aitranslator.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<LibraryExtension> { buildFeatures { compose = true } }
            addComposeDependencies()
        }
    }
}
