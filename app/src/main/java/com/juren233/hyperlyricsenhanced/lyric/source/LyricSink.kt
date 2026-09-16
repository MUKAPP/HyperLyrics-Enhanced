package com.juren233.hyperlyricsenhanced.lyric.source

interface LyricSink {
    fun onSongChanged(song: Any?)
    fun onLyricLine(line: Any?)
    fun onPlainText(text: String?)
    fun onStop()
    fun onMetadata(title: String?, artist: String?, album: String?, publisher: String? = null)
    fun onPlaybackStateChanged(isPlaying: Boolean)
    /** Actual consumer state, when observable; null preserves event-only sinks. */
    fun currentPlaybackState(): Boolean? = null
    fun onPositionChanged(position: Long)
    fun onSeekTo(position: Long) = onPositionChanged(position)
    fun onOnlineTranslationMatched(song: Any?) = onSongChanged(song)
    fun onOnlineTranslationUnavailable(song: Any?) = Unit
}
