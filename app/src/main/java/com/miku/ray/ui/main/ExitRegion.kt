package com.miku.ray.ui.main

import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.Utils

/**
 * Resolves which region a global-exit entry belongs to, shown as a flag + ISO
 * code in the exit picker.
 *
 * Two sources, best effort by design: the measured country cache the speedtest
 * writes per profile, then the name. Airport names encode region in three
 * habits — a flag emoji, a bare ISO code, or Chinese/English place words — so
 * the heuristic tries them in that order and gives up quietly.
 */
object ExitRegion {

    // A flag emoji is two regional indicators (U+1F1E6..U+1F1FF). Java regex
    // matches by code point, so the classes must spell code points, not the
    // UTF-16 units — a surrogate class either misses or matches half a flag.
    private val FLAG = Regex("[\\x{1F1E6}-\\x{1F1FF}]{2}")

    /** A bare two-letter code, not glued to other letters (keeps IEPL/IPLC out). */
    private val CODE_TOKEN = Regex("(?<![A-Za-z])[A-Z]{2}(?![A-Za-z])")

    /** ISO code → the words that point at it. Iteration order is match order. */
    private val REGION_WORDS: Map<String, List<String>> = mapOf(
        "HK" to listOf("香港", "hong kong", "hongkong"),
        "TW" to listOf("台湾", "臺灣", "新北", "台中", "彰化", "taiwan", "taipei"),
        "JP" to listOf("日本", "东京", "東京", "大阪", "名古屋", "埼玉", "japan", "tokyo", "osaka"),
        "SG" to listOf("新加坡", "狮城", "singapore"),
        "KR" to listOf("韩国", "韓國", "首尔", "首爾", "korea", "seoul"),
        "US" to listOf("美国", "美國", "洛杉矶", "洛杉磯", "圣何塞", "聖何塞", "西雅图", "西雅圖", "凤凰城", "鳳凰城", "硅谷", "usa", "united states", "america", "los angeles", "san jose", "seattle", "dallas", "chicago"),
        "GB" to listOf("英国", "英國", "伦敦", "倫敦", "united kingdom", "london"),
        "DE" to listOf("德国", "德國", "法兰克福", "法蘭克福", "germany", "frankfurt"),
        "FR" to listOf("法国", "法國", "巴黎", "france", "paris"),
        "NL" to listOf("荷兰", "荷蘭", "阿姆斯特丹", "netherlands", "amsterdam"),
        "CA" to listOf("加拿大", "多伦多", "多倫多", "canada", "toronto"),
        "AU" to listOf("澳大利亚", "澳大利亞", "澳洲", "悉尼", "australia", "sydney"),
        "RU" to listOf("俄罗斯", "俄羅斯", "莫斯科", "russia", "moscow"),
        "IN" to listOf("印度", "孟买", "孟買", "india", "mumbai"),
        "TR" to listOf("土耳其", "伊斯坦布尔", "伊斯坦布爾", "turkey", "istanbul"),
        "MY" to listOf("马来西亚", "馬來西亞", "malaysia"),
        "TH" to listOf("泰国", "泰國", "曼谷", "thailand", "bangkok"),
        "VN" to listOf("越南", "vietnam"),
        "PH" to listOf("菲律宾", "菲律賓", "philippines"),
        "ID" to listOf("印尼", "印度尼西亚", "雅加达", "雅加達", "indonesia", "jakarta"),
        "BR" to listOf("巴西", "圣保罗", "聖保羅", "brazil"),
        "AR" to listOf("阿根廷", "argentina"),
        "IT" to listOf("意大利", "義大利", "italy", "milan"),
        "ES" to listOf("西班牙", "spain", "madrid"),
        "CH" to listOf("瑞士", "苏黎世", "蘇黎世", "switzerland", "zurich"),
        "SE" to listOf("瑞典", "斯德哥尔摩", "斯德哥爾摩", "sweden", "stockholm"),
        "FI" to listOf("芬兰", "芬蘭", "赫尔辛基", "赫爾辛基", "finland", "helsinki"),
        "NO" to listOf("挪威", "norway", "oslo"),
        "PL" to listOf("波兰", "波蘭", "华沙", "華沙", "poland", "warsaw"),
        "UA" to listOf("乌克兰", "烏克蘭", "ukraine"),
        "AE" to listOf("阿联酋", "阿聯酋", "迪拜", "dubai"),
    )

    private val KNOWN_CODES = REGION_WORDS.keys

    /**
     * ISO code of [name], "" when unknown: the measured cache wins, then the
     * name heuristic.
     */
    fun resolve(name: String, measured: Map<String, String>): String =
        measured[name] ?: fromName(name)

    /** Display name for a chip: the flag emoji plus the ISO code. */
    fun label(code: String): String = "${Utils.countryCodeToFlag(code)} $code"

    /** name → measured country code from the profile speedtest cache. */
    fun measuredRegions(): Map<String, String> {
        val map = HashMap<String, String>()
        for (guid in MmkvManager.decodeAllServerList()) {
            val code = MmkvManager.decodeServerAffiliationInfo(guid)?.countryCode
                ?.takeIf { it.length == 2 }?.uppercase() ?: continue
            MmkvManager.decodeServerConfig(guid)?.remarks?.takeIf { it.isNotBlank() }?.let { map[it] = code }
        }
        return map
    }

    /** The three naming habits, tried from most to least trustworthy. */
    private fun fromName(name: String): String {
        FLAG.find(name)?.let { return flagToCode(it.value) }
        CODE_TOKEN.findAll(name).forEach { match ->
            val code = match.value.uppercase()
            if (code in KNOWN_CODES) return code
        }
        val lower = name.lowercase()
        for ((code, words) in REGION_WORDS) {
            if (words.any { lower.contains(it) }) return code
        }
        return ""
    }

    private fun flagToCode(flag: String): String {
        val letters = StringBuilder()
        var i = 0
        while (i < flag.length) {
            val point = flag.codePointAt(i)
            if (point in 0x1F1E6..0x1F1FF) letters.append(Char(point - 0x1F1E6 + 'A'.code))
            i += Character.charCount(point)
        }
        return letters.toString().takeIf { it.length == 2 } ?: ""
    }
}
