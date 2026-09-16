/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import android.media.MediaMetadata
import android.os.Bundle
import com.juren233.hyperlyricsenhanced.BuildConfig
import java.util.concurrent.atomic.AtomicLong

/** Resolves Media3 metadata identity and owns the shared metadata-chain diagnostics. */
internal class AppleMedia3MetadataCoordinator(
    runtime: AppleMusicProviderRuntime,
    private val metadataStore: AppleMetadataOverrideStore,
    private val resolutionCoordinator: AppleInAppMetadataResolutionCoordinator,
    private val frameworkMetadataHooks: io.github.proify.lyricon.amprovider.xposed.hooks.AppleFrameworkMetadataHooks,
    private val queueMetadataHooks: AppleQueueMetadataHooks,
    private val playbackMetadataCoordinator: ApplePlaybackMetadataCoordinator,
    private val traceSequence: AtomicLong,
) {
    // 可空降级：6.5.3 队列适配器 submit 目标无法解析时由 now-playing 目标兜底携带
    // MEDIA3 成员名；两者都失败时元数据字段读取降级为 null，不再中断初始化。
    private val metadataTarget: AppleMusicHookTarget? =
        runtime.hookResolver.resolveMedia3MetadataTarget()

    fun mediaId(
        metadata: Any,
        fallback: String?,
        trustedFallback: Boolean = false,
    ): String? {
        val bundleId = fieldValue(metadata, AppleMusicRuntimeMember.MEDIA3_METADATA_BUNDLE_FIELD)
            ?.let { it as? Bundle }?.getString(MEDIA3_METADATA_ID_KEY)
        bundleId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }?.let { return it }
        accountMatches(metadata).singleOrNull()?.let { return it }
        return fallback
            ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            ?.takeIf { trustedFallback || matchesId(metadata, it) }
    }

    fun details(metadata: Any): String {
        val bundleId = fieldValue(metadata, AppleMusicRuntimeMember.MEDIA3_METADATA_BUNDLE_FIELD)
            ?.let { it as? Bundle }?.getString(MEDIA3_METADATA_ID_KEY)
        val title = fieldValue(metadata, AppleMusicRuntimeMember.MEDIA3_METADATA_TITLE_FIELD)
            ?.toString()
        val artist = fieldValue(metadata, AppleMusicRuntimeMember.MEDIA3_METADATA_ARTIST_FIELD)
            ?.toString()
        val matches = accountMatches(metadata)
        return "bundleId=$bundleId, title=$title, artist=$artist, " +
            "accountMatches=$matches, matchCount=${matches.size}"
    }

    fun activePlaybackIdentity(): ActivePlaybackMediaIdentity {
        val candidates = listOf(
            "queue" to playbackMetadataCoordinator.currentMetadataId(),
            "in_app_now_playing" to queueMetadataHooks.currentNowPlayingRefresh()?.mediaId,
            "framework_session" to frameworkMetadataHooks.currentMediaId(),
            "playback_refresh" to playbackMetadataCoordinator.currentRefreshMediaId(),
        )
        val selected = candidates.firstOrNull { (_, mediaId) ->
            !mediaId.isNullOrBlank() && mediaId.all(Char::isDigit)
        }
        return ActivePlaybackMediaIdentity(
            mediaId = selected?.second,
            source = selected?.first ?: "none",
            candidates = candidates.joinToString(prefix = "[", postfix = "]") { (source, id) ->
                "$source=$id"
            },
        )
    }

    fun logIdentity(
        event: String,
        identity: ActivePlaybackMediaIdentity = activePlaybackIdentity(),
        details: String,
    ) {
        if (!BuildConfig.DEBUG) return
        val sequence = traceSequence.incrementAndGet()
        val alias = identity.mediaId?.let(resolutionCoordinator::effectiveAlias)
        ProviderLogger.info(
            "Apple Music 元数据链路: seq=$sequence, event=$event, " +
                "selected=${identity.mediaId}, source=${identity.source}, " +
                "candidates=${identity.candidates}, aliasHit=${alias != null}, " +
                "alias=${alias?.title}/${alias?.artist}/${alias?.album}, $details"
        )
    }

    private fun matchesId(metadata: Any, mediaId: String): Boolean {
        val title = textField(metadata, AppleMusicRuntimeMember.MEDIA3_METADATA_TITLE_FIELD)
        val artist = textField(metadata, AppleMusicRuntimeMember.MEDIA3_METADATA_ARTIST_FIELD)
        if (title == null && artist == null) return false

        val account = metadataStore.accountMetadata(mediaId)
        val alias = resolutionCoordinator.effectiveAlias(mediaId)
        val cached = MediaMetadataCache.getMetadataById(mediaId)
        val framework = frameworkMetadataHooks.originalMetadata(mediaId)
        val knownTitles = sequenceOf(
            account?.title,
            alias?.title,
            cached?.title,
            framework?.getString(MediaMetadata.METADATA_KEY_TITLE),
            framework?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
        ).filterNotNull().filter(String::isNotBlank).toSet()
        val knownArtists = sequenceOf(
            account?.artist,
            alias?.artist,
            cached?.artist,
            framework?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            framework?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
        ).filterNotNull().filter(String::isNotBlank).toSet()
        return (title == null || title in knownTitles) &&
            (artist == null || artist in knownArtists)
    }

    private fun accountMatches(metadata: Any): List<String> {
        val title = textField(metadata, AppleMusicRuntimeMember.MEDIA3_METADATA_TITLE_FIELD)
            ?: return emptyList()
        val artist = textField(metadata, AppleMusicRuntimeMember.MEDIA3_METADATA_ARTIST_FIELD)
            ?: return emptyList()
        return metadataStore.accountMetadataSnapshot().entries.mapNotNull { (mediaId, account) ->
            val alias = resolutionCoordinator.effectiveAlias(mediaId)
            val titleMatches = title == account.title || title == alias?.title
            val artistMatches = artist == account.artist || artist == alias?.artist
            mediaId.takeIf { titleMatches && artistMatches }
        }
    }

    private fun textField(metadata: Any, runtimeMember: AppleMusicRuntimeMember): String? =
        fieldValue(metadata, runtimeMember)
            ?.let { it as? CharSequence }?.toString()
            ?.takeIf(String::isNotBlank)

    /** 成员名载体缺失或反射读取失败时返回 null，不向调用方抛出。 */
    private fun fieldValue(metadata: Any, runtimeMember: AppleMusicRuntimeMember): Any? =
        metadataTarget?.runtimeMemberNameOrNull(runtimeMember)?.let { name ->
            runCatching { AppleReflection.field(metadata, name) }.getOrNull()
        }

    private companion object {
        const val MEDIA3_METADATA_ID_KEY = Constants.APPLE_MEDIA3_METADATA_ID_KEY
    }
}
