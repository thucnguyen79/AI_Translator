package io.github.thucnguyen79.aitranslator.core.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.SpeechSynthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Supertonic 3 (Supertone, OpenRAIL-M) through sherpa-onnx: one ~130 MB model for 31 languages, including
 * vi, en, ko and ja but not zh. Used when the phone has no offline system voice for a language.
 */
class SupertonicTts(dir: File, threads: Int = 2) : SpeechSynthesizer, AutoCloseable {
    private val tts: OfflineTts
    @Volatile private var stopRequested = false

    init {
        fun f(name: String) = File(dir, name).absolutePath
        tts = OfflineTts(
            assetManager = null,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    supertonic = OfflineTtsSupertonicModelConfig(
                        durationPredictor = f("duration_predictor.int8.onnx"),
                        textEncoder = f("text_encoder.int8.onnx"),
                        vectorEstimator = f("vector_estimator.int8.onnx"),
                        vocoder = f("vocoder.int8.onnx"),
                        ttsJson = f("tts.json"),
                        unicodeIndexer = f("unicode_indexer.bin"),
                        voiceStyle = f("voice.bin"),
                    ),
                    numThreads = threads,
                ),
            ),
        )
    }

    override suspend fun speak(text: String, lang: Lang): Unit = withContext(Dispatchers.Default) {
        require(lang in LANGS) { "Supertonic does not support ${lang.code}" }
        stopRequested = false
        val rate = tts.sampleRate()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            val config = GenerationConfig(sid = 0, speed = 1.0f, numSteps = 5, extra = mapOf("lang" to lang.code))
            tts.generateWithConfigAndCallback(text, config, Sink(track))
            Unit
        } finally {
            track.stop()
            track.release()
        }
    }

    override fun stop() {
        stopRequested = true
    }

    override fun close() = tts.release()

    /**
     * Audio callback. sherpa-onnx's JNI looks up `invoke([F)Ljava/lang/Integer;` on the callback object;
     * Kotlin 2 compiles lambdas with invokedynamic (only a generic `invoke(Object)`), so it must be a class.
     */
    private inner class Sink(private val track: AudioTrack) : (FloatArray) -> Int {
        override fun invoke(samples: FloatArray): Int {
            if (!stopRequested) track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            return if (stopRequested) 0 else 1
        }
    }

    companion object {
        val LANGS = setOf(Lang.VI, Lang.EN, Lang.KO, Lang.JA)
    }
}
