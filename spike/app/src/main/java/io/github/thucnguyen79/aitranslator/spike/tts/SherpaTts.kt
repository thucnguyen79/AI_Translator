package io.github.thucnguyen79.aitranslator.spike.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.core.SpikePaths
import java.io.File

/**
 * Supertonic 3 (Supertone, OpenRAIL-M) through sherpa-onnx: one ~130 MB model for 31 languages,
 * including vi, en, ko and ja but not zh. Used when the phone has no offline system voice.
 */
class SherpaTts(paths: SpikePaths, threads: Int = 2) : AutoCloseable {
    private val tts: OfflineTts
    val loadMs: Long
    @Volatile private var stopRequested = false

    init {
        val dir = folder(paths)
        require(dir.isDirectory) { "Chưa có mô hình TTS: ${dir.path}" }
        fun f(name: String) = File(dir, name).absolutePath
        val config = OfflineTtsConfig(
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
        )
        val t0 = System.nanoTime()
        tts = OfflineTts(assetManager = null, config = config)
        loadMs = (System.nanoTime() - t0) / 1_000_000
    }

    /** Synthesises and plays [text]; returns ms from the call to the first audio chunk. */
    fun speak(text: String, lang: Lang, speed: Float = 1.0f, voice: Int = 0): Long {
        require(lang in LANGS) { "Supertonic không hỗ trợ ${lang.code}" }
        stopRequested = false
        val rate = tts.sampleRate()
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val sink = Sink(track, System.nanoTime())
        try {
            track.play()
            val config = GenerationConfig(sid = voice, speed = speed, numSteps = 5, extra = mapOf("lang" to lang.code))
            tts.generateWithConfigAndCallback(text, config, sink)
        } finally {
            track.stop()
            track.release()
        }
        return sink.firstChunkMs
    }

    /**
     * Audio callback. sherpa-onnx's JNI looks up `invoke([F)Ljava/lang/Integer;` on the callback object;
     * Kotlin 2 compiles lambdas with invokedynamic (only a generic `invoke(Object)`), so it must be a class.
     */
    private inner class Sink(private val track: AudioTrack, private val t0: Long) : (FloatArray) -> Int {
        var firstChunkMs = -1L

        override fun invoke(samples: FloatArray): Int {
            if (firstChunkMs < 0) firstChunkMs = (System.nanoTime() - t0) / 1_000_000
            if (!stopRequested) track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            return if (stopRequested) 0 else 1
        }
    }

    fun stop() {
        stopRequested = true
    }

    override fun close() = tts.release()

    companion object {
        const val DIR = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
        val LANGS = setOf(Lang.VI, Lang.EN, Lang.KO, Lang.JA)
        fun folder(paths: SpikePaths) = File(paths.ttsModels, DIR)
        fun isPresent(paths: SpikePaths) = folder(paths).isDirectory
    }
}
