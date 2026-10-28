package io.github.thucnguyen79.aitranslator.core.packs

import io.github.thucnguyen79.aitranslator.core.model.Lang
import java.io.File

/** Where each installed model lives inside the packs described by assets/packs.json. */
class ModelLocator(private val packs: PackManager) {
    val vadModel: File get() = File(packs.dir("core"), "vad/silero_vad.onnx")

    /** Vietnamese has its own zipformer; the other languages share SenseVoice. */
    fun asrDir(lang: Lang): File =
        if (lang == Lang.VI) File(packs.dir("vi"), "asr") else File(packs.dir("speech-multi"), "asr")

    val viPunctuationDir: File get() = File(packs.dir("vi"), "punct")

    /** Bergamot direction folder, e.g. "vien" or "enzh": it belongs to the non-English language's pack. */
    fun bergamotDir(key: String): File {
        val lang = if (key.startsWith("en")) key.substring(2) else key.substring(0, 2)
        return File(packs.dir(lang), "mt/$key")
    }

    /** Null when the optional Supertonic pack is not installed. */
    val supertonicDir: File? get() = File(packs.dir("tts-supertonic"), "tts").takeIf { packs.isInstalled("tts-supertonic") }

    /** Null when the optional language-identification pack is not installed. */
    val lidDir: File? get() = File(packs.dir("lid"), "lid").takeIf { packs.isInstalled("lid") }
}
