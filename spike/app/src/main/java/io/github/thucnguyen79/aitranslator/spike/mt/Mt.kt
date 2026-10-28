package io.github.thucnguyen79.aitranslator.spike.mt

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import io.github.thucnguyen79.aitranslator.spike.BuildConfig
import io.github.thucnguyen79.aitranslator.spike.core.Lang
import io.github.thucnguyen79.aitranslator.spike.core.SpikePaths
import kotlinx.coroutines.tasks.await
import java.io.File

interface MtEngine : AutoCloseable {
    val id: String

    /** Loads (and for ML Kit downloads) whatever is needed for these directions. Returns load time in ms. */
    suspend fun prepare(directions: List<Pair<Lang, Lang>>): Long

    suspend fun translate(text: String, from: Lang, to: Lang): String

    /** True when the direction runs through English. */
    fun pivots(from: Lang, to: Lang): Boolean = from != Lang.EN && to != Lang.EN
}

enum class MtEngineId(val label: String) {
    BERGAMOT("Bergamot (mã nguồn mở)"),
    MLKIT("ML Kit (mốc so sánh)"),
}

object MtEngines {
    fun create(id: MtEngineId, paths: SpikePaths): MtEngine = when (id) {
        MtEngineId.BERGAMOT -> BergamotEngine(paths)
        MtEngineId.MLKIT -> MlKitEngine()
    }

    fun available(): List<MtEngineId> =
        if (BergamotNative.isLoaded) MtEngineId.entries else listOf(MtEngineId.MLKIT)
}

/** Google ML Kit on-device translation: closed source, English-centric, ~30 MB per language. */
class MlKitEngine : MtEngine {
    override val id = "mlkit"
    private val translators = HashMap<Pair<Lang, Lang>, Translator>()

    override suspend fun prepare(directions: List<Pair<Lang, Lang>>): Long {
        val t0 = System.nanoTime()
        for (d in directions) client(d.first, d.second).downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        return (System.nanoTime() - t0) / 1_000_000
    }

    override suspend fun translate(text: String, from: Lang, to: Lang): String =
        client(from, to).translate(text).await()

    private fun client(from: Lang, to: Lang): Translator = translators.getOrPut(from to to) {
        Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(code(from)).setTargetLanguage(code(to)).build())
    }

    private fun code(lang: Lang) = when (lang) {
        Lang.VI -> TranslateLanguage.VIETNAMESE
        Lang.EN -> TranslateLanguage.ENGLISH
        Lang.ZH -> TranslateLanguage.CHINESE
        Lang.KO -> TranslateLanguage.KOREAN
        Lang.JA -> TranslateLanguage.JAPANESE
    }

    override fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}

/**
 * Mozilla Bergamot (Firefox Translations). Models only exist to/from English, so other directions are
 * pivoted inside the native service (from→en→to).
 *
 * Files for a direction live in models/bergamot/<from><to>/ (e.g. vien, envi, enzh), as downloaded by
 * `tools/spike.py models --mt`.
 */
class BergamotEngine(private val paths: SpikePaths) : MtEngine {
    override val id = "bergamot"
    private val loaded = HashSet<String>()

    override suspend fun prepare(directions: List<Pair<Lang, Lang>>): Long {
        check(BergamotNative.isLoaded) { "Bản build này không có Bergamot (xem spike/README.md)" }
        val t0 = System.nanoTime()
        directions.flatMap { (f, t) -> legs(f, t) }.distinct().forEach { key ->
            if (loaded.add(key)) BergamotNative.loadModel(key, config(File(paths.bergamotModels, key)))
        }
        return (System.nanoTime() - t0) / 1_000_000
    }

    override suspend fun translate(text: String, from: Lang, to: Lang): String {
        val legs = legs(from, to)
        val out = BergamotNative.translate(legs.first(), legs.getOrNull(1), arrayOf(text))
        return out.first()
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
        val model = requireNotNull(find { it.startsWith("model.") }) { "Thiếu model.*.bin trong ${dir.path}" }
        val shared = find { it.startsWith("vocab.") }
        val src = find { it.startsWith("srcvocab.") } ?: shared
        val trg = find { it.startsWith("trgvocab.") } ?: shared
        val lex = find { it.startsWith("lex.") }
        requireNotNull(src) { "Thiếu vocab trong ${dir.path}" }
        requireNotNull(trg) { "Thiếu vocab trong ${dir.path}" }
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

    override fun close() {
        if (BergamotNative.isLoaded) BergamotNative.unloadAll()
        loaded.clear()
    }
}

object BergamotNative {
    val isLoaded: Boolean = BuildConfig.WITH_BERGAMOT && runCatching { System.loadLibrary("bergamot_jni") }.isSuccess

    @JvmStatic external fun loadModel(key: String, yamlConfig: String)

    /** Translates with [first], then [second] when pivoting. */
    @JvmStatic external fun translate(first: String, second: String?, inputs: Array<String>): Array<String>

    @JvmStatic external fun unloadAll()
}
