package io.github.thucnguyen79.aitranslator.spike.core

import android.content.Context
import java.io.File

/**
 * Everything lives in the app's external files dir so it can be filled and read with adb:
 * /sdcard/Android/data/io.github.thucnguyen79.aitranslator.spike/files/
 */
class SpikePaths(context: Context) {
    val root: File = requireNotNull(context.getExternalFilesDir(null)) { "External storage unavailable" }

    val models = File(root, "models")
    val asrModels = File(models, "asr")
    val vadModel = File(models, "vad/silero_vad.onnx")
    val bergamotModels = File(models, "bergamot")

    val testset = File(root, "testset")
    /** Optional override of the bundled sentence set. */
    val sentencesOverride = File(testset, "sentences.tsv")
    val audio = File(testset, "audio")

    val results = File(root, "results")

    fun ensureDirs(context: Context) {
        listOf(asrModels, vadModel.parentFile!!, bergamotModels, audio, results).forEach { it.mkdirs() }
    }

    fun audioDir(lang: Lang, speaker: String) = File(audio, "${lang.code}/$speaker")
}
