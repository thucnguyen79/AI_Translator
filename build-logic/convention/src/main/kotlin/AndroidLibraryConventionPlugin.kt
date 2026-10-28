import com.android.build.api.dsl.LibraryExtension
import io.github.thucnguyen79.aitranslator.buildlogic.configureKotlinAndroid
import io.github.thucnguyen79.aitranslator.buildlogic.derivedNamespace
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                namespace = derivedNamespace
            }
        }
    }
}
