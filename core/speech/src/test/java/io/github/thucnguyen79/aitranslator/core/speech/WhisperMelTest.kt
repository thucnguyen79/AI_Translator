package io.github.thucnguyen79.aitranslator.core.speech

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class WhisperMelTest {
    /** 0.8 s of tones and a chirp; expected values from librosa (OpenAI Whisper's log_mel_spectrogram recipe). */
    private val signal = FloatArray(12_800) { i ->
        val t = i / 16_000.0
        (0.3 * sin(2 * PI * 440 * t) + 0.2 * sin(2 * PI * 1800 * t) * exp(-2 * t) + 0.05 * sin(2 * PI * 97 * t * t * 50)).toFloat()
    }

    @Test fun matchesLibrosa() {
        val mel = WhisperMel.compute(signal)
        assertEquals(WhisperMel.N_MELS * WhisperMel.FRAMES, mel.size)
        fun at(m: Int, t: Int) = mel[m * WhisperMel.FRAMES + t]
        listOf(
            Triple(0, 0, 0.93621f), Triple(5, 10, -0.65868f), Triple(10, 40, 1.23781f), Triple(20, 79, 0.05452f),
            Triple(40, 50, 0.73640f), Triple(60, 30, -0.65868f), Triple(30, 100, -0.65868f), Triple(10, 2999, -0.65868f),
        ).forEach { (m, t, expected) -> assertEquals("mel $m frame $t", expected, at(m, t), 2e-3f) }
        assertEquals(-0.651421f, mel.average().toFloat(), 1e-4f)
    }
}
