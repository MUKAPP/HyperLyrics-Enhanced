/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.amprovider.xposed

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApplePlaybackMetadataPublicationOrderTest {
    @Test
    fun `current queue metadata is published only after effective alias hydration`() {
        val source = File(
            "src/main/java/io/github/proify/lyricon/amprovider/xposed/metadata/" +
                "ApplePlaybackMetadataCoordinator.kt"
        ).readText()
        val currentBranch = source.substring(
            source.indexOf("if (publishAsCurrent) {"),
            source.indexOf("} else {", source.indexOf("if (publishAsCurrent) {")),
        )

        val hydrateIndex = currentBranch.indexOf("host.onCurrentPlaybackItem(")
        val effectiveAliasIndex = currentBranch.indexOf("host.effectiveMetadataAlias(mediaId)")
        val finalPutIndex = currentBranch.indexOf("MediaMetadataCache.put(publishedMetadata)")
        val publishIndex = currentBranch.indexOf("PlaybackManager.onSongChanged(publishedMetadata.id)")

        assertTrue(hydrateIndex >= 0)
        assertTrue(effectiveAliasIndex > hydrateIndex)
        assertTrue(finalPutIndex > effectiveAliasIndex)
        assertTrue(publishIndex > finalPutIndex)
        assertTrue(!currentBranch.contains("MediaMetadataCache.put(metadata)"))
    }
}
