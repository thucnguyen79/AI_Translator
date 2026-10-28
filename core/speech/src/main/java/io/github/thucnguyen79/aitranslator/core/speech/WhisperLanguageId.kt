package io.github.thucnguyen79.aitranslator.core.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.LanguageIdentifier
import io.github.thucnguyen79.aitranslator.core.model.Utterance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Spoken language identification with Whisper tiny (int8, sherpa-onnx export) run directly on ONNX Runtime:
 * one encoder pass and one decoder step from the start-of-transcript token, then the highest-scoring
 * language token among the candidates only. Choosing among the languages at the table instead of all 99
 * matters: on sample speech Vietnamese came out as Chinese, Thai or Arabic about half the time otherwise.
 * Speech picked up from a distance is often ambiguous (Vietnamese vs Chinese within a fraction of a logit),
 * so a narrow win does not override whose turn it is (see [choose]).
 */
class WhisperLanguageId(dir: File, threads: Int = 2) : LanguageIdentifier, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val mutex = Mutex()
    private val encoder: OrtSession
    private val decoder: OrtSession
    private val sot: Long
    private val langTokens: Map<Lang, Int>
    private val layers: Int
    private val textCtx: Int
    private val textState: Int

    init {
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }
        encoder = env.createSession(File(dir, "encoder.int8.onnx").absolutePath, options)
        decoder = env.createSession(File(dir, "decoder.int8.onnx").absolutePath, options)
        val meta = encoder.metadata.customMetadata
        sot = meta.getValue("sot").toLong()
        val codes = meta.getValue("all_language_codes").split(',')
        val tokens = meta.getValue("all_language_tokens").split(',').map(String::toInt)
        langTokens = codes.zip(tokens).mapNotNull { (code, token) -> Lang.find(code)?.let { it to token } }.toMap()
        layers = meta.getValue("n_text_layer").toInt()
        textCtx = meta.getValue("n_text_ctx").toInt()
        textState = meta.getValue("n_text_state").toInt()
    }

    override suspend fun identify(utterance: Utterance, candidates: Collection<Lang>, expected: Lang?): Lang? = withContext(Dispatchers.Default) {
        val options = candidates.filter { it in langTokens }
        if (options.size <= 1) return@withContext options.firstOrNull()
        mutex.withLock {
            val logits = firstStepLogits(WhisperMel.compute(utterance.samples))
            choose(options.associateWith { logits[langTokens.getValue(it)] }, expected)
        }
    }

    private fun firstStepLogits(mel: FloatArray): FloatArray {
        val cacheShape = longArrayOf(layers.toLong(), 1, textCtx.toLong(), textState.toLong())
        val cache = FloatBuffer.allocate(layers * textCtx * textState)
        val owned = mutableListOf<AutoCloseable>()
        try {
            val melTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(mel), longArrayOf(1, WhisperMel.N_MELS.toLong(), WhisperMel.FRAMES.toLong())).also(owned::add)
            val encoded = encoder.run(mapOf("mel" to melTensor)).also(owned::add)
            val inputs = mapOf(
                "tokens" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(sot)), longArrayOf(1, 1)).also(owned::add),
                "in_n_layer_self_k_cache" to OnnxTensor.createTensor(env, cache, cacheShape).also(owned::add),
                "in_n_layer_self_v_cache" to OnnxTensor.createTensor(env, cache.duplicate(), cacheShape).also(owned::add),
                "n_layer_cross_k" to encoded.get("n_layer_cross_k").get() as OnnxTensor,
                "n_layer_cross_v" to encoded.get("n_layer_cross_v").get() as OnnxTensor,
                "offset" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(0)), longArrayOf(1)).also(owned::add),
            )
            decoder.run(inputs, setOf("logits")).use { result ->
                val buffer = (result.get("logits").get() as OnnxTensor).floatBuffer
                return FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        } finally {
            owned.asReversed().forEach { it.close() }
        }
    }

    override fun close() {
        encoder.close()
        decoder.close()
    }

    companion object {
        /** How far (in logits) another language must beat the expected one to take the turn. */
        const val MARGIN = 2f

        /** The best-scoring language, unless it beats [expected] by less than [MARGIN]. */
        fun choose(scores: Map<Lang, Float>, expected: Lang?): Lang {
            val best = scores.maxBy { it.value }.key
            val expectedScore = expected?.let(scores::get) ?: return best
            return if (scores.getValue(best) - expectedScore < MARGIN) expected else best
        }

        fun isPresent(dir: File) = File(dir, "encoder.int8.onnx").isFile && File(dir, "decoder.int8.onnx").isFile
    }
}
