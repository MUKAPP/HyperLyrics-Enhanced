/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.common

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * 米系“获取应用列表”权限（HyperOS/MIUI 对 [android.Manifest.permission.QUERY_ALL_PACKAGES]
 * 的额外门禁）的解析与授予状态判定。
 */
object InstalledAppsPermission {

    /** HyperOS/MIUI 的“获取应用列表”运行时权限名。 */
    const val PERMISSION = "com.android.permission.GET_INSTALLED_APPS"

    /** 该权限由米系安全组件（com.lbe.security.miui）声明；非米系系统上不存在。 */
    private const val MIUI_SECURITY_PACKAGE = "com.lbe.security.miui"

    /** 解析米系“获取应用列表”运行时权限；非米系系统上不存在该权限，返回 null 表示无需授予。 */
    fun resolve(context: Context): String? =
        runCatching {
            context.packageManager
                .getPermissionInfo(PERMISSION, 0)
                .takeIf { it.packageName == MIUI_SECURITY_PACKAGE }
                ?.name
        }.getOrNull()

    /** 权限是否已生效；[permission] 为 null（非米系系统）时视为已授予。 */
    fun isGranted(context: Context, permission: String?): Boolean =
        permission == null ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
