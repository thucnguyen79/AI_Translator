package io.github.thucnguyen79.aitranslator.core.speech

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.SpeechRecognizer
import io.github.thucnguyen79.aitranslator.core.model.Utterance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class AsrKind { TRANSDUCER, SENSE_VOICE, WHISPER }

/** An installed sherpa-onnx offline ASR model: [dir] holds the extracted release archive. */
data class AsrModel(val kind: AsrKind, val dir: File, val langs: Set<Lang>)

/**
 * One loaded sherpa-onnx offline recognizer. Vietnamese output (all caps, no punctuation) is restored by
 * [punctuator] when given, else by [AsrText] rules.
 */
class SherpaRecognizer(
    private val model: AsrModel,
    threads: Int = 2,
    private val punctuator: ViPunctuator? = null,
) : SpeechRecognizer, AutoCloseable {
    private val mutex = Mutex()
    private val threads = threads
    private var currentLang = model.langs.first()
    private val recognizer = OfflineRecognizer(assetManager = null, config = config(currentLang))

    override suspend fun transcribe(utterance: Utterance, lang: Lang): String = withContext(Dispatchers.Default) {
        require(lang in model.langs) { "${model.dir.name} does not support ${lang.code}" }
        mutex.withLock {
            if (lang != currentLang && model.kind != AsrKind.TRANSDUCER) {
                recognizer.setConfig(config(lang))
                currentLang = lang
            }
            val stream = recognizer.createStream()
            val raw = try {
                stream.acceptWaveform(utterance.samples, utterance.sampleRate)
                recognizer.decode(stream)
                clean(recognizer.getResult(stream).text)
            } finally {
                stream.release()
            }
            AsrText.restore(raw, lang, punctuator?.let { p -> { s: String -> p.restore(s) } })
        }
    }

    override fun close() = recognizer.release()

    private fun config(lang: Lang): OfflineRecognizerConfig {
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
                senseVoice = OfflineSenseVoiceModelConfig(model = pickOnnx("model"), language = lang.code, useInverseTextNormalization = true),
            )
            AsrKind.WHISPER -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(encoder = pickOnnx("encoder"), decoder = pickOnnx("decoder"), language = lang.code),
            )
        }
        modelConfig.tokens = requireNotNull(model.dir.listFiles().orEmpty().firstOrNull { it.name.endsWith("tokens.txt") }) {
            "tokens.txt missing in ${model.dir}"
        }.absolutePath
        modelConfig.numThreads = threads
        return OfflineRecognizerConfig(featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80), modelConfig = modelConfig)
    }

    /** Prefers the int8 variant of `<role>*.onnx` when an archive ships both. */
    private fun pickOnnx(role: String): String {
        val candidates = model.dir.listFiles().orEmpty().filter { it.name.endsWith(".onnx") && role in it.name }
        val chosen = candidates.firstOrNull { ".int8." in it.name } ?: candidates.firstOrNull()
        return requireNotNull(chosen) { "*$role*.onnx missing in ${model.dir}" }.absolutePath
    }

    /** SenseVoice may emit tags such as <|zh|><|NEUTRAL|>. */
    private fun clean(text: String) = text.replace(Regex("<\\|[^|]*\\|>"), "").trim()
}

/** Dispatches to the recognizer installed for each language. */
class LanguageRouter(private val byLang: Map<Lang, SpeechRecognizer>) : SpeechRecognizer {
    val languages: Set<Lang> get() = byLang.keys

    override suspend fun transcribe(utterance: Utterance, lang: Lang): String =
        requireNotNull(byLang[lang]) { "No speech model installed for ${lang.code}" }.transcribe(utterance, lang)
}
