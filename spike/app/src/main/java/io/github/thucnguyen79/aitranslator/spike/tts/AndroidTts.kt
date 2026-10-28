package io.github.thucnguyen79.aitranslator.spike.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TtsProbe(
    val lang: Lang,
    val engine: String,
    /** Result of setLanguage / isLanguageAvailable: >= 0 means usable. */
    val availability: Int,
    val offlineVoices: List<String>,
    val networkVoices: List<String>,
) {
    val usableOffline get() = availability >= TextToSpeech.LANG_AVAILABLE && offlineVoices.isNotEmpty()
}

/** The system TextToSpeech engine; measures time from speak() to the first audio callback. */
class AndroidTts(context: Context) : AutoCloseable {
    private val ready = CompletableDeferred<Boolean>()
    private val started = ConcurrentHashMap<String, CompletableDeferred<Long>>()
    private val done = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                started[utteranceId]?.complete(System.nanoTime())
            }

            override fun onDone(utteranceId: String) {
                done[utteranceId]?.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                started[utteranceId]?.completeExceptionally(IllegalStateException("TTS lỗi"))
                done[utteranceId]?.complete(Unit)
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                started[utteranceId]?.completeExceptionally(IllegalStateException("TTS lỗi $errorCode"))
                done[utteranceId]?.complete(Unit)
            }
        })
    }

    suspend fun awaitReady(): Boolean = withTimeout(10_000) { ready.await() }

    suspend fun probe(lang: Lang): TtsProbe {
        awaitReady()
        val voices = tts.voices.orEmpty().filter { it.locale.language == lang.locale.language }
        val (offline, network) = voices.partition { isOffline(it) }
        return TtsProbe(
            lang = lang,
            engine = tts.defaultEngine ?: "?",
            availability = tts.isLanguageAvailable(lang.locale),
            offlineVoices = offline.map { it.name }.sorted(),
            networkVoices = network.map { it.name }.sorted(),
        )
    }

    /**
     * Speaks [text] and returns ms from the speak() call to the start of audio. Prefers an installed
     * offline voice so the measurement reflects the offline product.
     */
    suspend fun speak(text: String, lang: Lang, rate: Float = 1.0f, waitDone: Boolean = true): Long {
        awaitReady()
        val voice = tts.voices.orEmpty()
            .filter { it.locale.language == lang.locale.language && isOffline(it) }
            .minByOrNull { if (it.locale.country == lang.locale.country) 0 else 1 }
        if (voice != null) tts.voice = voice else tts.language = lang.locale
        tts.setSpeechRate(rate)

        val id = UUID.randomUUID().toString()
        val start = CompletableDeferred<Long>().also { started[id] = it }
        val end = CompletableDeferred<Unit>().also { done[id] = it }
        val t0 = System.nanoTime()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        try {
            val startedAt = withTimeout(15_000) { start.await() }
            if (waitDone) withTimeout(60_000) { end.await() }
            return (startedAt - t0) / 1_000_000
        } finally {
            started.remove(id)
            done.remove(id)
        }
    }

    fun stop() {
        tts.stop()
    }

    override fun close() {
        tts.stop()
        tts.shutdown()
    }

    private fun isOffline(v: Voice) =
        !v.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features.orEmpty()
}
