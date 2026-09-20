package com.juren233.hyperlyricsenhanced.root.timeline

import com.juren233.hyperlyricsenhanced.lyric.source.TimelineContent
import com.juren233.hyperlyricsenhanced.timeline.model.TrackIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineContentPolicyTest {
    private val anchor = TrackIdentity(
        packageName = "player.package",
        mediaId = "session-id",
        title = "Song",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
    )

    @Test
    fun `inactive source can never enter timeline`() {
        val content = TimelineContent(sourceId = "lyricinfo", track = anchor, song = null)

        assertEquals(
            TimelineContentPolicy.Decision.DROP_INACTIVE_SOURCE,
            TimelineContentPolicy.decide("lyricon", anchor, content),
        )
    }

    @Test
    fun `content waits until system media identity exists`() {
        val content = TimelineContent(sourceId = "remote_payload", track = anchor, song = null)

        assertEquals(
            TimelineContentPolicy.Decision.HOLD_FOR_TRACK,
            TimelineContentPolicy.decide("remote_payload", null, content),
        )
    }

    @Test
    fun `app timeline requires exact cross process identity`() {
        val content = TimelineContent(
            sourceId = "remote_payload",
            track = anchor.copy(mediaId = null),
            song = null,
            strictIdentity = true,
        )

        assertEquals(
            TimelineContentPolicy.Decision.APPLY,
            TimelineContentPolicy.decide("remote_payload", anchor, content),
        )
        assertEquals(
            TimelineContentPolicy.Decision.DROP_WRONG_TRACK,
            TimelineContentPolicy.decide(
                "remote_payload",
                anchor.copy(durationMs = 181_000L),
                content,
            ),
        )
    }

    @Test
    fun `in process source matches only fields it actually provides`() {
        val partial = TrackIdentity(
            packageName = anchor.packageName,
            title = anchor.title,
            artist = anchor.artist,
        )
        val content = TimelineContent(sourceId = "lyricon", track = partial, song = null)

        assertEquals(
            TimelineContentPolicy.Decision.APPLY,
            TimelineContentPolicy.decide("lyricon", anchor, content),
        )
        assertEquals(
            TimelineContentPolicy.Decision.DROP_WRONG_TRACK,
            TimelineContentPolicy.decide(
                "lyricon",
                anchor.copy(packageName = "other.player"),
                content,
            ),
        )
    }

    @Test
    fun `available field matching never falls back to fuzzy title or id`() {
        assertFalse(anchor.matchesAvailableFields(anchor.copy(mediaId = "other-id")))
        assertFalse(
            anchor.copy(mediaId = null).matchesAvailableFields(
                anchor.copy(mediaId = null, title = "Song (Live)"),
            )
        )
        assertTrue(
            anchor.copy(mediaId = null, album = "", durationMs = 0L)
                .matchesAvailableFields(anchor),
        )
    }
}
