package io.github.thucnguyen79.aitranslator.spike.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import io.github.thucnguyen79.aitranslator.spike.audio.SAMPLE_RATE
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.core.SpikePaths
import java.io.File

enum class AsrKind { TRANSDUCER, SENSE_VOICE, WHISPER }

/**
 * Candidate offline ASR models. [dir] is the folder name inside models/asr, i.e. the archive name from
 * https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models without `.tar.bz2`.
 */
data class AsrModel(val id: String, val dir: String, val kind: AsrKind, val langs: List<Lang>) {
    fun folder(paths: SpikePaths) = File(paths.asrModels, dir)
    fun isPresent(paths: SpikePaths) = folder(paths).isDirectory
}

object AsrCatalog {
    private val all5 = Lang.entries.toList()

    val models = listOf(
        AsrModel("vi-zipformer-30m", "sherpa-onnx-zipformer-vi-30M-int8-2026-02-09", AsrKind.TRANSDUCER, listOf(Lang.VI)),
        AsrModel("vi-zipformer-70kh", "sherpa-onnx-zipformer-vi-int8-2025-04-20", AsrKind.TRANSDUCER, listOf(Lang.VI)),
        AsrModel(
            "sensevoice-2024", "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17", AsrKind.SENSE_VOICE,
            listOf(Lang.ZH, Lang.EN, Lang.JA, Lang.KO),
        ),
        AsrModel(
            "sensevoice-2025", "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09", AsrKind.SENSE_VOICE,
            listOf(Lang.ZH, Lang.EN, Lang.JA, Lang.KO),
        ),
        AsrModel("whisper-tiny", "sherpa-onnx-whisper-tiny", AsrKind.WHISPER, all5),
        AsrModel("whisper-base", "sherpa-onnx-whisper-base", AsrKind.WHISPER, all5),
        AsrModel("whisper-small", "sherpa-onnx-whisper-small", AsrKind.WHISPER, all5),
    )

    fun byId(id: String) = models.first { it.id == id }

    /** Preferred installed model for a language: specialised models first, Whisper last. */
    fun defaultFor(lang: Lang, paths: SpikePaths): AsrModel? =
        models.filter { lang in it.langs && it.isPresent(paths) }.minByOrNull { if (it.kind == AsrKind.WHISPER) 1 else 0 }
}

/** One loaded sherpa-onnx offline recognizer. Not thread-safe; callers serialise access. */
class SherpaAsr(val model: AsrModel, paths: SpikePaths, val numThreads: Int) : AutoCloseable {
    private val folder = model.folder(paths)
    private var currentLang: Lang = model.langs.first()
    private val recognizer: OfflineRecognizer
    val loadMs: Long

    init {
        require(folder.isDirectory) { "Chưa có mô hình: ${folder.path}" }
        val t0 = System.nanoTime()
        recognizer = OfflineRecognizer(assetManager = null, config = config(currentLang))
        loadMs = (System.nanoTime() - t0) / 1_000_000
    }

    fun transcribe(samples: FloatArray, lang: Lang): String {
        require(lang in model.langs) { "${model.id} không hỗ trợ ${lang.code}" }
        if (lang != currentLang && model.kind != AsrKind.TRANSDUCER) {
            recognizer.setConfig(config(lang))
            currentLang = lang
        }
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            return AsrText.restore(clean(recognizer.getResult(stream).text), lang)
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()

    private fun config(lang: Lang): OfflineRecognizerConfig {
        val tokens = pick { it.endsWith("tokens.txt") }
        val modelConfig = when (model.kind) {
            AsrKind.TRANSDUCER -> OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = pickOnnx("encoder"),
                    decoder = pickOnnx("decoder"),
                    joiner = pickOnnx("joiner"),
                ),
                modelType = "transducer",
            )
            AsrKind.SENSE_VOICE -> OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = pickOnnx("model"),
                    language = lang.code,
                    useInverseTextNormalization = true,
                ),
            )
            AsrKind.WHISPER -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = pickOnnx("encoder"),
                    decoder = pickOnnx("decoder"),
                    language = lang.code,
                    task = "transcribe",
                ),
            )
        }
        modelConfig.tokens = tokens
        modelConfig.numThreads = numThreads
        return OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = modelConfig,
        )
    }

    /** Prefer the int8 variant of `<role>*.onnx` when an archive ships both. */
    private fun pickOnnx(role: String): String {
        val candidates = folder.listFiles().orEmpty().filter { it.name.endsWith(".onnx") && role in it.name }
        val chosen = candidates.firstOrNull { ".int8." in it.name } ?: candidates.firstOrNull()
        return requireNotNull(chosen) { "Không tìm thấy *$role*.onnx trong ${folder.name}" }.absolutePath
    }

    private fun pick(match: (String) -> Boolean): String =
        requireNotNull(folder.listFiles().orEmpty().firstOrNull { match(it.name) }) { "Thiếu tokens.txt trong ${folder.name}" }.absolutePath

    /** SenseVoice may emit tags such as <|zh|><|NEUTRAL|>; Whisper may add leading spaces. */
    private fun clean(text: String) = text.replace(Regex("<\\|[^|]*\\|>"), "").trim()
}

object VadFactory {
    /** End of utterance after [minSilence] seconds of silence. */
    fun create(paths: SpikePaths, minSilence: Float = 0.6f): Vad {
        require(paths.vadModel.isFile) { "Chưa có ${paths.vadModel.path}" }
        val config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = paths.vadModel.absolutePath,
                threshold = 0.5f,
                minSilenceDuration = minSilence,
                minSpeechDuration = 0.25f,
                windowSize = 512,
                maxSpeechDuration = 20f,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
        )
        return Vad(assetManager = null, config = config)
    }
}
