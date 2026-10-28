package io.github.thucnguyen79.aitranslator.spike.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.Vad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

const val SAMPLE_RATE = 16_000
private const val WINDOW = 512 // Silero VAD window at 16 kHz

class Recording(
    /** Everything captured between start and stop. */
    val all: FloatArray,
    /** The utterance cut out by the VAD, or [all] when no VAD was used / nothing was detected. */
    val speech: FloatArray,
    /** System.nanoTime() when the end of speech was detected (VAD) or recording stopped (manual). */
    val endNanos: Long,
    val endedByVad: Boolean,
) {
    val seconds: Double get() = speech.size / SAMPLE_RATE.toDouble()
}

/** 16 kHz mono capture. With a [Vad], returns as soon as the first utterance ends. */
object AudioCapture {
    @SuppressLint("MissingPermission") // callers request RECORD_AUDIO first
    suspend fun record(
        vad: Vad?,
        shouldStop: () -> Boolean,
        maxSeconds: Double = 20.0,
        onLevel: (Float) -> Unit = {},
    ): Recording = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, SAMPLE_RATE),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Không mở được micro" }

        val all = FloatBuffer()
        val pcm = ShortArray(WINDOW)
        var speech: FloatArray? = null
        val maxSamples = (maxSeconds * SAMPLE_RATE).toInt()
        vad?.reset()
        recorder.startRecording()
        try {
            while (currentCoroutineContext().isActive && !shouldStop() && all.size < maxSamples) {
                val n = recorder.read(pcm, 0, WINDOW)
                if (n <= 0) continue
                val chunk = FloatArray(n) { pcm[it] / 32768f }
                all.add(chunk)
                onLevel(rms(chunk))
                if (vad != null) {
                    vad.acceptWaveform(chunk)
                    if (!vad.empty()) {
                        speech = vad.front().samples
                        vad.pop()
                        break
                    }
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        val end = System.nanoTime()
        val endedByVad = speech != null
        if (speech == null && vad != null) {
            // Manual stop while still talking: keep whatever the VAD has buffered.
            vad.flush()
            val parts = FloatBuffer()
            while (!vad.empty()) {
                parts.add(vad.front().samples)
                vad.pop()
            }
            if (parts.size > 0) speech = parts.toArray()
        }
        val captured = all.toArray()
        Recording(captured, speech ?: captured, end, endedByVad)
    }

    private fun rms(x: FloatArray): Float {
        var s = 0.0
        for (v in x) s += v * v
        return sqrt(s / x.size).toFloat()
    }
}

class FloatBuffer {
    private var data = FloatArray(SAMPLE_RATE * 4)
    var size = 0
        private set

    fun add(x: FloatArray) {
        if (size + x.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + x.size))
        x.copyInto(data, size)
        size += x.size
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
