package io.github.thucnguyen79.aitranslator.spike

import com.k2fsa.sherpa.onnx.Vad
import io.github.thucnguyen79.aitranslator.spike.asr.AsrModel
import io.github.thucnguyen79.aitranslator.spike.asr.SherpaAsr
import io.github.thucnguyen79.aitranslator.spike.asr.VadFactory
import io.github.thucnguyen79.aitranslator.spike.asr.ViPunctuator
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngine
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngineId
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngines
import io.github.thucnguyen79.aitranslator.spike.tts.AndroidTts
import io.github.thucnguyen79.aitranslator.spike.tts.SherpaTts
import kotlinx.coroutines.sync.Mutex

/**
 * Models kept loaded for the interactive tabs (quick tests and the conversation demo), so each turn
 * measures inference rather than loading. Benchmarks load their own instances.
 */
object Engines {
    /** Native engines are not thread-safe: interactive work holds this lock. */
    val lock = Mutex()

    private val asr = HashMap<String, SherpaAsr>()
    private var vad: Vad? = null
    private var mt: MtEngine? = null
    private var mtId: MtEngineId? = null
    private val preparedDirections = HashSet<Pair<Lang, Lang>>()
    val tts: AndroidTts by lazy { AndroidTts(SpikeApp.instance) }
    private var supertonic: SherpaTts? = null
    private val androidOffline = HashMap<Lang, Boolean>()

    fun supertonic(): SherpaTts = supertonic ?: SherpaTts(SpikeApp.paths).also { supertonic = it }

    /** Offline system voice first, then Supertonic, else the system engine even if it needs network. */
    suspend fun ttsRoute(lang: Lang): TtsRoute {
        val offline = androidOffline.getOrPut(lang) { tts.probe(lang).usableOffline }
        return when {
            offline -> TtsRoute.ANDROID
            lang in SherpaTts.LANGS && SherpaTts.isPresent(SpikeApp.paths) -> TtsRoute.SUPERTONIC
            else -> TtsRoute.ANDROID_ONLINE
        }
    }

    /** Speaks via [ttsRoute]; returns the route and ms until audio starts. Supertonic blocks until done. */
    suspend fun speak(text: String, lang: Lang): Pair<TtsRoute, Long> {
        val route = ttsRoute(lang)
        val ms = when (route) {
            TtsRoute.SUPERTONIC -> supertonic().speak(text, lang)
            else -> tts.speak(text, lang, waitDone = false)
        }
        return route to ms
    }

    fun stopSpeaking() {
        tts.stop()
        supertonic?.stop()
    }

    /** Use the vibert-capu model (when installed) instead of rules to restore Vietnamese punctuation. */
    var usePunctModel = true
    private var punctuator: ViPunctuator? = null

    fun asr(model: AsrModel, threads: Int = 2): SherpaAsr {
        val cached = asr[model.id]
        val engine = if (cached != null && cached.numThreads == threads) {
            cached
        } else {
            cached?.close()
            SherpaAsr(model, SpikeApp.paths, threads).also { asr[model.id] = it }
        }
        engine.punctuator = if (usePunctModel && Lang.VI in model.langs) punctuator() else null
        return engine
    }

    private fun punctuator(): ViPunctuator? {
        if (punctuator == null && ViPunctuator.isPresent(SpikeApp.paths)) {
            punctuator = ViPunctuator(ViPunctuator.folder(SpikeApp.paths))
        }
        return punctuator
    }

    fun vad(): Vad = vad ?: VadFactory.create(SpikeApp.paths).also { vad = it }

    suspend fun mt(id: MtEngineId, directions: List<Pair<Lang, Lang>>): MtEngine {
        if (mtId != id) {
            mt?.close()
            mt = MtEngines.create(id, SpikeApp.paths)
            mtId = id
            preparedDirections.clear()
        }
        val engine = mt!!
        val missing = directions.filter { it !in preparedDirections }
        if (missing.isNotEmpty()) {
            engine.prepare(missing)
            preparedDirections += missing
        }
        return engine
    }

    fun releaseAll() {
        asr.values.forEach { it.close() }
        asr.clear()
        vad?.release(); vad = null
        mt?.close(); mt = null; mtId = null
        preparedDirections.clear()
        supertonic?.close(); supertonic = null
        punctuator?.close(); punctuator = null
        androidOffline.clear()
    }
}

enum class TtsRoute(val label: String, val offline: Boolean) {
    ANDROID("Android", true),
    SUPERTONIC("Supertonic", true),
    ANDROID_ONLINE("Android (cần mạng!)", false),
}
