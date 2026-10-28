import com.android.build.api.dsl.ApplicationExtension
import io.github.thucnguyen79.aitranslator.buildlogic.addComposeDependencies
import io.github.thucnguyen79.aitranslator.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                buildFeatures { compose = true }
            }
            addComposeDependencies()
        }
    }
}
