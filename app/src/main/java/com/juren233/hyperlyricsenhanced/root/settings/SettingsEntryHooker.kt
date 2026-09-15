/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.UserHandle
import android.view.View
import android.widget.ImageView
import com.juren233.hyperlyricsenhanced.BuildConfig
import com.juren233.hyperlyricsenhanced.R
import com.juren233.hyperlyricsenhanced.common.UIConstants
import com.juren233.hyperlyricsenhanced.root.HookEntry
import com.juren233.hyperlyricsenhanced.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker

/**
 * 在 HyperOS 系统设置首页（MiuiSettings header 列表）注入 HyperLyrics 入口。
 *
 * 仅当"隐藏应用图标"开启时注入；"在设置中显示入口"为位置下拉（不显示/顶部/中部/底部），
 * 由 [SettingsEntryProfile.resolveEntryPosition] 解析（含旧布尔 key 迁移），"不显示"时把
 * 入口从列表移除（或不再插入）。顶部插在 my_device 等锚点之后，中部插在"系统个性化"
 * （personalize_title）之后，底部插在"更多设置"（other_advanced_settings）之前。
 * 入口 intent 直指 MainActivity：图标隐藏只停用启动器别名，
 * 主 Activity 始终可用，因此入口恰好在桌面图标消失的时间窗内可用。
 *
 * 图标不写入 iconRes、也不向设置进程挂载任何模块资源：
 * - 挂载整个模块 APK（ResourcesLoader）会让模块资源表按 id 覆盖设置自身资源，
 *   已被真机证伪（设置首页账号头像等其他图标被污染），不得回退该方向；
 * - 自适应启动图标被 setIcon 原样 inflate 会跳过设置页自身的 BitmapDrawable
 *   缩放逻辑导致超大渲染。
 * 因此 iconRes 恒为 0，图标在 HeaderAdapter.setIcon 之后按宿主 header_icon_size
 * 用模块包上下文（createPackageContext）自绘位图回填。
 */
object SettingsEntryHooker {
    private const val TAG = "SettingsEntry"

    fun install(module: HookEntry, classLoader: ClassLoader) {
        val hooker = runCatching {
            val headerClass = Class.forName(SettingsEntryProfile.HEADER_CLASS, false, classLoader)
            headerClass.getDeclaredField(SettingsEntryProfile.FIELD_ID).isAccessible = true
            headerClass.getDeclaredField(SettingsEntryProfile.FIELD_ICON_RES).isAccessible = true
            val iconViewField = Class.forName(SettingsEntryProfile.HEADER_VIEW_HOLDER_CLASS, false, classLoader)
                .getDeclaredField(SettingsEntryProfile.ICON_VIEW_FIELD)
                .apply { isAccessible = true }
            HeaderHooks(
                prefs = module.prefs,
                modulePackage = BuildConfig.APPLICATION_ID,
                headerClass = headerClass,
                idField = headerClass.getDeclaredField(SettingsEntryProfile.FIELD_ID).apply { isAccessible = true },
                iconViewField = iconViewField,
            )
        }.getOrElse { error ->
            HookLogger.e(TAG, "设置入口反射初始化失败", error)
            return
        }

        runCatching {
            val activityClass = classLoader.loadClass(SettingsEntryProfile.MIUI_SETTINGS_CLASS)
            val updateHeaderList = activityClass.declaredMethods.firstOrNull {
                SettingsEntryProfile.isUpdateHeaderListTarget(it.name, it.parameterTypes.map(Class<*>::getName))
            } ?: run {
                HookLogger.w(TAG, "未找到 updateHeaderList 目标，设置入口不可用")
                return
            }
            module.deoptimize(updateHeaderList)
            module.hook(updateHeaderList).intercept(hooker)
            HookLogger.i(TAG, "设置首页入口 Hook 已安装: updateHeaderList")
        }.onFailure { HookLogger.e(TAG, "updateHeaderList Hook 安装失败", it) }

        runCatching {
            val adapterClass = classLoader.loadClass(SettingsEntryProfile.HEADER_ADAPTER_CLASS)
            val setIcon = adapterClass.declaredMethods.firstOrNull {
                SettingsEntryProfile.isSetIconTarget(it.name, it.parameterTypes.map(Class<*>::getName))
            }
            if (setIcon == null) {
                HookLogger.w(TAG, "未找到 setIcon 目标，入口将无图标显示")
                return
            }
            module.deoptimize(setIcon)
            module.hook(setIcon).intercept(hooker)
            HookLogger.i(TAG, "设置首页入口 Hook 已安装: setIcon")
        }.onFailure { HookLogger.e(TAG, "setIcon Hook 安装失败", it) }
    }

