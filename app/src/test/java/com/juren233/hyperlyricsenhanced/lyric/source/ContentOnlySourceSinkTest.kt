package com.juren233.hyperlyricsenhanced.lyric.source

import com.juren233.hyperlyricsenhanced.lyric.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentOnlySourceSinkTest {
    @Test
    fun `source clock events never reach system timeline`() {
        val delegate = RecordingSink()
        val boundary = ContentOnlySourceSink("lyricon", { true }, delegate)

        boundary.onMetadata("title", "artist", "album", "publisher")
        boundary.onPlaybackStateChanged(true)
        boundary.onPositionChanged(123L)
        boundary.onSeekTo(456L)

        assertTrue(delegate.events.isEmpty())
    }

    @Test
    fun `line only source content reaches the same timeline sink`() {
        val delegate = RecordingSink()
        val boundary = ContentOnlySourceSink("superlyric", { true }, delegate)

        boundary.onLyricLine("line")
        boundary.onPlainText("plain")

        assertEquals(listOf("line", "text"), delegate.events)
    }

    @Test
    fun `complete song is converted to tagged timeline content`() {
        val delegate = RecordingSink()
        val boundary = ContentOnlySourceSink("lyricon", { true }, delegate)
        val song = Song(name = "Song")

        boundary.onSongChanged(song)

        assertEquals(1, delegate.contents.size)
        assertEquals("lyricon", delegate.contents.single().sourceId)
        assertEquals(song, delegate.contents.single().song)
    }

    @Test
    fun `late callbacks from stopped source are rejected`() {
        var active = true
        val delegate = RecordingSink()
        val boundary = ContentOnlySourceSink("lyricinfo", { active }, delegate)
        active = false

        boundary.onTimelineContent(TimelineContent("lyricinfo", null, Song(name = "Old")))
        boundary.onStop()

        assertTrue(delegate.contents.isEmpty())
        assertTrue(delegate.events.isEmpty())
        assertNull(boundary.currentPlaybackState())
    }

    private class RecordingSink : LyricSink {
        val contents = mutableListOf<TimelineContent>()
        val events = mutableListOf<String>()

        override fun onTimelineContent(content: TimelineContent) {
            contents += content
        }

        override fun onSongChanged(song: Any?) { events += "song" }
        override fun onLyricLine(line: Any?) { events += "line" }
        override fun onPlainText(text: String?) { events += "text" }
        override fun onStop() { events += "stop" }
        override fun onMetadata(title: String?, artist: String?, album: String?, publisher: String?) {
            events += "metadata"
        }
        override fun onPlaybackStateChanged(isPlaying: Boolean) { events += "playback" }
        override fun currentPlaybackState(): Boolean = true
        override fun onPositionChanged(position: Long) { events += "position" }
        override fun onSeekTo(position: Long) { events += "seek" }
    }
}
