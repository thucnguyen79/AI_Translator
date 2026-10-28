plugins {
    alias(libs.plugins.aitranslator.android.library)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
}
