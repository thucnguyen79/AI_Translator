package io.github.thucnguyen79.aitranslator.spike.core

import java.text.Normalizer
import kotlin.math.ceil
import kotlin.math.min

/** Text normalisation and scores. tools/spike.py re-scores exported results with sacrebleu. */
object Metrics {
    private val punct = Regex("[\\p{P}\\p{S}]")
    private val spaces = Regex("\\s+")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFC)
            .replace(punct, " ")
            .replace(spaces, " ")
            .trim()

    fun <T> editDistance(ref: List<T>, hyp: List<T>): Int {
        var prev = IntArray(hyp.size + 1) { it }
        var cur = IntArray(hyp.size + 1)
        for (i in 1..ref.size) {
            cur[0] = i
            for (j in 1..hyp.size) {
                val sub = prev[j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1
                cur[j] = min(sub, min(prev[j] + 1, cur[j - 1] + 1))
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[hyp.size]
    }

    /** Errors and reference length at character level, ignoring spaces. */
    fun charErrors(ref: String, hyp: String): Pair<Int, Int> {
        val r = normalize(ref).replace(" ", "").codePoints().toArray().toList()
        val h = normalize(hyp).replace(" ", "").codePoints().toArray().toList()
        return editDistance(r, h) to r.size
    }

    /** Errors and reference length at word level. */
    fun wordErrors(ref: String, hyp: String): Pair<Int, Int> {
        val r = normalize(ref).split(' ').filter { it.isNotEmpty() }
        val h = normalize(hyp).split(' ').filter { it.isNotEmpty() }
        return editDistance(r, h) to r.size
    }

    fun mean(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.sum() / values.size

    fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val idx = (ceil(p / 100.0 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }
}

/** Corpus-level chrF (character 6-grams, beta = 2), same definition as sacrebleu's CHRF. */
class ChrF(private val order: Int = 6, private val beta: Double = 2.0) {
    private val hypCount = LongArray(order)
    private val refCount = LongArray(order)
    private val matchCount = LongArray(order)

    fun add(hyp: String, ref: String) {
        val h = hyp.replace(Regex("\\s+"), "")
        val r = ref.replace(Regex("\\s+"), "")
        for (n in 1..order) {
            val hg = ngrams(h, n)
            val rg = ngrams(r, n)
            hypCount[n - 1] += hg.values.sum().toLong()
            refCount[n - 1] += rg.values.sum().toLong()
            matchCount[n - 1] += hg.entries.sumOf { (g, c) -> min(c, rg[g] ?: 0) }.toLong()
        }
    }

    fun score(): Double {
        val b2 = beta * beta
        var total = 0.0
        var effective = 0
        for (i in 0 until order) {
            if (hypCount[i] > 0 && refCount[i] > 0) {
                val p = matchCount[i].toDouble() / hypCount[i]
                val r = matchCount[i].toDouble() / refCount[i]
                if (p + r > 0) total += (1 + b2) * p * r / (b2 * p + r)
                effective++
            }
        }
        return if (effective == 0) 0.0 else 100.0 * total / effective
    }

    private fun ngrams(s: String, n: Int): Map<String, Int> {
        val cps = s.codePoints().toArray()
        if (cps.size < n) return emptyMap()
        val out = HashMap<String, Int>()
        for (i in 0..cps.size - n) {
            val g = String(cps, i, n)
            out[g] = (out[g] ?: 0) + 1
        }
        return out
    }
}
