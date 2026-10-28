package io.github.thucnguyen79.aitranslator.spike.core

import java.util.Locale

enum class Lang(val code: String, val label: String, val locale: Locale) {
    VI("vi", "Tiếng Việt", Locale.forLanguageTag("vi-VN")),
    EN("en", "English", Locale.forLanguageTag("en-US")),
    ZH("zh", "中文", Locale.forLanguageTag("zh-CN")),
    KO("ko", "한국어", Locale.forLanguageTag("ko-KR")),
    JA("ja", "日本語", Locale.forLanguageTag("ja-JP"));

    /** Languages written with spaces between words, where WER is meaningful. */
    val spaced: Boolean get() = this == VI || this == EN

    companion object {
        fun of(code: String): Lang = entries.first { it.code == code }
    }
}
