package io.github.thucnguyen79.aitranslator.spike

import com.k2fsa.sherpa.onnx.Vad
import io.github.thucnguyen79.aitranslator.spike.asr.AsrModel
import io.github.thucnguyen79.aitranslator.spike.asr.SherpaAsr
import io.github.thucnguyen79.aitranslator.spike.asr.VadFactory
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngine
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngineId
import io.github.thucnguyen79.aitranslator.spike.mt.MtEngines
import io.github.thucnguyen79.aitranslator.spike.tts.AndroidTts
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

    fun asr(model: AsrModel, threads: Int = 2): SherpaAsr {
        val cached = asr[model.id]
        if (cached != null && cached.numThreads == threads) return cached
        cached?.close()
        return SherpaAsr(model, SpikeApp.paths, threads).also { asr[model.id] = it }
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
    }
}
