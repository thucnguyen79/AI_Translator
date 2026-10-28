plugins {
    alias(libs.plugins.aitranslator.android.library)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.coroutines.android)
    // Real org.json for JVM unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20250517")
}
