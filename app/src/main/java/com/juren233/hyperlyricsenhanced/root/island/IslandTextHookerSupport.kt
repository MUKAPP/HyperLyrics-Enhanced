package com.juren233.hyperlyricsenhanced.root.island

import android.view.View
import android.view.ViewGroup
import com.juren233.hyperlyricsenhanced.BuildConfig
import com.juren233.hyperlyricsenhanced.lyric.view.RichLyricLineView
import com.juren233.hyperlyricsenhanced.lyric.view.SpaceGateRichLyricLineView
import com.juren233.hyperlyricsenhanced.root.LyriconDataBridge
import com.juren233.hyperlyricsenhanced.root.island.renderer.BaseIslandRenderer
import com.juren233.hyperlyricsenhanced.root.utils.HookLogger

internal object IslandTextHookerSupport {
    const val TAG = "IslandTextHooker"

    /**
     * 运行时判定是否走"平板超级岛"路径。
     *
     * 手机出厂插件里同样携带 `DynamicIslandContentViewPadHelper`（真机 DEX 已核对），
     * 原生是用 `CommonUtils.getIS_TABLET()` 在 Pad/Phone helper 之间选择的，
     * 因此"类是否存在"不能作为平板判据。优先反射系统自身判定，失败时退回 sw600dp。
     */
    internal fun isTabletIslandDevice(cl: ClassLoader): Boolean {
        runCatching {
            val utils = cl.loadClass("miui.systemui.util.CommonUtils")
            val instance = utils.getField("INSTANCE").get(null)
            utils.getMethod("getIS_TABLET").invoke(instance) as? Boolean
        }.getOrNull()?.let { return it }
        return runCatching {
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            val density = if (metrics.density > 0f) metrics.density else 1f
            (minOf(metrics.widthPixels, metrics.heightPixels) / density) >= 600f
        }.getOrDefault(false)
    }

    fun extractMediaInfoFromContentOrReal(contentView: ViewGroup): IslandProbeUtils.MediaIslandInfo? {
        val currentData = IslandProbeUtils.getCurrentIslandData(contentView)
        val currentInfo = IslandProbeUtils.extractMediaIslandInfo(currentData)
        if (currentInfo != null) return currentInfo

        val realView = callNoArgMethodResult(contentView, "getRealView")
        val realData = IslandProbeUtils.getCurrentIslandData(realView)
        return IslandProbeUtils.extractMediaIslandInfo(realData)
    }

    fun prepareFrozenFakeIslandForTransition(fakeView: ViewGroup, source: String) {
        if (!IslandProbeUtils.isSuperIslandEnabled()) return
        val mediaInfo = extractMediaInfoFromContentOrReal(fakeView) ?: return

        if (!isCurrentLyricIsland(mediaInfo)) {
            return
        }
        if (!shouldRenderInjectedIsland()) {
            IslandHostFacade.clearInjectedViews(fakeView)
            return
        }

        if (IslandLyricTextInjector.hasInjectedLyricText(fakeView)) {
            IslandLyricTextInjector.restoreExistingSlotsLightweight(fakeView)
        } else {
            IslandLyricTextInjector.injectSlots(fakeView, reconfigureExisting = false, suppressAnimation = true)
        }
        IslandLyricTextInjector.refreshCurrentContent(fakeView, includeLyricSlots = true, force = true, suppressAnimation = true)
        IslandLyricTextInjector.freezeInjectedLyricProgress(fakeView, LyriconDataBridge.currentPosition)
        fakeView.alpha = 1f
        // 冻结快照可能落在数据暂缺或视图被重置的瞬间（签名去重感知不到实际内容丢失），
        // 过渡开始后补一次完整注入+校验刷新：丢失内容在此处回填并重新冻结，避免收回动画全程主行空白。
        fakeView.post {
            if (!IslandProbeUtils.isSuperIslandEnabled()) return@post
            if (!shouldRenderInjectedIsland()) return@post
            if (!fakeView.isAttachedToWindow) return@post
            IslandLyricTextInjector.injectSlots(fakeView, reconfigureExisting = true, suppressAnimation = true)
            IslandLyricTextInjector.refreshCurrentContent(
                fakeView,
                includeLyricSlots = true,
                force = true,
                suppressAnimation = true,
            )
            IslandLyricTextInjector.freezeInjectedLyricProgress(fakeView, LyriconDataBridge.currentPosition)
            logFakeSlotSnapshot("prepare_post", fakeView, source)
        }
        HookLogger.d(TAG, "已准备过渡冻结 fake view: 来源=$source")
    }

