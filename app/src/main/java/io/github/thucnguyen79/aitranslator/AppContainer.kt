package io.github.thucnguyen79.aitranslator

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.thucnguyen79.aitranslator.core.engine.ConversationEngine
import io.github.thucnguyen79.aitranslator.core.engine.EngineState
import io.github.thucnguyen79.aitranslator.core.engine.Seat
import io.github.thucnguyen79.aitranslator.core.engine.Turn
import io.github.thucnguyen79.aitranslator.core.model.AudioInput
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.LanguageIdentifier
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
import io.github.thucnguyen79.aitranslator.core.speech.WhisperLanguageId
import io.github.thucnguyen79.aitranslator.core.translate.BergamotTranslator
import io.github.thucnguyen79.aitranslator.core.tts.RoutingTts
import io.github.thucnguyen79.aitranslator.core.tts.SupertonicTts
import io.github.thucnguyen79.aitranslator.core.tts.SystemTts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Manual dependency wiring. Models load lazily on first use, once their pack is installed. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
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
    private val languageId = LazyLanguageId()

    val engine = ConversationEngine(
        audioInput, recognizer, translator, tts, scope,
        canTranslate = { from, to -> packs.isLanguageReady(from) && packs.isLanguageReady(to) },
    ).apply {
        mutedSeats = prefs.getStringSet("muted_seats", emptySet()).orEmpty().map(String::toInt).toSet()
        backTranslate = prefs.getBoolean("back_translate", false)
    }

    /** Face to face (phone between two people) or one list passed back and forth. */
    val layout = MutableStateFlow(
        runCatching { ConversationLayout.valueOf(prefs.getString("layout", null).orEmpty()) }.getOrDefault(ConversationLayout.FACE_TO_FACE),
    )

    /** People at the table in the "pass the phone" layout (2–4); face to face is always 2. */
    val seatCount = MutableStateFlow(prefs.getInt("seat_count", 2).coerceIn(2, 4))

    /** Each seat's language, also for seats not in use right now (remembered per seat). */
    private val savedLangs = MutableList(4) { savedLang("seat$it", DEFAULT_LANGS[it]) }

    /** The languages of the seats in use, in seat order. */
    val seatLangs = MutableStateFlow<List<Lang>>(emptyList())

    private fun applySeats() {
        val count = if (layout.value == ConversationLayout.FACE_TO_FACE) 2 else seatCount.value
        engine.seats = (0 until count).map { Seat(it, savedLangs[it]) }
        seatLangs.value = engine.seats.map { it.lang }
        preload()
    }

    fun setSeatCount(count: Int) {
        seatCount.value = count.coerceIn(2, 4)
        prefs.edit().putInt("seat_count", seatCount.value).apply()
        applySeats()
    }

    /** Optional short names per seat (F1.3); blank = shown by number. */
    val seatNames = MutableStateFlow(List(4) { prefs.getString("name$it", "").orEmpty() })

    fun setSeatName(seat: Int, name: String) {
        seatNames.update { names -> names.toMutableList().also { it[seat] = name.take(MAX_NAME) } }
        prefs.edit().putString("name$seat", name.take(MAX_NAME)).apply()
    }

    /** Hands-free mode (F2.6), see [ConversationEngine.setHandsFree]. */
    val handsFree get() = engine.handsFree

    fun setHandsFree(on: Boolean) {
        updateIdentifier()
        // Whisper takes a moment to load: start now rather than on the first utterance.
        if (on && engine.identifier != null) scope.launch { runCatching { languageId.prepare() } }
        engine.setHandsFree(on)
    }

    /** Uses spoken-language detection once one of its optional packs is installed. */
    private fun updateIdentifier() {
        engine.identifier = if (activeLidPack() != null) languageId else null
    }

    /** The language-detection model chosen in Language packs (F3.4). */
    val lidChoice = MutableStateFlow(prefs.getString("lid_pack", null))

    fun setLidChoice(packId: String) {
        lidChoice.value = packId
        prefs.edit().putString("lid_pack", packId).apply()
        updateIdentifier()
    }

    /** The chosen detection pack if installed, else an installed one (Base first): null when none is. */
    fun activeLidPack(): String? =
        lidChoice.value?.takeIf(packs::isInstalled) ?: listOf("lid-base", "lid-small", "lid-tiny").firstOrNull(packs::isInstalled)

    /** Chosen voice per language: a system voice name or SUPERTONIC_VOICE; absent = automatic (F5.3). */
    val voiceChoice = MutableStateFlow(
        Lang.entries.mapNotNull { lang -> prefs.getString("voice_${lang.code}", null)?.let { lang to it } }.toMap(),
    )

    fun setVoice(lang: Lang, voice: String?) {
        voiceChoice.update { if (voice == null) it - lang else it + (lang to voice) }
        tts.choice = voiceChoice.value
        prefs.edit().apply { if (voice == null) remove("voice_${lang.code}") else putString("voice_${lang.code}", voice) }.apply()
        tts.invalidate()
        voicesVersion.update { it + 1 }
    }

    /** Reads a greeting from the phrasebook with the current voice for [lang]. */
    fun previewVoice(lang: Lang, sample: String) {
        if (engine.state.value !is EngineState.Idle) return
        scope.launch { runCatching { tts.speak(sample, lang) } }
    }

    val history = HistoryStore(File(context.filesDir, "history"))

    /** Private mode: conversations are not saved (F9.5). */
    val privateMode = MutableStateFlow(prefs.getBoolean("private_mode", false))

    fun setPrivateMode(on: Boolean) {
        privateMode.value = on
        prefs.edit().putBoolean("private_mode", on).apply()
    }

    @Volatile private var sessionId = System.currentTimeMillis()

    private fun saveSession(id: Long, turns: List<Turn>) {
        if (turns.isEmpty() || privateMode.value) return
        runCatching { history.save(SavedSession(id, System.currentTimeMillis(), seatLangs.value, turns, seatNames.value)) }
    }

    /** Clears the screen and starts a new conversation; the old one stays in history (F9.3). */
    fun clearConversation() {
        val id = sessionId
        val turns = engine.turns.value
        sessionId = System.currentTimeMillis()
        engine.clear()
        scope.launch(Dispatchers.IO) { saveSession(id, turns) }
    }

    /** Check translations by translating them back into the speaker's language (F4.5). */
    val backTranslate = MutableStateFlow(engine.backTranslate)

    fun setBackTranslate(on: Boolean) {
        backTranslate.value = on
        engine.backTranslate = on
        prefs.edit().putBoolean("back_translate", on).apply()
    }

    /** Speaking rate of translations read aloud (F5.3). */
    val speechRate = MutableStateFlow(prefs.getFloat("speech_rate", 1f))

    fun setSpeechRate(rate: Float) {
        speechRate.value = rate
        tts.rate = rate
        prefs.edit().putFloat("speech_rate", rate).apply()
    }

    /** Text size multiplier on top of the system font size (F10.2). */
    val fontScale = MutableStateFlow(prefs.getFloat("font_scale", 1f))

    fun setFontScale(scale: Float) {
        fontScale.value = scale
        prefs.edit().putFloat("font_scale", scale).apply()
    }

    val theme = MutableStateFlow(runCatching { AppTheme.valueOf(prefs.getString("theme", null).orEmpty()) }.getOrDefault(AppTheme.SYSTEM))

    fun setTheme(value: AppTheme) {
        theme.value = value
        prefs.edit().putString("theme", value.name).apply()
    }

    /** Bumped when TTS voices may have changed (app resumed, Supertonic pack installed): UI re-checks routes. */
    val voicesVersion = MutableStateFlow(0)

    fun refreshVoices() {
        tts.invalidate()
        voicesVersion.update { it + 1 }
    }

    /** Seats whose translations are not read aloud (each half has its own speaker toggle). */
    val mutedSeats = MutableStateFlow(engine.mutedSeats)

    fun toggleMute(seat: Int) {
        val muted = if (seat in mutedSeats.value) mutedSeats.value - seat else mutedSeats.value + seat
        mutedSeats.value = muted
        engine.mutedSeats = muted
        prefs.edit().putStringSet("muted_seats", muted.map(Int::toString).toSet()).apply()
    }

    fun setLayout(value: ConversationLayout) {
        layout.value = value
        prefs.edit().putString("layout", value.name).apply()
        applySeats()
    }

    /** Installs [packId] (and what it needs) under a foreground service, so leaving the app does not stop it. */
    fun startDownloads(packId: String) {
        packs.install(packId)
        ContextCompat.startForegroundService(appContext, Intent(appContext, PackDownloadService::class.java))
    }

    /** Ask before downloading packs over mobile data. */
    val wifiOnly = MutableStateFlow(prefs.getBoolean("wifi_only", true))

    fun setWifiOnly(on: Boolean) {
        wifiOnly.value = on
        prefs.edit().putBoolean("wifi_only", on).apply()
    }

    /** Show per-turn timings in the conversation and a summary in the info tab. */
    val diagnostics = MutableStateFlow(prefs.getBoolean("diagnostics", false))

    /** True while models for the seats' languages are being loaded ahead of the first turn. */
    val preparing = MutableStateFlow(false)

    /** First load time of each component in ms (e.g. "ASR vi", "MT vi→en"), for diagnostics. */
    val loadTimes = MutableStateFlow<Map<String, Long>>(emptyMap())
    private var preloadJob: Job? = null

    fun setSeatLang(seat: Int, lang: Lang) {
        savedLangs[seat] = lang
        prefs.edit().putString("seat$seat", lang.code).apply()
        applySeats()
    }

    init {
        tts.rate = speechRate.value
        tts.choice = voiceChoice.value
        applySeats()
        scope.launch(Dispatchers.IO) { history.load() }
        // Autosave: the current conversation is written a moment after it last changed (new turn, late
        // translations, an edit). After clearConversation() the latest value is empty, so nothing stale is saved.
        @OptIn(FlowPreview::class)
        scope.launch(Dispatchers.IO) { engine.turns.debounce(1_000).collect { saveSession(sessionId, it) } }
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
        updateIdentifier()
        val langs = engine.seats.map { it.lang }.distinct()
        if (langs.size < 2 || !langs.all(packs::isLanguageReady)) return
        preloadJob?.cancel()
        preloadJob = scope.launch {
            preparing.value = true
            try {
                timed("VAD") { audioInput.prepare() }
                for (lang in langs) timed("ASR ${lang.code}") { recognizer.prepare(lang) }
                for (from in langs) for (to in langs) {
                    if (from != to) timed("MT ${from.code}→${to.code}") { translator.prepare(from, to) }
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

    /** Whisper language identification from the active detection pack, loaded on first use (and on a switch). */
    private inner class LazyLanguageId : LanguageIdentifier {
        private val mutex = Mutex()
        private var lid: WhisperLanguageId? = null
        private var loadedPack: String? = null

        suspend fun prepare(): WhisperLanguageId = mutex.withLock {
            val pack = checkNotNull(activeLidPack()) { "Language detection pack is not installed" }
            lid?.takeIf { loadedPack == pack } ?: run {
                lid?.close()
                lid = null
                val t0 = System.nanoTime()
                WhisperLanguageId(checkNotNull(models.lidDir(pack))).also {
                    lid = it
                    loadedPack = pack
                    loadTimes.update { times -> times + ("LID ${pack.removePrefix("lid-")}" to (System.nanoTime() - t0) / 1_000_000) }
                }
            }
        }

        override suspend fun identify(utterance: Utterance, candidates: Collection<Lang>, expected: Lang?): Lang? =
            prepare().identify(utterance, candidates, expected)
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

enum class ConversationLayout {
    /** Two people facing each other across the phone. */
    FACE_TO_FACE,
    /** One list, passed around (2–4 people). */
    LIST,
    /** 3–4 people around the phone, one area turned to each side (F6.2). */
    AROUND,
}

private const val MAX_NAME = 12

enum class AppTheme { SYSTEM, LIGHT, DARK }

private val DEFAULT_LANGS = listOf(Lang.VI, Lang.EN, Lang.ZH, Lang.KO)
