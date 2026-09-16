/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.juren233.hyperlyricsenhanced.root.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMetadataTraceBufferTest {
    @Test
    fun `checkpoint preserves original sequence time and both artist values`() {
        val buffer = AppleMetadataTraceBuffer(capacity = 4)
        val first = buffer.record(10L, "provider", "id=1789740677 artist=아이브")
        val second = buffer.record(20L, "island", "id=1789740677 artist=IVE")
        val checkpoint = buffer.checkpoint(100L, "snapshot") { "mediaArtist=아이브" }
        assertTrue(checkpoint.first().contains("mediaArtist=아이브"))
        assertEquals(listOf("replay $first", "replay $second"), checkpoint.drop(1))
        assertTrue(checkpoint[1].contains("seq=1 elapsedMs=10"))
    }

    @Test
    fun `checkpoint interval avoids evaluating details on frequent callbacks`() {
        val buffer = AppleMetadataTraceBuffer(checkpointIntervalMs = 30_000L)
        var calls = 0
        buffer.checkpoint(0L, "snapshot") { calls++; "first" }
        assertTrue(buffer.checkpoint(29_999L, "snapshot") { calls++; "early" }.isEmpty())
        assertEquals(1, calls)
        assertFalse(buffer.checkpoint(30_000L, "snapshot") { calls++; "next" }.isEmpty())
        assertEquals(2, calls)
    }

    @Test
    fun `history evicts oldest event without resetting sequence`() {
        val buffer = AppleMetadataTraceBuffer(capacity = 2)
        buffer.record(1L, "provider", "old")
        val second = buffer.record(2L, "provider", "current")
        val third = buffer.record(3L, "island", "current")
        assertEquals(
            listOf("replay $second", "replay $third"),
            buffer.checkpoint(4L, "snapshot") { "" }.drop(1),
        )
        assertTrue(third!!.contains("seq=3"))
    }

    @Test
    fun `unchanged island choice is suppressed but artist only changes are recorded`() {
        val buffer = AppleMetadataTraceBuffer()
        buffer.record(1L, "island", "id=1 artist=IVE", changedOnly = true)
        assertNull(buffer.record(2L, "island", "id=1 artist=IVE", changedOnly = true))
        val changed = buffer.record(3L, "island", "id=1 artist=아이브", changedOnly = true)
        assertTrue(changed!!.contains("seq=2"))
        assertTrue(changed.contains("artist=아이브"))
    }

    @Test
    fun `repeated transport deliveries remain visible for ordering diagnosis`() {
        val buffer = AppleMetadataTraceBuffer()
        buffer.record(1L, "central", "same")
        val repeated = buffer.record(2L, "central", "same")
        assertTrue(repeated!!.contains("seq=2"))
    }

    @Test
    fun `trace messages are bounded and cannot inject new log lines`() {
        val buffer = AppleMetadataTraceBuffer()
        val event = buffer.record(1L, "stage\n".repeat(30), "artist\r\n".repeat(300))!!
        assertFalse(event.contains('\n'))
        assertFalse(event.contains('\r'))
        assertTrue(event.length < 1_100)
    }
}
