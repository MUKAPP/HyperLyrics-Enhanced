package com.juren233.hyperlyricsenhanced.common.lyric

import com.juren233.hyperlyricsenhanced.lyric.LrcLine
import com.juren233.hyperlyricsenhanced.lyric.model.RichLyricLine
import com.juren233.hyperlyricsenhanced.lyric.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseLyricsPolicyTest {

    @Test
    fun `fully chinese lyrics are detected`() {
        val song = Song(
            lyrics = listOf(
                RichLyricLine(text = "感谢你曾来过"),
                RichLyricLine(text = "我早已明白了"),
            ),
        )
        assertTrue(ChineseLyricsPolicy.isFullyChinese(song))
    }

    @Test
    fun `english interjections do not break chinese detection`() {
        assertTrue(
            ChineseLyricsPolicy.isFullyChinese(
                listOf(
                    RichLyricLine(text = "whoa"),
                    RichLyricLine(text = "oh 感谢你曾来过"),
                    RichLyricLine(text = "ayy 我早已明白了"),
                    RichLyricLine(text = "yeah~ 想你"),
                    RichLyricLine(text = "oh!"),
                ),
            )
        )
    }

    @Test
    fun `symbol connected interjections are stripped`() {
        assertTrue(
            ChineseLyricsPolicy.isFullyChinese(
                listOf(
                    RichLyricLine(text = "whoa-oh-oh"),
                    RichLyricLine(text = "oh-oh 想见你"),
                    RichLyricLine(text = "ayy-ayy, yeah-yeah 感谢你曾来过"),
                ),
            )
        )
    }

    @Test
    fun `interjections attached to chinese text are stripped`() {
        assertTrue(
            ChineseLyricsPolicy.isFullyChinese(
                listOf(
                    RichLyricLine(text = "oh我想你"),
                    RichLyricLine(text = "感谢你曾来过whoa"),
                    RichLyricLine(text = "yeah~想见你"),
                ),
            )
        )
    }

    @Test
    fun `elongated interjections are stripped`() {
        assertTrue(
            ChineseLyricsPolicy.isFullyChinese(
                listOf(
                    RichLyricLine(text = "oooh"),
                    RichLyricLine(text = "ayyy 想你"),
                    RichLyricLine(text = "yeahh"),
                    RichLyricLine(text = "hahaha 感谢你曾来过"),
                    RichLyricLine(text = "lalala"),
                ),
            )
        )
    }

    @Test
    fun `pure punctuation and digit lines are neutral`() {
        assertTrue(
            ChineseLyricsPolicy.isFullyChinese(
                listOf(
                    RichLyricLine(text = "感谢你曾来过"),
                    RichLyricLine(text = "……"),
                    RichLyricLine(text = "1988"),
                ),
            )
        )
    }

    @Test
    fun `real english words still block chinese detection`() {
        assertFalse(
            ChineseLyricsPolicy.isFullyChinese(
                listOf(
                    RichLyricLine(text = "感谢你曾来过"),
                    RichLyricLine(text = "Baby 我早已明白了"),
                ),
            )
        )
        assertFalse(ChineseLyricsPolicy.isChineseOrNeutral("thank you 想你"))
        assertFalse(ChineseLyricsPolicy.isChineseOrNeutral("no no no"))
    }

    @Test
    fun `non han scripts block chinese detection`() {
        assertFalse(ChineseLyricsPolicy.isChineseOrNeutral("君の名は"))
        assertFalse(ChineseLyricsPolicy.isChineseOrNeutral("사랑해"))
    }

    @Test
    fun `few credit lines do not break chinese detection`() {
        val lines = buildList {
            add(RichLyricLine(text = "推开世界的门 (Live) - 王源/周传雄"))
            add(RichLyricLine(text = "Program：某人"))
            repeat(18) { index -> add(RichLyricLine(text = "推开世界的门第${index}行")) }
        }
        assertTrue(ChineseLyricsPolicy.isFullyChinese(lines))
    }

    @Test
    fun `widespread english lines still block chinese detection`() {
        val lines = buildList {
            repeat(6) { index -> add(RichLyricLine(text = "中文歌词第${index}行")) }
            repeat(4) { index -> add(RichLyricLine(text = "曾被压榨的 Now walk on water $index")) }
        }
        assertFalse(ChineseLyricsPolicy.isFullyChinese(lines))
    }

    @Test
    fun `single non chinese line song stays non chinese`() {
        assertFalse(
            ChineseLyricsPolicy.isFullyChinese(
                listOf(RichLyricLine(text = "Baby 我早已明白了"))
            )
        )
    }

    @Test
    fun `blank or missing lyrics are not fully chinese`() {
        assertFalse(ChineseLyricsPolicy.isFullyChinese(null as Song?))
        assertFalse(ChineseLyricsPolicy.isFullyChinese(Song(lyrics = null)))
        assertFalse(ChineseLyricsPolicy.isFullyChinese(listOf<RichLyricLine>()))
        assertFalse(
            ChineseLyricsPolicy.isFullyChinese(
                listOf(RichLyricLine(text = "  "), RichLyricLine(text = ""))
            )
        )
    }

    @Test
    fun `lrc lines are judged by content`() {
        assertTrue(
            ChineseLyricsPolicy.isFullyChineseLrc(
                listOf(
                    LrcLine(0, "感谢你曾来过", translation = "（在那个房间）"),
                    LrcLine(1_000, "whoa-oh"),
                )
            )
        )
        assertFalse(
            ChineseLyricsPolicy.isFullyChineseLrc(
                listOf(LrcLine(0, "Baby 感谢你曾来过"))
            )
        )
    }

    @Test
    fun `extended interjection families are stripped`() {
        assertTrue(
            ChineseLyricsPolicy.isFullyChinese(
                listOf(
                    RichLyricLine(text = "hah"),
                    RichLyricLine(text = "ughh 想你"),
                    RichLyricLine(text = "eww"),
                    RichLyricLine(text = "oof 感谢你曾来过"),
                    RichLyricLine(text = "ouch!"),
                    RichLyricLine(text = "pfft"),
                    RichLyricLine(text = "pffft"),
                    RichLyricLine(text = "skrrt 想你"),
                    RichLyricLine(text = "whoo 想见你"),
                    RichLyricLine(text = "ayo 想你"),
                    RichLyricLine(text = "phew~"),
                ),
            )
        )
    }

    @Test
    fun `scat syllables and onomatopoeia are stripped`() {
        assertTrue(
            ChineseLyricsPolicy.isFullyChinese(
                listOf(
                    RichLyricLine(text = "boom-boom 想你"),
                    RichLyricLine(text = "doo-doo-doo"),
                    RichLyricLine(text = "yeahyeah"),
                    RichLyricLine(text = "bam! 感谢你曾来过"),
                    RichLyricLine(text = "ding-dong"),
                ),
            )
        )
    }

    @Test
    fun `lexical words stay non interjections`() {
        assertFalse(ChineseLyricsPolicy.isChineseOrNeutral("who 想你"))
        assertFalse(ChineseLyricsPolicy.isChineseOrNeutral("yup 想你"))
        assertFalse(ChineseLyricsPolicy.isChineseOrNeutral("no 想你"))
        assertFalse(ChineseLyricsPolicy.isChineseOrNeutral("mama 想你"))
        assertFalse(ChineseLyricsPolicy.isChineseOrNeutral("papa 想你"))
        assertFalse(ChineseLyricsPolicy.isChineseOrNeutral("boombox 想你"))
    }

    @Test
    fun `strip english interjections keeps remaining text`() {
        assertEquals("我想你", ChineseLyricsPolicy.stripEnglishInterjections("oh我想你"))
        assertEquals("感谢你曾来过", ChineseLyricsPolicy.stripEnglishInterjections("whoa-oh 感谢你曾来过"))
        assertEquals("", ChineseLyricsPolicy.stripEnglishInterjections("whoa-oh-oh"))
        assertEquals("", ChineseLyricsPolicy.stripEnglishInterjections("oooh"))
    }
}
