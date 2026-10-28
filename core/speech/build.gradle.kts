plugins {
    alias(libs.plugins.aitranslator.android.library)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.sherpa.onnx)
    implementation(libs.onnxruntime.android)
    implementation(libs.kotlinx.coroutines.android)
}
