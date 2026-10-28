package io.github.thucnguyen79.aitranslator.core.translate

import io.github.thucnguyen79.aitranslator.core.model.Lang
import io.github.thucnguyen79.aitranslator.core.model.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Mozilla Bergamot (Firefox Translations). Models only exist to/from English, so other directions are
 * pivoted inside the native service (from→en→to).
 *
 * @param modelDir returns the folder of a direction such as "vien" or "enzh" (model.*.bin, vocab, lex.*).
 */
class BergamotTranslator(private val modelDir: (key: String) -> File) : Translator, AutoCloseable {
    private val mutex = Mutex()
    private val loaded = HashSet<String>()

    /**
     * Loads the models [from]→[to] needs; translating loads them too, this only moves the cost earlier.
     * Returns true when a model was loaded by this call.
     */
    suspend fun prepare(from: Lang, to: Lang): Boolean = withContext(Dispatchers.Default) {
        mutex.withLock { legs(from, to).map(::load).any { it } }
    }

    override suspend fun translate(text: String, from: Lang, to: Lang): String = withContext(Dispatchers.Default) {
        if (from == to || text.isBlank()) return@withContext text
        mutex.withLock {
            val legs = legs(from, to)
            legs.forEach(::load)
            BergamotNative.translate(legs.first(), legs.getOrNull(1), arrayOf(text)).first()
        }
    }

    override fun close() {
        BergamotNative.unloadAll()
        loaded.clear()
    }

    private fun load(key: String): Boolean {
        if (key in loaded) return false
        BergamotNative.loadModel(key, config(modelDir(key)))
        loaded += key
        return true
    }

    private fun legs(from: Lang, to: Lang): List<String> = when {
        from == Lang.EN || to == Lang.EN -> listOf(key(from, to))
        else -> listOf(key(from, Lang.EN), key(Lang.EN, to))
    }

    private fun key(from: Lang, to: Lang) = from.code + to.code

    /** Marian options, as used by Firefox for the int8 "intgemm.alphas" models. */
    private fun config(dir: File): String {
        val files = dir.listFiles().orEmpty()
        fun find(p: (String) -> Boolean) = files.firstOrNull { p(it.name) }
        val model = requireNotNull(find { it.startsWith("model.") }) { "model.*.bin missing in $dir" }
        val shared = find { it.startsWith("vocab.") }
        val src = requireNotNull(find { it.startsWith("srcvocab.") } ?: shared) { "vocabulary missing in $dir" }
        val trg = requireNotNull(find { it.startsWith("trgvocab.") } ?: shared) { "vocabulary missing in $dir" }
        val lex = find { it.startsWith("lex.") }
        return buildString {
            appendLine("models:\n  - ${model.absolutePath}")
            appendLine("vocabs:\n  - ${src.absolutePath}\n  - ${trg.absolutePath}")
            if (lex != null) appendLine("shortlist:\n  - ${lex.absolutePath}\n  - false")
            appendLine("beam-size: 1")
            appendLine("normalize: 1.0")
            appendLine("word-penalty: 0")
            appendLine("max-length-break: 128")
            appendLine("mini-batch-words: 1024")
            appendLine("workspace: 128")
            appendLine("max-length-factor: 2.0")
            appendLine("skip-cost: true")
            appendLine("cpu-threads: 1")
            appendLine("quiet: true")
            appendLine("quiet-translation: true")
            if ("intgemm.alphas" in model.name) appendLine("gemm-precision: int8shiftAlphaAll")
            else if ("intgemm8" in model.name) appendLine("gemm-precision: int8shiftAll")
            appendLine("alignment: soft")
        }
    }

    companion object {
        /** Direction folders a language needs: both ways with English. */
        fun directionKeys(lang: Lang): List<String> =
            if (lang == Lang.EN) emptyList() else listOf("${lang.code}en", "en${lang.code}")
    }
}

internal object BergamotNative {
    init {
        System.loadLibrary("bergamot_jni")
    }

    @JvmStatic external fun loadModel(key: String, yamlConfig: String)

    /** Translates with [first], then [second] when pivoting. */
    @JvmStatic external fun translate(first: String, second: String?, inputs: Array<String>): Array<String>

    @JvmStatic external fun unloadAll()
}
