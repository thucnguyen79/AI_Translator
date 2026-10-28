package io.github.thucnguyen79.aitranslator.core.model

/** One utterance cut out of the microphone stream. */
class Utterance(
    val samples: FloatArray,
    val sampleRate: Int,
    /** System.nanoTime() when the end of speech was detected (or capture stopped). */
    val endNanos: Long,
) {
    val seconds: Double get() = samples.size / sampleRate.toDouble()
}

/** Microphone capture that returns after one utterance, or null when stopped before any speech. */
interface AudioInput {
    suspend fun capture(shouldStop: () -> Boolean): Utterance?
}

interface SpeechRecognizer {
    suspend fun transcribe(utterance: Utterance, lang: Lang): String
}

interface Translator {
    suspend fun translate(text: String, from: Lang, to: Lang): String
}

interface SpeechSynthesizer {
    /** Speaks [text] and returns when playback has finished (or was stopped). */
    suspend fun speak(text: String, lang: Lang)

    fun stop()
}