    private class HeaderHooks(
        private val prefs: android.content.SharedPreferences,
        private val modulePackage: String,
        private val headerClass: Class<*>,
        private val idField: java.lang.reflect.Field,
        private val iconViewField: java.lang.reflect.Field,
    ) : Hooker {

        private fun entryVisible(): Boolean =
            prefs.getBoolean(UIConstants.KEY_HIDE_APP_ICON, UIConstants.DEFAULT_HIDE_APP_ICON) &&
                resolveEntryPosition() != SettingsEntryProfile.POSITION_HIDDEN

        /** 位置模式：新 int key 优先，缺失时从旧布尔 key 迁移（false=不显示，true/缺失=顶部）。 */
        private fun resolveEntryPosition(): Int = SettingsEntryProfile.resolveEntryPosition(
            positionExists = prefs.contains(UIConstants.KEY_SETTINGS_ENTRY_POSITION),
            positionValue = prefs.getInt(UIConstants.KEY_SETTINGS_ENTRY_POSITION, 0),
            legacyShowEntryExists = prefs.contains(UIConstants.KEY_SHOW_SETTINGS_ENTRY),
            legacyShowEntry = prefs.getBoolean(UIConstants.KEY_SHOW_SETTINGS_ENTRY, UIConstants.DEFAULT_SHOW_SETTINGS_ENTRY),
        )

        private fun resolveAnchorId(activity: Activity, name: String): Long =
            activity.resources.getIdentifier(name, "id", SettingsEntryProfile.SETTINGS_PACKAGE).toLong()

        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
                if (headerClass.isInstance(chain.args.getOrNull(1))) {
                    applyEntryIcon(chain)
                } else {
                    applyEntryList(chain)
                }
            }.onFailure { HookLogger.e(TAG, "设置入口更新失败", it) }
            return result
        }

        /** updateHeaderList 之后：先移除既有入口，再按位置模式重新插入或保持移除。 */
        private fun applyEntryList(chain: Chain) {
            val activity = chain.thisObject as? Activity ?: return
            val headers = chain.args.firstOrNull() as? MutableList<Any> ?: return

            val ids = ArrayList<Long>(headers.size)
            headers.forEach { ids.add(idField.getLong(it)) }
            val existingIndex = ids.indexOf(SettingsEntryProfile.ENTRY_HEADER_ID)
            if (existingIndex >= 0) headers.removeAt(existingIndex)

            val mode = resolveEntryPosition()
            if (!entryVisible()) return

            val header = createHeader(activity) ?: return
            val placement = when (mode) {
                SettingsEntryProfile.POSITION_BOTTOM -> bottomPlacement(activity, ids)
                SettingsEntryProfile.POSITION_MIDDLE -> Placement(middleInsertPosition(activity, ids))
                else -> Placement(topInsertPosition(activity, ids))
            }
            if (headers.isNotEmpty()) {
                setGroupField(
                    activity,
                    header,
                    headers[SettingsEntryProfile.groupSourceIndex(placement.position, placement.insertBeforeAnchor)],
                )
            }
            headers.add(placement.position, header)
            HookLogger.i(TAG, "设置首页入口已注入: mode=$mode position=${placement.position}")
        }

        private data class Placement(val position: Int, val insertBeforeAnchor: Boolean = false)

        /** 顶部锚点命中位置；完全未命中时使用 HyperCeiler 同款前 25 项兜底。 */
        private fun topInsertPosition(activity: Activity, ids: List<Long>): Int {
            val anchorIds = SettingsEntryProfile.ANCHOR_HEADER_IDS.map { resolveAnchorId(activity, it) }
            return SettingsEntryProfile.findInsertPosition(ids, anchorIds)
                .takeIf { it >= 0 }
                ?: SettingsEntryProfile.fallbackInsertPosition(ids.size)
        }

        /** 中部锚点（"系统个性化"之后）未命中时回落到顶部锚点。 */
        private fun middleInsertPosition(activity: Activity, ids: List<Long>): Int {
            val anchorIds = SettingsEntryProfile.MIDDLE_ANCHOR_HEADER_IDS.map { resolveAnchorId(activity, it) }
            return SettingsEntryProfile.findInsertPosition(ids, anchorIds)
                .takeIf { it >= 0 }
                ?: topInsertPosition(activity, ids)
        }

        /** 底部锚点（"更多设置"之前）未命中时兜底追加到列表末尾。 */
        private fun bottomPlacement(activity: Activity, ids: List<Long>): Placement {
            val anchorIds = SettingsEntryProfile.BOTTOM_ANCHOR_HEADER_IDS.map { resolveAnchorId(activity, it) }
            val position = SettingsEntryProfile.findInsertBeforePosition(ids, anchorIds)
            return if (position >= 0) Placement(position, insertBeforeAnchor = true) else Placement(ids.size)
        }

        private fun setGroupField(activity: Activity, header: Any, adjacent: Any) {
            runCatching {
                val groupField = headerClass.getDeclaredField(SettingsEntryProfile.FIELD_GROUP_ID)
                    .apply { isAccessible = true }
                groupField.setInt(header, groupField.getInt(adjacent))
            }.onFailure { HookLogger.w(TAG, "继承分组失败: ${it.message}") }
        }

        private fun createHeader(activity: Activity): Any? {
            val moduleContext = runCatching {
                activity.createPackageContext(modulePackage, Context.CONTEXT_IGNORE_SECURITY)
            }.getOrNull() ?: run {
                HookLogger.w(TAG, "无法创建模块上下文，跳过设置入口")
                return null
            }
            return headerClass.getDeclaredConstructor().newInstance().also { header ->
                idField.setLong(header, SettingsEntryProfile.ENTRY_HEADER_ID)
                // iconRes 保持 0：宿主渲染不可依赖模块资源 id（见类注释），图标由 applyEntryIcon 回填
                val titleField = headerClass.getDeclaredField(SettingsEntryProfile.FIELD_TITLE)
                    .apply { isAccessible = true }
                titleField.set(header, moduleContext.getString(R.string.app_name))
                val intentField = headerClass.getDeclaredField(SettingsEntryProfile.FIELD_INTENT)
                    .apply { isAccessible = true }
                intentField.set(header, Intent().setClassName(modulePackage, "$modulePackage.ui.MainActivity"))
                val extrasField = headerClass.getDeclaredField(SettingsEntryProfile.FIELD_EXTRAS)
                    .apply { isAccessible = true }
                extrasField.set(header, Bundle().apply {
                    // getUserHandleForUid(0) = 用户 0 句柄（SDK 37 公开 API；SYSTEM 常量与 int 构造器均已隐藏）
                    putParcelableArrayList(
                        SettingsEntryProfile.EXTRA_HEADER_USER,
                        arrayListOf(UserHandle.getUserHandleForUid(0)),
                    )
                })
            }
        }

        /** setIcon 之后：仅对入口 Header 用模块包上下文自绘位图，尺寸对齐宿主 header_icon_size。 */
        private fun applyEntryIcon(chain: Chain) {
            val header = chain.args.getOrNull(1) ?: return
            if (idField.getLong(header) != SettingsEntryProfile.ENTRY_HEADER_ID) return
            val holder = chain.args.getOrNull(0) ?: return
            val iconView = iconViewField.get(holder) as? ImageView ?: return
            val moduleContext = runCatching {
                iconView.context.createPackageContext(modulePackage, Context.CONTEXT_IGNORE_SECURITY)
            }.getOrNull() ?: return
            val drawable = runCatching {
                moduleContext.packageManager.getApplicationIcon(modulePackage)
            }.getOrNull() ?: return

            val size = resolveHeaderIconSize(iconView)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            iconView.visibility = View.VISIBLE
            iconView.setImageBitmap(bitmap)
        }

        private fun resolveHeaderIconSize(iconView: ImageView): Int {
            val resources = iconView.resources
            val dimenId = resources.getIdentifier(
                SettingsEntryProfile.DIMEN_HEADER_ICON_SIZE,
                "dimen",
                SettingsEntryProfile.SETTINGS_PACKAGE,
            )
            if (dimenId != 0) {
                runCatching { return resources.getDimensionPixelSize(dimenId) }
            }
            return (56 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        }
    }
}
