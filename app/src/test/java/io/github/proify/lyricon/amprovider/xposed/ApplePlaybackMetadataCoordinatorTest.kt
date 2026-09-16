/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class ApplePlaybackMetadataCoordinatorTest {

    @Test
    fun `published current media id wins over a stale observed queue item`() {
        assertEquals(
            "635770202",
            selectCurrentPlaybackMediaId(
                publishedMediaId = "635770202",
                observedQueueMediaId = "1810905308",
            ),
        )
    }

    @Test
    fun `observed queue media id is used before the first publication`() {
        assertEquals(
            "635770202",
            selectCurrentPlaybackMediaId(
                publishedMediaId = null,
                observedQueueMediaId = "635770202",
            ),
        )
    }

    @Test
    fun `queue media id can supply adam id when lyrics item id is absent`() {
        assertEquals(
            635770202L,
            selectPlaybackAdamId(
                runtimeAdamId = null,
                itemMediaId = "635770202",
                expectedContentSongId = "635770202",
            ),
        )
    }

    @Test
    fun `effective original alias survives a repeated base queue callback`() {
        val base = metadata(title = "I Miss You So", artist = "sodagreen")
        val effective = metadataWithEffectiveDisplayAlias(
            metadata = base,
            alias = Alias(
                title = "我好想你",
                artist = "苏打绿",
                language = "zh-Hant",
                album = "",
            ),
        )

        assertEquals("我好想你", effective.title)
        assertEquals("苏打绿", effective.artist)
        assertEquals(base.duration, effective.duration)
        assertEquals(base.queueId, effective.queueId)
    }

    @Test
    fun `artist-only alias cannot erase the queue title`() {
        val effective = metadataWithEffectiveDisplayAlias(
            metadata = metadata(title = "Catherine", artist = "David Tao"),
            alias = Alias(title = "", artist = "陶喆", language = "zh-Hans", album = ""),
        )

        assertEquals("Catherine", effective.title)
        assertEquals("陶喆", effective.artist)
    }

    @Test
    fun `missing or blank alias keeps base queue metadata`() {
        val base = metadata(title = "Title", artist = "Artist")
        assertEquals(base, metadataWithEffectiveDisplayAlias(base, null))
        assertEquals(
            base,
            metadataWithEffectiveDisplayAlias(
                base,
                Alias(title = " ", artist = "", language = "", album = ""),
            ),
        )
    }

    private fun metadata(title: String, artist: String) = MediaMetadataCache.Metadata(
        id = "1777357772",
        title = title,
        artist = artist,
        genre = "Mandopop",
        duration = 329_207L,
        queueId = 42L,
    )
}
