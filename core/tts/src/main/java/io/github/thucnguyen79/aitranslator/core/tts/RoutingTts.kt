package io.github.thucnguyen79.aitranslator.core.tts

import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.SpeechSynthesizer
import java.util.concurrent.ConcurrentHashMap

enum class TtsRoute(val offline: Boolean) {
    /** An installed offline voice of the phone's TTS engine. */
    SYSTEM(true),
    SUPERTONIC(true),
    /** The phone's engine without an offline voice: needs network (or may not speak at all). */
    SYSTEM_ONLINE(false),
}

/** Offline system voice first, then Supertonic when installed, else the system engine as a last resort. */
class RoutingTts(
    private val system: SystemTts,
    private val supertonic: () -> SupertonicTts?,
) : SpeechSynthesizer {
    private val offlineCache = ConcurrentHashMap<Lang, Boolean>()
    @Volatile private var current: SpeechSynthesizer? = null

    /** Speaking rate for every voice, 1.0 = normal (F5.3). */
    @Volatile var rate = 1f

    /** Chosen voice per language: a system voice name or [SUPERTONIC_VOICE]; absent = automatic. */
    @Volatile var choice: Map<Lang, String> = emptyMap()
        set(value) {
            field = value
            system.preferred = value.filterValues { it != SUPERTONIC_VOICE }
        }

    /** Voices that can read [lang] without network: system voice names, then [SUPERTONIC_VOICE] if installed. */
    suspend fun voiceOptions(lang: Lang): List<String> =
        system.offlineVoiceNames(lang) + listOfNotNull(SUPERTONIC_VOICE.takeIf { lang in SupertonicTts.LANGS && supertonic() != null })

    suspend fun route(lang: Lang): TtsRoute {
        val offline = offlineCache.getOrPut(lang) { system.hasOfflineVoice(lang) }
        val supertonicReady = lang in SupertonicTts.LANGS && supertonic() != null
        return when {
            choice[lang] == SUPERTONIC_VOICE && supertonicReady -> TtsRoute.SUPERTONIC
            offline -> TtsRoute.SYSTEM
            supertonicReady -> TtsRoute.SUPERTONIC
            else -> TtsRoute.SYSTEM_ONLINE
        }
    }

    /** Call after the user installs voice data or a TTS pack. */
    fun invalidate() = offlineCache.clear()

    override suspend fun speak(text: String, lang: Lang) {
        val engine: SpeechSynthesizer = when (route(lang)) {
            TtsRoute.SUPERTONIC -> supertonic() ?: system
            else -> system
        }
        system.rate = rate
        (engine as? SupertonicTts)?.speed = rate
        current = engine
        try {
            engine.speak(text, lang)
        } finally {
            current = null
        }
    }

    override fun stop() {
        current?.stop()
        system.stop()
    }
}

/** [RoutingTts.choice] value that selects the Supertonic voice. */
const val SUPERTONIC_VOICE = "supertonic"
