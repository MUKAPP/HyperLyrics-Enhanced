/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.ui.page.main

/**
 * 主页面（MainPage）中实际保留的页面。
 *
 * 主页与 Apple Music 体验优化页分别由“功能入口”中的米系入口与 Apple Music 入口控制，
 * 关于页在仍存在任一功能页面时保留；三个入口全部关闭时只显示引导页。
 */
enum class MainTab {
    Home,
    AppleMusic,
    About,
    Unsupported,
}

object MainTabPolicy {

    /**
     * 主页是否保留：两个米系入口（超级岛歌词、息屏歌词）至少有一个开启。
     *
     * 主页被隐藏时，主页上的歌词设置、通知型灵动岛歌词与特殊功能会迁到设置页顶部。
     */
    fun isHomePageVisible(
        superIslandEntryEnabled: Boolean,
        aodLyricsEntryEnabled: Boolean,
    ): Boolean = superIslandEntryEnabled || aodLyricsEntryEnabled

    fun tabs(
        superIslandEntryEnabled: Boolean,
        aodLyricsEntryEnabled: Boolean,
        appleMusicEntryEnabled: Boolean,
    ): List<MainTab> {
        val showHome = isHomePageVisible(superIslandEntryEnabled, aodLyricsEntryEnabled)
        val showAppleMusic = appleMusicEntryEnabled
        if (!showHome && !showAppleMusic) {
            return listOf(MainTab.Unsupported)
        }
        return buildList {
            if (showHome) add(MainTab.Home)
            if (showAppleMusic) add(MainTab.AppleMusic)
            add(MainTab.About)
        }
    }
}
