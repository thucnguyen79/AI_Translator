package io.github.thucnguyen79.aitranslator.spike.asr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.thucnguyen79.aitranslator.spike.core.SpikePaths
import java.io.File
import java.nio.LongBuffer
import java.text.Normalizer

/**
 * Vietnamese punctuation + capitalisation restoration with vibert-capu (dragonSwing, CC-BY-SA-4.0; ONNX
 * export by welcomyou). A BERT tagger that predicts one GECToR-style edit per word ($APPEND_, ,
 * $TRANSFORM_CASE_CAPITAL, ...); applied up to [ITERATIONS] times until the text stops changing.
 * Mirrors gec_model.py: no [CLS]/[SEP], a "$START" token first, WordPiece on whitespace-split words.
 */
class ViPunctuator(dir: File, threads: Int = 2) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val vocab = HashMap<String, Long>()
    private val startId: Long
    private val unkId: Long
    private val labels: List<String>
    val loadMs: Long

    init {
        val t0 = System.nanoTime()
        File(dir, "vocab.txt").readLines().forEachIndexed { i, w -> if (w.isNotEmpty()) vocab[w] = i.toLong() }
        startId = vocab.size.toLong() // "$START" is appended to the base vocabulary
        unkId = vocab.getValue("[UNK]")
        labels = File(dir, "labels.txt").readLines().filter { it.isNotEmpty() }
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }
        session = env.createSession(File(dir, MODEL).absolutePath, options)
        loadMs = (System.nanoTime() - t0) / 1_000_000
    }

    /** Returns null for fewer than 3 words (the model is not applied to them, as in gec_model.py). */
    fun restore(text: String): String? {
        var words = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFC).split(' ').filter { it.isNotEmpty() }
        if (words.size < 3) return null
        for (iteration in 1..ITERATIONS) {
            val next = apply(words, predict(words))
            if (next == words) break
            words = next
        }
        return words.joinToString(" ").replace(Regex(" ([,.?:])"), "$1")
    }

    private fun predict(words: List<String>): IntArray {
        val ids = ArrayList<Long>().apply { add(startId) }
        val offsets = ArrayList<Long>().apply { add(0) }
        for (w in words) {
            offsets += ids.size.toLong()
            ids += wordPiece(w)
        }
        val n = ids.size.toLong()
        val inputs = mapOf(
            "input_ids" to tensor(ids.toLongArray(), n),
            "attention_mask" to tensor(LongArray(ids.size) { 1 }, n),
            "token_type_ids" to tensor(LongArray(ids.size), n),
            "input_offsets" to tensor(offsets.toLongArray(), offsets.size.toLong()),
        )
        try {
            session.run(inputs, setOf("logits")).use { result ->
                @Suppress("UNCHECKED_CAST")
                val logits = (result[0].value as Array<Array<FloatArray>>)[0]
                return IntArray(logits.size) { i -> logits[i].indices.maxBy { logits[i][it] } }
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun tensor(data: LongArray, len: Long) = OnnxTensor.createTensor(env, LongBuffer.wrap(data), longArrayOf(1, len))

    /** BERT WordPiece: greedy longest match, "##" continuations, whole word -> [UNK] if a piece is missing. */
    private fun wordPiece(word: String): List<Long> {
        if (word.length > 100) return listOf(unkId)
        val out = ArrayList<Long>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var id: Long? = null
            while (start < end) {
                val sub = if (start == 0) word.substring(start, end) else "##" + word.substring(start, end)
                id = vocab[sub]
                if (id != null) break
                end--
            }
            if (id == null) return listOf(unkId)
            out += id
            start = end
        }
        return out
    }

    /** Applies one label per word; index 0 of [idx] is "$START" and is ignored. */
    private fun apply(words: List<String>, idx: IntArray): List<String> {
        val out = ArrayList<String>()
        for ((i, original) in words.withIndex()) {
            val label = labels[idx[i + 1]]
            out += when (label) {
                "\$TRANSFORM_CASE_CAPITAL" -> capitalize(original)
                "\$TRANSFORM_CASE_UPPER" -> original.uppercase()
                "\$TRANSFORM_CASE_LOWER" -> original.lowercase()
                "\$TRANSFORM_CASE_CAPITAL_1" -> original.take(1) + capitalize(original.drop(1))
                "\$TRANSFORM_CASE_UPPER_-1" -> original.dropLast(1).uppercase() + original.takeLast(1)
                else -> original
            }
            if (label.startsWith("\$APPEND_")) {
                val p = label.removePrefix("\$APPEND_")
                if (words.getOrNull(i + 1) != p && out.last() != p) out += p
            } else if (label == "\$MERGE_SPACE" && i + 1 < words.size) {
                out += MERGE
            }
        }
        return out.joinToString(" ").replace(" $MERGE ", "").replace(" $MERGE", "").split(' ').filter { it.isNotEmpty() }
    }

    private fun capitalize(s: String) = s.take(1).uppercase() + s.drop(1).lowercase()

    override fun close() {
        session.close()
    }

    companion object {
        const val DIR = "vibert-capu"
        const val MODEL = "vibert-capu.int8.onnx"
        private const val ITERATIONS = 3
        private const val MERGE = "\u0000MERGE\u0000"

        fun folder(paths: SpikePaths) = File(paths.punctModels, DIR)
        fun isPresent(paths: SpikePaths) = File(folder(paths), MODEL).isFile
    }
}