    fun restoreRealIslandAfterFakeTransition(fakeView: ViewGroup, source: String) {
        if (!IslandProbeUtils.isSuperIslandEnabled()) return
        val mediaInfo = extractMediaInfoFromContentOrReal(fakeView) ?: return
        if (!isCurrentLyricIsland(mediaInfo)) return
        if (!shouldRenderInjectedIsland()) return

        val realView = callNoArgMethodResult(fakeView, "getRealView") as? ViewGroup ?: return
        IslandViewRegistry.register(realView, mediaInfo.packageName)
        val changed = IslandLyricTextInjector.restoreExistingSlotsLightweight(realView)
        IslandLyricTextInjector.refreshCurrentContent(realView)
        realView.visibility = View.VISIBLE
        val backgroundView = callNoArgMethodResult(realView, "getBackgroundView") as? View
        backgroundView?.visibility = View.VISIBLE
        if (BuildConfig.DEBUG) {
            IslandBackgroundTraceDiagnostics.event(
                "fake过渡结束恢复真实岛",
                backgroundView ?: realView,
                "source=$source 重新布局=$changed background=${if (backgroundView == null) "未找到" else "VISIBLE"}",
                background = backgroundView,
            )
        }
        realView.post {
            IslandLyricTextInjector.resumeInjectedContentMotion(
                realView,
                BaseIslandRenderer.currentPlaybackActive(),
            )
        }
        if (changed) {
            IslandHostFacade.triggerSystemRelayout(realView)
        }
        // 收起结束后原生渲染的 capsule 仍是 fake 视图：其槽位在过渡拆建后常处于空模型
        // （主行被 hug 成 0px 宽，视觉上主行消失）。这里把 fake 也补成完整内容并重新冻结，
        // 保证无论原生展示哪棵树，用户看到的都是完整双行内容。
        fakeView.post {
            if (!IslandProbeUtils.isSuperIslandEnabled()) return@post
            if (!shouldRenderInjectedIsland()) return@post
            if (!fakeView.isAttachedToWindow) return@post
            IslandLyricTextInjector.injectSlots(fakeView, reconfigureExisting = true, suppressAnimation = true)
            IslandLyricTextInjector.refreshCurrentContent(
                fakeView,
                includeLyricSlots = true,
                force = true,
                suppressAnimation = true,
            )
            IslandLyricTextInjector.freezeInjectedLyricProgress(fakeView, LyriconDataBridge.currentPosition)
            logFakeSlotSnapshot("restore_post", fakeView, source)
        }
        HookLogger.d(TAG, "fake view 过渡结束后已恢复真实岛: 来源=$source, 重新布局=$changed")
    }

    fun isCurrentLyricIsland(mediaInfo: IslandProbeUtils.MediaIslandInfo): Boolean {
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        return !lyricPkg.isNullOrEmpty() && mediaInfo.packageName == lyricPkg
    }

    fun clearOnlyWhenPackageIsDefinitelyDifferent(viewGroup: ViewGroup, mediaInfo: IslandProbeUtils.MediaIslandInfo) {
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        if (lyricPkg.isNullOrEmpty()) {
            HookLogger.d(TAG, "歌词包名暂时为空，保留已注入岛: 岛包名=${mediaInfo.packageName}")
            return
        }
        hardClearInjectedIsland(viewGroup)
    }

    fun shouldRenderInjectedIsland(): Boolean {
        return BaseIslandRenderer.shouldRenderInjectedIsland()
    }

