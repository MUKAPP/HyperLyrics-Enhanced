package com.juren233.hyperlyricsenhanced.common.lyric

import com.juren233.hyperlyricsenhanced.lyric.LrcLine
import com.juren233.hyperlyricsenhanced.lyric.model.RichLyricLine
import com.juren233.hyperlyricsenhanced.lyric.model.Song
import java.util.Locale

/**
 * 判定歌词是否「整体为中文」，供在线翻译抓取门禁使用。
 *
 * 英文语气词（whoa、oh、ayy、yeah 之类）不代表外文歌词，判定前先剔除：
 * - 语气词以符号连接（whoa-oh、oh!yeah、yeah~）时，按非字母数字分词一并拆开剔除；
 * - 紧贴中文的语气词（oh我想你）按行首/行尾的拉丁字母段剔除；
 * - 拉长形式（oooh、ayyy、yeahh）折叠连续重复字母后仍可识别。
 * 剔除后每行要么不含任何字母（纯语气词、标点、数字，视为中性），
 * 要么剩余字母必须全部是汉字；非中文行不超过总行数 10% 时整首歌仍认定为
 * 全中文（容忍“词：xxx”“Program：xxx”“(Live)”等词曲信息行），
 * 超过则判为非全中文。全部非空行都满足时，整首歌才认定为全中文。
 */
object ChineseLyricsPolicy {

    private val INTERJECTIONS = setOf(
        "oh", "ah", "aha", "ay", "ey", "eh", "heh", "hah", "uh", "um", "hm", "mm", "mhm",
        "ya", "yo", "ye", "yah", "yay", "yuh", "ayo", "wu", "woo", "wuh", "whoo", "whoa",
        "woah", "wow", "nah", "na", "la", "lah", "da", "ba", "ha", "ho", "he", "hi", "hey",
        "huh", "hoo", "oho", "wee", "whee", "yee", "aye", "yea", "yeah", "sha", "skr",
        "ugh", "duh", "ew", "oof", "ow", "ouch", "pf", "pff", "pft", "pfft", "phew", "whew",
        "doo", "dee", "dum", "ding", "dong", "bam", "boom", "skrt",
    )
    private val ADLIB_SYLLABLES = INTERJECTIONS.filter { it.length <= 4 }
    private val TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")

    fun isFullyChinese(song: Song?): Boolean = isFullyChinese(song?.lyrics)

    fun isFullyChinese(lines: List<RichLyricLine>?): Boolean =
        isFullyChineseTexts(lines?.map { line -> line.text })

    fun isFullyChineseLrc(lines: List<LrcLine>?): Boolean =
        isFullyChineseTexts(lines?.map { line -> line.content })

    fun isFullyChineseTexts(texts: List<String?>?): Boolean {
        val present = texts.orEmpty().filterNot { it.isNullOrBlank() }
        if (present.isEmpty()) return false
        val nonChinese = present.count { !isChineseOrNeutral(it) }
        if (nonChinese == 0) return true
        // 歌词常混有词曲信息行（“词：xxx”“Program：xxx”“(Live) - 歌手”标题行），
        // 少量非中文行不改变歌曲的中文属性；超过总行数 10% 仍视为非全中文
        // （如英文副歌占大头的歌），避免把真正的外语歌误判成中文歌。
        val tolerated = present.size / 10
        return nonChinese <= tolerated
    }

    /** 剔除英文语气词后：不含字母视为中性；含字母时必须全部是汉字。 */
    fun isChineseOrNeutral(text: String?): Boolean {
        val stripped = stripEnglishInterjections(text) ?: return true
        var index = 0
        while (index < stripped.length) {
            val codePoint = stripped.codePointAt(index)
            index += Character.charCount(codePoint)
            if (!Character.isLetter(codePoint)) continue
            if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN) return false
        }
        return true
    }

    fun stripEnglishInterjections(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        return text.split(TOKEN_SEPARATOR)
            .mapNotNull(::stripTokenInterjections)
            .joinToString(" ")
    }

    private fun stripTokenInterjections(token: String): String? {
        var current = token
        while (current.isNotEmpty()) {
            val leading = latinRunAtStart(current)
            if (leading.isNotEmpty() && isInterjection(leading)) {
                current = current.substring(leading.length)
                continue
            }
            val trailing = latinRunAtEnd(current)
            if (trailing.isNotEmpty() && isInterjection(trailing)) {
                current = current.dropLast(trailing.length)
                continue
            }
            break
        }
        return current.takeIf(String::isNotEmpty)
    }

    private fun latinRunAtStart(value: String): String =
        value.takeWhile { it in 'a'..'z' || it in 'A'..'Z' }

    private fun latinRunAtEnd(value: String): String =
        value.takeLastWhile { it in 'a'..'z' || it in 'A'..'Z' }

    private fun isInterjection(raw: String): Boolean {
        val lower = raw.lowercase(Locale.ROOT)
        if (lower.any { it !in 'a'..'z' }) return false
        // whoo/boom 这类折叠后撞上真词（who/bom）或本身就是标准形的，先按原样查一次。
        if (lower in INTERJECTIONS) return true
        val collapsed = collapseRepeatedCharacters(lower)
        if (collapsed in INTERJECTIONS) return true
        return ADLIB_SYLLABLES.any { syllable ->
            collapsed.length % syllable.length == 0 &&
                collapsed.chunked(syllable.length).all { it == syllable }
        }
    }

    private fun collapseRepeatedCharacters(value: String): String = buildString(value.length) {
        value.forEach { char ->
            if (isEmpty() || last() != char) append(char)
        }
    }
}
