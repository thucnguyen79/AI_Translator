package io.github.thucnguyen79.aitranslator.core.model

import java.util.Locale

enum class Lang(val code: String, val nativeName: String, val locale: Locale) {
    VI("vi", "Tiếng Việt", Locale.forLanguageTag("vi-VN")),
    EN("en", "English", Locale.forLanguageTag("en-US")),
    ZH("zh", "中文", Locale.forLanguageTag("zh-CN")),
    KO("ko", "한국어", Locale.forLanguageTag("ko-KR")),
    JA("ja", "日本語", Locale.forLanguageTag("ja-JP"));

    companion object {
        fun of(code: String): Lang = entries.first { it.code == code }

        fun find(code: String): Lang? = entries.firstOrNull { it.code == code }
    }
}
