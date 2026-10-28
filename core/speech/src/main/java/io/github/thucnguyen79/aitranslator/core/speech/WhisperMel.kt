package io.github.thucnguyen79.aitranslator.core.speech

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Whisper's input features: 80 log-mel bands × 3000 frames (30 s at 16 kHz, zero-padded), as in OpenAI's
 * `log_mel_spectrogram` (librosa Slaney mel filters, periodic Hann window, centred reflect-padded STFT).
 * Frames past the end of the audio are silence and are filled in without computing their spectrum.
 */
object WhisperMel {
    const val N_MELS = 80
    const val FRAMES = 3000
    private const val N_FFT = 400
    private const val HOP = 160
    private const val BINS = N_FFT / 2 + 1
    private const val N_SAMPLES = FRAMES * HOP

    private val window = FloatArray(N_FFT) { (0.5 - 0.5 * cos(2 * PI * it / N_FFT)).toFloat() }
    private val cosTable = FloatArray(BINS * N_FFT) { cos(2 * PI * (it / N_FFT) * (it % N_FFT) / N_FFT).toFloat() }
    private val sinTable = FloatArray(BINS * N_FFT) { sin(2 * PI * (it / N_FFT) * (it % N_FFT) / N_FFT).toFloat() }
    private val filters: Array<FloatArray> = melFilters()

    /** Features for [samples] (16 kHz mono), row-major [N_MELS][FRAMES] as the encoder's (1, 80, 3000) input. */
    fun compute(samples: FloatArray): FloatArray {
        val audio = if (samples.size > N_SAMPLES) samples.copyOf(N_SAMPLES) else samples
        val out = FloatArray(N_MELS * FRAMES)
        val frame = FloatArray(N_FFT)
        val power = FloatArray(BINS)
        // Frames that only see the zero padding have no energy: log10 of the floor.
        val voiced = min(FRAMES, (audio.size + N_FFT / 2) / HOP + 1)
        for (t in 0 until FRAMES) {
            if (t < voiced) {
                for (n in 0 until N_FFT) frame[n] = sample(audio, t * HOP - N_FFT / 2 + n) * window[n]
                for (k in 0 until BINS) {
                    var re = 0f
                    var im = 0f
                    val row = k * N_FFT
                    for (n in 0 until N_FFT) {
                        re += frame[n] * cosTable[row + n]
                        im -= frame[n] * sinTable[row + n]
                    }
                    power[k] = re * re + im * im
                }
                for (m in 0 until N_MELS) {
                    var e = 0f
                    val f = filters[m]
                    for (k in 0 until BINS) e += f[k] * power[k]
                    out[m * FRAMES + t] = log10(max(e, 1e-10f))
                }
            } else {
                for (m in 0 until N_MELS) out[m * FRAMES + t] = -10f
            }
        }
        val floor = out.max() - 8f
        for (i in out.indices) out[i] = (max(out[i], floor) + 4f) / 4f
        return out
    }

    /** The padded signal at [i]: reflected at both ends of the 30 s window, zero after the audio. */
    private fun sample(audio: FloatArray, i: Int): Float {
        var j = i
        if (j < 0) j = -j
        if (j >= N_SAMPLES) j = 2 * N_SAMPLES - 2 - j
        return if (j < audio.size) audio[j] else 0f
    }

    /** librosa.filters.mel(sr=16000, n_fft=400, n_mels=80): Slaney mel scale and area normalisation. */
    private fun melFilters(): Array<FloatArray> {
        fun hzToMel(f: Double): Double = if (f < 1000.0) f / (200.0 / 3) else 15.0 + ln(f / 1000.0) / (ln(6.4) / 27)
        fun melToHz(m: Double): Double = if (m < 15.0) m * (200.0 / 3) else 1000.0 * exp((m - 15.0) * (ln(6.4) / 27))
        val fftFreqs = DoubleArray(BINS) { it * 8000.0 / (BINS - 1) }
        val maxMel = hzToMel(8000.0)
        val melF = DoubleArray(N_MELS + 2) { melToHz(maxMel * it / (N_MELS + 1)) }
        return Array(N_MELS) { m ->
            val lower = melF[m]
            val center = melF[m + 1]
            val upper = melF[m + 2]
            val norm = 2.0 / (upper - lower)
            FloatArray(BINS) { k ->
                val f = fftFreqs[k]
                (max(0.0, min((f - lower) / (center - lower), (upper - f) / (upper - center))) * norm).toFloat()
            }
        }
    }
}
