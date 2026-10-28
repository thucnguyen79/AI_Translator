package io.github.thucnguyen79.aitranslator

import android.content.Context
import io.github.thucnguyen79.aitranslator.core.engine.ConversationEngine
import io.github.thucnguyen79.aitranslator.core.engine.Seat
import io.github.thucnguyen79.aitranslator.core.model.AudioInput
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.SpeechRecognizer
import io.github.thucnguyen79.aitranslator.core.model.Utterance
import io.github.thucnguyen79.aitranslator.core.packs.ModelLocator
import io.github.thucnguyen79.aitranslator.core.packs.PackCatalog
import io.github.thucnguyen79.aitranslator.core.packs.PackManager
import io.github.thucnguyen79.aitranslator.core.speech.AsrKind
import io.github.thucnguyen79.aitranslator.core.speech.AsrModel
import io.github.thucnguyen79.aitranslator.core.speech.SherpaRecognizer
import io.github.thucnguyen79.aitranslator.core.speech.VadAudioInput
import io.github.thucnguyen79.aitranslator.core.speech.ViPunctuator
import io.github.thucnguyen79.aitranslator.core.translate.BergamotTranslator
import io.github.thucnguyen79.aitranslator.core.tts.RoutingTts
import io.github.thucnguyen79.aitranslator.core.tts.SupertonicTts
import io.github.thucnguyen79.aitranslator.core.tts.SystemTts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Manual dependency wiring. Models load lazily on first use, once their pack is installed. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val packs = PackManager(
        root = File(context.filesDir, "packs"),
        catalog = PackCatalog.parse(context.assets.open("packs.json").bufferedReader().use { it.readText() }),
        scope = scope,
    )
    val models = ModelLocator(packs)

    /** Microphone level 0..1 for the UI meter. */
    val micLevel = MutableStateFlow(0f)

    private val systemTts = SystemTts(context)
    private var supertonic: SupertonicTts? = null
    val tts = RoutingTts(systemTts) {
        models.supertonicDir?.let { dir -> supertonic ?: SupertonicTts(dir).also { supertonic = it } }
    }

    private val translator = BergamotTranslator(models::bergamotDir)

    val engine = ConversationEngine(LazyAudioInput(), LazyRecognizer(), translator, tts, scope).apply {
        seats = listOf(Seat(0, savedLang("seat0", Lang.VI)), Seat(1, savedLang("seat1", Lang.EN)))
    }

    fun setSeatLang(seat: Int, lang: Lang) {
        engine.seats = engine.seats.map { if (it.index == seat) it.copy(lang = lang) else it }
        prefs.edit().putString("seat$seat", lang.code).apply()
    }

    private fun savedLang(key: String, default: Lang) = prefs.getString(key, null)?.let(Lang::of) ?: default

    /** The VAD model comes with the "core" pack, so the microphone is only set up when first needed. */
    private inner class LazyAudioInput : AudioInput {
        private var input: VadAudioInput? = null

        override suspend fun capture(shouldStop: () -> Boolean): Utterance? {
            val vad = input ?: VadAudioInput(models.vadModel, onLevel = { micLevel.value = (it * 8).coerceAtMost(1f) })
                .also { input = it }
            return try {
                vad.capture(shouldStop)
            } finally {
                micLevel.value = 0f
            }
        }
    }

    /** One sherpa-onnx recognizer per installed model, created on first use. */
    private inner class LazyRecognizer : SpeechRecognizer {
        private val mutex = Mutex()
        private val loaded = HashMap<File, SherpaRecognizer>()

        override suspend fun transcribe(utterance: Utterance, lang: Lang): String {
            val dir = models.asrDir(lang)
            val recognizer = mutex.withLock {
                loaded.getOrPut(dir) {
                    check(packs.isLanguageReady(lang)) { "Language pack ${lang.code} is not installed" }
                    if (lang == Lang.VI) {
                        val punct = models.viPunctuationDir.takeIf(ViPunctuator::isPresent)?.let { ViPunctuator(it) }
                        SherpaRecognizer(AsrModel(AsrKind.TRANSDUCER, dir, setOf(Lang.VI)), punctuator = punct)
                    } else {
                        SherpaRecognizer(AsrModel(AsrKind.SENSE_VOICE, dir, setOf(Lang.EN, Lang.ZH, Lang.KO, Lang.JA)))
                    }
                }
            }
            return recognizer.transcribe(utterance, lang)
        }
    }
}
