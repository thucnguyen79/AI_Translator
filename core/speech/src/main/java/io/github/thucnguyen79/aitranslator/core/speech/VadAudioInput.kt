package io.github.thucnguyen79.aitranslator.core.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import io.github.thucnguyen79.aitranslator.core.model.AudioInput
import io.github.thucnguyen79.aitranslator.core.model.Utterance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

const val SAMPLE_RATE = 16_000
private const val WINDOW = 512 // Silero VAD window at 16 kHz

/**
 * 16 kHz mono microphone capture with Silero VAD: returns as soon as the first utterance ends
 * ([minSilence] seconds of silence), or whatever was said when [AudioInput.capture]'s stop flag is raised.
 */
class VadAudioInput(
    vadModel: File,
    minSilence: Float = 0.6f,
    private val maxSeconds: Double = 20.0,
    /** RMS level of each captured window, for a level meter. */
    private val onLevel: (Float) -> Unit = {},
) : AudioInput, AutoCloseable {
    private val vad = Vad(
        assetManager = null,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = vadModel.absolutePath,
                threshold = 0.5f,
                minSilenceDuration = minSilence,
                minSpeechDuration = 0.25f,
                windowSize = WINDOW,
                maxSpeechDuration = maxSeconds.toFloat(),
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
        ),
    )

    @SuppressLint("MissingPermission") // the UI requests RECORD_AUDIO before capturing
    override suspend fun capture(shouldStop: () -> Boolean): Utterance? = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, SAMPLE_RATE),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }

        val pcm = ShortArray(WINDOW)
        var speech: FloatArray? = null
        var captured = 0
        val maxSamples = (maxSeconds * SAMPLE_RATE).toInt()
        vad.reset()
        recorder.startRecording()
        try {
            while (currentCoroutineContext().isActive && !shouldStop() && captured < maxSamples) {
                val n = recorder.read(pcm, 0, WINDOW)
                if (n <= 0) continue
                val chunk = FloatArray(n) { pcm[it] / 32768f }
                captured += n
                onLevel(rms(chunk))
                vad.acceptWaveform(chunk)
                if (!vad.empty()) {
                    speech = vad.front().samples
                    vad.pop()
                    break
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        val end = System.nanoTime()
        if (speech == null) {
            // Stopped while still talking: keep whatever the VAD has buffered.
            vad.flush()
            val parts = ArrayList<FloatArray>()
            while (!vad.empty()) {
                parts += vad.front().samples
                vad.pop()
            }
            if (parts.isNotEmpty()) speech = concat(parts)
        }
        speech?.let { Utterance(it, SAMPLE_RATE, end) }
    }

    override fun close() = vad.release()

    private fun rms(x: FloatArray): Float {
        var s = 0.0
        for (v in x) s += v * v
        return sqrt(s / x.size).toFloat()
    }

    private fun concat(parts: List<FloatArray>): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var pos = 0
        for (p in parts) {
            p.copyInto(out, pos)
            pos += p.size
        }
        return out
    }
}
