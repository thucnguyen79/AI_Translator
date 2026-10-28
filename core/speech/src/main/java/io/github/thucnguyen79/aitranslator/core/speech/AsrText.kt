package io.github.thucnguyen79.aitranslator.core.speech

import io.github.thucnguyen79.aitranslator.core.model.Lang

/**
 * Turns raw ASR output into sentence-cased, punctuated text for translation.
 *
 * The Vietnamese zipformer models emit UPPER CASE text without punctuation ("XIN CHÀO"); Bergamot was
 * trained on normal text and mis-segments it badly ("HANDSELEN"). SenseVoice and Whisper already
 * produce cased, punctuated text, so only all-caps output is rewritten.
 */
object AsrText {
    private val viProperNouns = listOf(
        "Việt Nam", "Hà Nội", "Sài Gòn", "Hồ Chí Minh", "Đà Nẵng", "Hội An", "Huế", "Nha Trang", "Đà Lạt",
        "Phú Quốc", "Hạ Long", "Sa Pa", "Hàn Quốc", "Nhật Bản", "Trung Quốc", "Đài Loan", "Thái Lan",
        "Bắc Kinh", "Thượng Hải", "Seoul", "Busan", "Tokyo", "Osaka", "Kyoto", "Vietnam Airlines",
    )
    private val viLanguages = listOf("Việt", "Anh", "Trung", "Hàn", "Nhật", "Pháp", "Đức")

    /** Sentence-final particles and question words that make a Vietnamese sentence a question. */
    private val viQuestionEnds = listOf(
        "không", "chưa", "à", "hả", "hở", "nhỉ", "chứ", "gì", "đâu", "nào", "sao", "mấy",
        "bao nhiêu", "bao giờ", "bao lâu", "thế nào", "ra sao", "phải không", "được không",
    )
    private val viQuestionStarts = listOf("tại sao", "vì sao", "sao", "ai", "ở đâu", "khi nào", "làm sao", "làm thế nào", "có phải")

    /** Opening greetings/apologies followed by a comma ("Xin lỗi, bạn có…"); without it MT reads "Are you sorry…". */
    private val viOpeners = listOf("xin lỗi", "xin chào", "dạ", "vâng", "này") +
        listOf("bạn", "anh", "chị", "em", "ông", "bà", "cô", "chú").map { "chào $it" }

    /**
     * [punctuate] is an optional model (Vietnamese only, see ViPunctuator) that returns null when it does
     * not handle the input; the rules below are the fallback.
     */
    fun restore(text: String, lang: Lang, punctuate: ((String) -> String?)? = null): String {
        var t = text.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty() || !isAllCaps(t)) return t
        if (lang == Lang.VI && punctuate != null) {
            punctuate(t)?.let { m ->
                val s = m.replaceFirstChar { it.titlecase(lang.locale) }
                return if (s.last().isLetterOrDigit()) s + (if (isViQuestion(s)) "?" else ".") else s
            }
        }
        t = t.lowercase(lang.locale)
        if (lang == Lang.VI) {
            for (name in viProperNouns) t = t.replace(word(name), name)
            for (l in viLanguages) t = t.replace(word("tiếng $l"), "tiếng $l")
            viOpeners.firstOrNull { t.startsWith("$it ") }?.let { t = "$it," + t.substring(it.length) }
        }
        t = t.replaceFirstChar { it.titlecase(lang.locale) }
        if (t.last().isLetterOrDigit()) t += if (lang == Lang.VI && isViQuestion(t)) "?" else "."
        return t
    }

    private fun isAllCaps(t: String): Boolean {
        val letters = t.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.count { it.isUpperCase() } >= letters.length * 0.9
    }

    private fun isViQuestion(t: String): Boolean {
        val s = t.lowercase(Lang.VI.locale)
        return viQuestionEnds.any { s == it || s.endsWith(" $it") } || viQuestionStarts.any { s.startsWith("$it ") }
    }

    /** Case-insensitive whole-word match (Vietnamese letters count as word characters). */
    private fun word(phrase: String) =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
}
