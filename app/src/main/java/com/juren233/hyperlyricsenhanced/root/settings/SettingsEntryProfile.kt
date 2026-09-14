/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.settings

/**
 * HyperOS 设置首页入口注入的二进制标识符。
 *
 * 以下名称全部于 2026-09-12 对照原始设置 APK（Xiaomi 15 / Android 17，
 * /system_ext/priv-app/Settings/Settings.apk）核实，不得改用反编译器显示别名：
 * - `com.android.settings.MiuiSettings` 声明唯一的 `public void updateHeaderList(java.util.List)`
 *   （jadx 单类输出 828 行），列表元素就地增删且调用方持有同一引用；
 * - 列表元素类型为 `com.android.settingslib.miuisettings.preference.PreferenceActivity$Header`，
 *   含公共无参构造器（89 行）与字段 id(long)、groupId(int)、iconRes(int)、
 *   title(CharSequence)、intent(Intent)、extras(Bundle)；
 * - 锚点 id 资源名 my_device / launcher_settings / app_timer 均被 updateHeaderList 自身使用。
 */
object SettingsEntryProfile {
    const val SETTINGS_PACKAGE = "com.android.settings"
    const val MIUI_SETTINGS_CLASS = "com.android.settings.MiuiSettings"
    const val UPDATE_HEADER_LIST_METHOD = "updateHeaderList"
    const val HEADER_CLASS = "com.android.settingslib.miuisettings.preference.PreferenceActivity\$Header"

    // setIcon 声明于 MiuiSettings$HeaderAdapter；图标 ImageView 为 HeaderViewHolder.icon
    const val HEADER_ADAPTER_CLASS = "com.android.settings.MiuiSettings\$HeaderAdapter"
    const val HEADER_VIEW_HOLDER_CLASS = "com.android.settings.MiuiSettings\$HeaderViewHolder"
    const val SET_ICON_METHOD = "setIcon"
    const val ICON_VIEW_FIELD = "icon"
    const val DIMEN_HEADER_ICON_SIZE = "header_icon_size"

    const val FIELD_ID = "id"
    const val FIELD_GROUP_ID = "groupId"
    const val FIELD_ICON_RES = "iconRes"
    const val FIELD_TITLE = "title"
    const val FIELD_INTENT = "intent"
    const val FIELD_EXTRAS = "extras"

    val FIELDS = listOf(FIELD_ID, FIELD_GROUP_ID, FIELD_ICON_RES, FIELD_TITLE, FIELD_INTENT, FIELD_EXTRAS)

    // 与 HyperCeiler 相同的 extras 键：宿主按该键读取入口归属用户。
    const val EXTRA_HEADER_USER = "header_user"

    // 入口 Header 固定 id。设置自身 header id 来自 R.id（0x7f... 区间），此值刻意避开该区间。
    const val ENTRY_HEADER_ID = 0x484C594CL

    /** 入口落点的锚点 header id 资源名，按优先级排列，插在首个命中的锚点之后。 */
    val ANCHOR_HEADER_IDS = listOf("my_device", "launcher_settings", "app_timer")

    fun isUpdateHeaderListTarget(name: String, parameterTypeNames: List<String>): Boolean =
        name == UPDATE_HEADER_LIST_METHOD && parameterTypeNames == listOf("java.util.List")

    fun isSetIconTarget(name: String, parameterTypeNames: List<String>): Boolean =
        name == SET_ICON_METHOD &&
            parameterTypeNames == listOf(HEADER_VIEW_HOLDER_CLASS, HEADER_CLASS)

    /** 返回插在首个命中锚点之后的位置；未命中任何锚点时返回 -1，0 值锚点视为未解析并跳过。 */
    fun findInsertPosition(headerIds: List<Long>, anchorIds: List<Long>): Int {
        anchorIds.filter { it != 0L }.forEach { anchor ->
            val index = headerIds.indexOf(anchor)
            if (index >= 0) return index + 1
        }
        return -1
    }

    /** 与 HyperCeiler 一致的兜底位置：列表前 25 项内追加，分组继承由调用方处理。 */
    fun fallbackInsertPosition(headerCount: Int): Int = minOf(25, headerCount)

    fun groupSourceIndex(insertPosition: Int): Int = if (insertPosition > 0) insertPosition - 1 else 0
}
