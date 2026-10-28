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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val audioInput = LazyAudioInput()
    private val recognizer = LazyRecognizer()

    val engine = ConversationEngine(
        audioInput, recognizer, translator, tts, scope,
        canTranslate = { from, to -> packs.isLanguageReady(from) && packs.isLanguageReady(to) },
    ).apply {
        seats = listOf(Seat(0, savedLang("seat0", Lang.VI)), Seat(1, savedLang("seat1", Lang.EN)))
    }

    /** Show per-turn timings in the conversation and a summary in the info tab. */
    val diagnostics = MutableStateFlow(prefs.getBoolean("diagnostics", false))

    /** True while models for the seats' languages are being loaded ahead of the first turn. */
    val preparing = MutableStateFlow(false)

    /** First load time of each component in ms (e.g. "ASR vi", "Dịch vi→en"), for diagnostics. */
    val loadTimes = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var preloadJob: Job? = null

    fun setSeatLang(seat: Int, lang: Lang) {
        engine.seats = engine.seats.map { if (it.index == seat) it.copy(lang = lang) else it }
        prefs.edit().putString("seat$seat", lang.code).apply()
        preload()
    }

    fun setDiagnostics(on: Boolean) {
        diagnostics.value = on
        prefs.edit().putBoolean("diagnostics", on).apply()
    }

    /**
     * Loads the microphone VAD, recognizers and translation models the current seats need, so the first
     * turn does not wait 1–2 s for them. Safe to call repeatedly; already loaded models are skipped.
     */
    fun preload() {
        val langs = engine.seats.map { it.lang }.distinct()
        if (langs.size < 2 || !langs.all(packs::isLanguageReady)) return
        preloadJob?.cancel()
        preloadJob = scope.launch {
            preparing.value = true
            try {
                timed("Micro (VAD)") { audioInput.prepare() }
                for (lang in langs) timed("Nhận dạng ${lang.code}") { recognizer.prepare(lang) }
                for (from in langs) for (to in langs) {
                    if (from != to) timed("Dịch ${from.code}→${to.code}") { translator.prepare(from, to) }
                }
            } catch (e: Exception) {
                // A failed preload only costs the first turn its speed; that turn reports the error.
            } finally {
                preparing.value = false
            }
        }
    }

    private suspend fun timed(name: String, block: suspend () -> Boolean) {
        val t0 = System.nanoTime()
        val loadedNow = block()
        if (loadedNow) loadTimes.update { it + (name to (System.nanoTime() - t0) / 1_000_000) }
    }

    private fun savedLang(key: String, default: Lang) = prefs.getString(key, null)?.let(Lang::of) ?: default

    /** The VAD model comes with the "core" pack, so the microphone is only set up when first needed. */
    private inner class LazyAudioInput : AudioInput {
        private var input: VadAudioInput? = null

        /** Returns true when the VAD was loaded by this call. */
        fun prepare(): Boolean {
            if (input != null) return false
            input = VadAudioInput(models.vadModel, onLevel = { micLevel.value = (it * 8).coerceAtMost(1f) })
            return true
        }

        override suspend fun capture(shouldStop: () -> Boolean): Utterance? {
            prepare()
            return try {
                input!!.capture(shouldStop)
            } finally {
                micLevel.value = 0f
            }
        }
    }

    /** One sherpa-onnx recognizer per installed model, created on first use (or by [preload]). */
    private inner class LazyRecognizer : SpeechRecognizer {
        private val mutex = Mutex()
        private val loaded = HashMap<File, SherpaRecognizer>()

        /** Returns true when the model was loaded by this call. */
        suspend fun prepare(lang: Lang): Boolean = mutex.withLock {
            val dir = models.asrDir(lang)
            if (dir in loaded) return@withLock false
            check(packs.isLanguageReady(lang)) { "Language pack ${lang.code} is not installed" }
            loaded[dir] = if (lang == Lang.VI) {
                val punct = models.viPunctuationDir.takeIf(ViPunctuator::isPresent)?.let { ViPunctuator(it) }
                SherpaRecognizer(AsrModel(AsrKind.TRANSDUCER, dir, setOf(Lang.VI)), punctuator = punct)
            } else {
                SherpaRecognizer(AsrModel(AsrKind.SENSE_VOICE, dir, setOf(Lang.EN, Lang.ZH, Lang.KO, Lang.JA)))
            }
            true
        }

        override suspend fun transcribe(utterance: Utterance, lang: Lang): String {
            prepare(lang)
            val recognizer = mutex.withLock { loaded.getValue(models.asrDir(lang)) }
            return recognizer.transcribe(utterance, lang)
        }
    }
}
