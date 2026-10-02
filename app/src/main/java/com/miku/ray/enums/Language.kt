package com.miku.ray.enums

enum class Language(val code: String) {
    AUTO("auto"),
    ENGLISH("en"),
    CHINA("zh-rCN"),
    TRADITIONAL_CHINESE("zh-rTW"),
    VIETNAMESE("vi"),
    RUSSIAN("ru"),
    PERSIAN("fa"),
    ARABIC("ar"),
    BANGLA("bn"),
    BAKHTIARI("bqi-rIR"),
    INDONESIAN("id");

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: AUTO
        }
    }
}