    /** Clears the current lyric presentation but keeps the real island registered for resume. */
    fun clearInjectedIsland(viewGroup: ViewGroup, suppressRelayout: Boolean = false) {
        // Native re-posts the same island update every second while media progress advances
        // (updateBigIslandView), and this hook runs on each of those. Recalculate the host
        // width only when this call actually removed visible injected content: otherwise every
        // native update re-enters the width calculation and keeps re-laying out the island
        // content, which is the persistent flicker the user sees.
        val hadVisibleInjectedContent = IslandLyricTextInjector.hasVisibleInjectedContent(viewGroup)
        IslandHostFacade.clearInjectedViews(viewGroup)
        if (!suppressRelayout && hadVisibleInjectedContent) {
            IslandHostFacade.triggerSystemRelayout(viewGroup)
        }
    }

    /** Clears an island that is no longer a valid target and removes it from the registry. */
    fun hardClearInjectedIsland(viewGroup: ViewGroup, suppressRelayout: Boolean = false) {
        // Charging and other native islands can update the same host repeatedly. Recalculate the
        // host width only when this call actually retires media-island state or visible HLE views;
        // otherwise every native update would restart the host's width/entry animation.
        val hasVisibleInjectedContent = IslandLyricTextInjector.hasVisibleInjectedContent(viewGroup)
        val wasRegisteredMediaIsland = IslandViewRegistry.unregister(viewGroup)
        val decision = IslandHostCleanupPolicy.decide(
            wasRegisteredMediaIsland = wasRegisteredMediaIsland,
            hasVisibleInjectedContent = hasVisibleInjectedContent,
            suppressRelayout = suppressRelayout,
        )
        if (!decision.shouldClear) return

        IslandHostFacade.clearInjectedViews(viewGroup)
        if (decision.shouldRelayout) {
            IslandHostFacade.triggerSystemRelayout(viewGroup)
        }
    }

    fun restoreAdapterModule(adapter: Any?, moduleType: String?, source: String) {
        val holderRoot = IslandProbeUtils.getHolderRootView(
            IslandProbeUtils.getHolder(adapter, moduleType)
        ) ?: return

        if (IslandLyricTextInjector.restoreExistingModuleSlotLightweight(holderRoot, moduleType)) {
            IslandLyricTextInjector.refreshCurrentContent(holderRoot)
        HookLogger.d(TAG, "已轻量恢复歌词视图: 来源=$source，模块=$moduleType")
        }
    }

    fun callNoArgMethodResult(receiver: Any, name: String): Any? {
        return runCatching {
            receiver.javaClass.methods.find {
                it.name == name && it.parameterTypes.isEmpty()
            }?.invoke(receiver)
        }.getOrNull()
    }

    /** debug-only：逐槽记录 fake 视图的实际绑定状态，用于定位"主行空模型 0px"残留的具体环节。 */
    private fun logFakeSlotSnapshot(phase: String, fakeView: ViewGroup, source: String) {
        if (!BuildConfig.DEBUG) return
        fun describe(view: View?): String = when (view) {
            null -> "missing"
            is RichLyricLineView ->
                "found raw=${view.rawLine != null} mainLw=${view.main.lineWidth} secLw=${view.secondary.lineWidth} attached=${view.isAttachedToWindow}"
            is SpaceGateRichLyricLineView ->
                "found raw=${view.rawLine != null} mainLw=${view.main.lineWidth} secLw=${view.secondary.lineWidth} attached=${view.isAttachedToWindow}"
            else -> "other:${view.javaClass.simpleName}"
        }
        val left = fakeView.findViewWithTag<View>(IslandProbeUtils.LEFT_TEST_VIEW_TAG)
        val right = fakeView.findViewWithTag<View>(IslandProbeUtils.RIGHT_TEST_VIEW_TAG)
        HookLogger.d(
            TAG,
            "[FakeSlotDiag] phase=$phase 来源=$source left=(${describe(left)}) right=(${describe(right)})"
        )
    }

    fun findFieldValue(receiver: Any?, name: String): Any? {
        val target = receiver ?: return null
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val field = current.declaredFields.find { it.name == name }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }
}
