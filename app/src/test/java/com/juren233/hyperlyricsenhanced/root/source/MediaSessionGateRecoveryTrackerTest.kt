/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.juren233.hyperlyricsenhanced.root.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionGateRecoveryTrackerTest {

    @Test
    fun `same player is only stopped once while blocked`() {
        val tracker = MediaSessionGateRecoveryTracker()

        assertNotNull(tracker.requestStop("netease"))
        assertNull(tracker.requestStop("netease"))
    }

    @Test
    fun `recovery without any stop does not replay`() {
        val tracker = MediaSessionGateRecoveryTracker()

        assertFalse(tracker.shouldReplayAfterRecovery("netease"))
    }

    @Test
    fun `recovery of the stopped player replays exactly once`() {
        val tracker = MediaSessionGateRecoveryTracker()
        assertNotNull(tracker.requestStop("netease"))

        assertTrue(tracker.shouldReplayAfterRecovery("netease"))
        assertFalse(tracker.shouldReplayAfterRecovery("netease"))
    }

    @Test
    fun `a blocked player can be stopped again after its recovery`() {
        val tracker = MediaSessionGateRecoveryTracker()
        assertNotNull(tracker.requestStop("netease"))
        assertTrue(tracker.shouldReplayAfterRecovery("netease"))

        assertNotNull(tracker.requestStop("netease"))
    }

    @Test
    fun `switching player while blocked stops the new player separately`() {
        val tracker = MediaSessionGateRecoveryTracker()
        assertNotNull(tracker.requestStop("netease"))

        assertNotNull(tracker.requestStop("kugou"))
        assertNull(tracker.requestStop("kugou"))
    }

    @Test
    fun `recovery at a never-stopped player clears the record without replay`() {
        val tracker = MediaSessionGateRecoveryTracker()
        assertNotNull(tracker.requestStop("netease"))

        // The active player switched to an allowed player: its own switch path delivers
        // the snapshot, so the zombie record is just dropped.
        assertFalse(tracker.shouldReplayAfterRecovery("kugou"))
        assertNotNull(tracker.requestStop("kugou"))
    }

    @Test
    fun `recovery of the latest stopped player replays after an earlier player stop`() {
        val tracker = MediaSessionGateRecoveryTracker()
        assertNotNull(tracker.requestStop("netease"))
        assertNotNull(tracker.requestStop("kugou"))

        // Both players had their one-shot events dropped while blocked; the latest one
        // must still be replayed when its session recovers.
        assertTrue(tracker.shouldReplayAfterRecovery("kugou"))
    }

    @Test
    fun `queued stop requires the original player and a still blocked session`() {
        val tracker = MediaSessionGateRecoveryTracker()
        val request = requireNotNull(tracker.requestStop("apple"))
        assertTrue(tracker.shouldApplyStop(request, "apple", true))
        assertFalse(tracker.shouldApplyStop(request, "apple", false))
        assertFalse(tracker.shouldApplyStop(request, "netease", true))
        assertFalse(tracker.shouldApplyStop(request, null, true))
    }

    @Test
    fun `recovery invalidates an already queued stop`() {
        val tracker = MediaSessionGateRecoveryTracker()
        val old = requireNotNull(tracker.requestStop("apple"))
        assertTrue(tracker.shouldReplayAfterRecovery("apple"))
        assertFalse(tracker.shouldApplyStop(old, "apple", true))
    }

    @Test
    fun `old same player stop cannot become valid again after recovery and another disappearance`() {
        val tracker = MediaSessionGateRecoveryTracker()
        val old = requireNotNull(tracker.requestStop("apple"))
        assertTrue(tracker.shouldReplayAfterRecovery("apple"))
        val current = requireNotNull(tracker.requestStop("apple"))
        assertFalse(tracker.shouldApplyStop(old, "apple", true))
        assertTrue(tracker.shouldApplyStop(current, "apple", true))
    }

    @Test
    fun `switching away and back invalidates the original queued stop`() {
        val tracker = MediaSessionGateRecoveryTracker()
        val old = requireNotNull(tracker.requestStop("apple"))
        val other = requireNotNull(tracker.requestStop("netease"))
        val current = requireNotNull(tracker.requestStop("apple"))
        assertFalse(tracker.shouldApplyStop(old, "apple", true))
        assertFalse(tracker.shouldApplyStop(other, "apple", true))
        assertTrue(tracker.shouldApplyStop(current, "apple", true))
    }

}
