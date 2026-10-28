package io.github.thucnguyen79.aitranslator.spike.core

import android.content.Context

data class Sentence(val id: String, val category: String, val text: Map<Lang, String>) {
    operator fun get(lang: Lang): String = text.getValue(lang)
}

/** Parallel travel sentences: `id, category, vi, en, zh, ko, ja` (tab separated, header row). */
object Sentences {
    @Volatile private var cache: List<Sentence>? = null

    fun all(context: Context, paths: SpikePaths): List<Sentence> = cache ?: load(context, paths).also { cache = it }

    fun byId(context: Context, paths: SpikePaths): Map<String, Sentence> = all(context, paths).associateBy { it.id }

    private fun load(context: Context, paths: SpikePaths): List<Sentence> {
        val lines = if (paths.sentencesOverride.isFile) {
            paths.sentencesOverride.readLines()
        } else {
            context.assets.open("sentences.tsv").bufferedReader().readLines()
        }
        val header = lines.first().split('\t').map { it.trim() }
        val langCols = Lang.entries.associateWith { header.indexOf(it.code) }
        return lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val cols = line.split('\t')
            Sentence(
                id = cols[0],
                category = cols[1],
                text = langCols.mapValues { (_, i) -> cols.getOrElse(i) { "" }.trim() },
            )
        }
    }

    fun invalidate() {
        cache = null
    }
}
