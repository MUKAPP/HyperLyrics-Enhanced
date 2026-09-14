package com.juren233.hyperlyricsenhanced.root.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定 2026-09-12 对照原始设置 APK（Xiaomi 15 / Android 17）核实的二进制标识符。
 * 名称漂移必须先重新核对原始 DEX，再更新此处的常量与断言。
 */
class SettingsEntryProfileTest {

    @Test
    fun `binary identifiers stay locked to verified dex names`() {
        assertEquals("com.android.settings", SettingsEntryProfile.SETTINGS_PACKAGE)
        assertEquals("com.android.settings.MiuiSettings", SettingsEntryProfile.MIUI_SETTINGS_CLASS)
        assertEquals("updateHeaderList", SettingsEntryProfile.UPDATE_HEADER_LIST_METHOD)
        assertEquals(
            "com.android.settingslib.miuisettings.preference.PreferenceActivity\$Header",
            SettingsEntryProfile.HEADER_CLASS,
        )
        assertEquals(
            listOf("id", "groupId", "iconRes", "title", "intent", "extras"),
            SettingsEntryProfile.FIELDS,
        )
        assertEquals("header_user", SettingsEntryProfile.EXTRA_HEADER_USER)
        assertEquals(listOf("my_device", "launcher_settings", "app_timer"), SettingsEntryProfile.ANCHOR_HEADER_IDS)
        assertEquals("com.android.settings.MiuiSettings\$HeaderAdapter", SettingsEntryProfile.HEADER_ADAPTER_CLASS)
        assertEquals("com.android.settings.MiuiSettings\$HeaderViewHolder", SettingsEntryProfile.HEADER_VIEW_HOLDER_CLASS)
        assertEquals("setIcon", SettingsEntryProfile.SET_ICON_METHOD)
        assertEquals("icon", SettingsEntryProfile.ICON_VIEW_FIELD)
        assertEquals("header_icon_size", SettingsEntryProfile.DIMEN_HEADER_ICON_SIZE)
    }

    @Test
    fun `updateHeaderList target matches verified signature`() {
        assertTrue(SettingsEntryProfile.isUpdateHeaderListTarget("updateHeaderList", listOf("java.util.List")))
        assertFalse(SettingsEntryProfile.isUpdateHeaderListTarget("updateHeaderList", listOf("java.util.List", "boolean")))
        assertFalse(SettingsEntryProfile.isUpdateHeaderListTarget("updateHeader", listOf("java.util.List")))
    }

    @Test
    fun `setIcon target matches verified signature`() {
        val holder = "com.android.settings.MiuiSettings\$HeaderViewHolder"
        val header = "com.android.settingslib.miuisettings.preference.PreferenceActivity\$Header"
        assertTrue(SettingsEntryProfile.isSetIconTarget("setIcon", listOf(holder, header)))
        assertFalse(SettingsEntryProfile.isSetIconTarget("setIcon", listOf(holder)))
        assertFalse(SettingsEntryProfile.isSetIconTarget("setIcon", listOf(header, holder)))
        assertFalse(SettingsEntryProfile.isSetIconTarget("setIconInternal", listOf(holder, header)))
    }

    @Test
    fun `entry header id stays outside host R id range`() {
        assertTrue(SettingsEntryProfile.ENTRY_HEADER_ID !in 0x7f000000L..0x7fffffffL)
    }

    @Test
    fun `insert position lands right after first matching anchor by priority`() {
        val ids = listOf(10L, 20L, 30L)
        assertEquals(2, SettingsEntryProfile.findInsertPosition(ids, listOf(99L, 20L)))
        assertEquals(1, SettingsEntryProfile.findInsertPosition(ids, listOf(0L, 10L)))
        assertEquals(-1, SettingsEntryProfile.findInsertPosition(ids, listOf(99L)))
        assertEquals(-1, SettingsEntryProfile.findInsertPosition(emptyList(), listOf(10L)))
    }

    @Test
    fun `fallback position and group source follow documented behavior`() {
        assertEquals(25, SettingsEntryProfile.fallbackInsertPosition(100))
        assertEquals(3, SettingsEntryProfile.fallbackInsertPosition(3))
        assertEquals(0, SettingsEntryProfile.fallbackInsertPosition(0))
        assertEquals(1, SettingsEntryProfile.groupSourceIndex(2))
        assertEquals(0, SettingsEntryProfile.groupSourceIndex(0))
    }
}
