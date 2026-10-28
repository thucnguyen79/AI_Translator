package io.github.thucnguyen79.aitranslator.core.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.SpeechSynthesizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The phone's TextToSpeech engine, preferring installed offline voices. */
class SystemTts(context: Context) : SpeechSynthesizer, AutoCloseable {
    private val ready = CompletableDeferred<Boolean>()
    private val done = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                done.remove(utteranceId)?.complete(Unit)
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                done.remove(utteranceId)?.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                done.remove(utteranceId)?.complete(Unit)
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                done.remove(utteranceId)?.complete(Unit)
            }
        })
    }

    /** True when the phone has an installed voice for [lang] that works without network. */
    suspend fun hasOfflineVoice(lang: Lang): Boolean {
        if (!awaitReady()) return false
        return tts.isLanguageAvailable(lang.locale) >= TextToSpeech.LANG_AVAILABLE && offlineVoice(lang) != null
    }

    override suspend fun speak(text: String, lang: Lang) {
        if (!awaitReady()) return
        val voice = offlineVoice(lang)
        if (voice != null) tts.voice = voice else tts.language = lang.locale
        val id = UUID.randomUUID().toString()
        val finished = CompletableDeferred<Unit>().also { done[id] = it }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        withTimeout(60_000) { finished.await() }
    }

    override fun stop() {
        tts.stop()
    }

    override fun close() {
        tts.stop()
        tts.shutdown()
    }

    private suspend fun awaitReady(): Boolean = withTimeout(10_000) { ready.await() }

    private fun offlineVoice(lang: Lang): Voice? = tts.voices.orEmpty()
        .filter { it.locale.language == lang.locale.language && isOffline(it) }
        .minByOrNull { if (it.locale.country == lang.locale.country) 0 else 1 }

    private fun isOffline(v: Voice) =
        !v.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features.orEmpty()
}
