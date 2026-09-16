/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.provider

import android.content.Context
import com.juren233.hyperlyricsenhanced.common.PrefsBridge
import com.juren233.hyperlyricsenhanced.root.RootApplication
import java.io.File

object OfficialProviderInstaller {
    fun install(context: Context, packBytes: ByteArray): ProviderPackManifest {
        val verified = ProviderPackVerifier.verify(packBytes)
        val manifest = verified.manifest
        OfficialProviderAcquisitionLog.info(
            "插件校验通过: id=${manifest.pluginId} version=${manifest.versionCode} " +
                "name=${manifest.versionName} targets=${manifest.targetPackages.joinToString()} " +
                "packBytes=${packBytes.size}",
        )
        val remoteName = OfficialProviderCatalog.remoteFileName(
            manifest.pluginId,
            manifest.versionCode,
        )
        writeRemoteFile(context, remoteName, packBytes)

        PrefsBridge.putBoolean(
            OfficialProviderCatalog.enabledKey(manifest.pluginId),
            true,
        )
        PrefsBridge.putInt(
            OfficialProviderCatalog.installedVersionKey(manifest.pluginId),
            manifest.versionCode,
        )
        PrefsBridge.putString(
            OfficialProviderCatalog.installedVersionNameKey(manifest.pluginId),
            manifest.versionName,
        )
        manifest.targetPackages.forEach { packageName ->
            PrefsBridge.putString(
                OfficialProviderCatalog.activeFileKey(packageName),
                remoteName,
            )
        }
        OfficialProviderScopeManager.requestPluginScopes(manifest.pluginId)
        OfficialProviderAcquisitionLog.info(
            "插件已写入并启用: id=${manifest.pluginId} remoteFile=$remoteName",
        )
        return manifest
    }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        requireNotNull(OfficialProviderCatalog.definitionForId(pluginId))
        PrefsBridge.putBoolean(OfficialProviderCatalog.enabledKey(pluginId), enabled)
        if (enabled) {
            OfficialProviderScopeManager.requestPluginScopes(pluginId)
        }
    }

    fun delete(context: Context, pluginId: String) {
        val definition = requireNotNull(OfficialProviderCatalog.definitionForId(pluginId))
        val prefix = "hle-provider-$pluginId-"
        val service = RootApplication.xposedService
        if (service != null) {
            service.listRemoteFiles()
                .filter { it.startsWith(prefix) && it.endsWith(".hlp") }
                .forEach(service::deleteRemoteFile)
        } else {
            context.filesDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".hlp") }
                ?.forEach(File::delete)
        }
        PrefsBridge.putBoolean(OfficialProviderCatalog.enabledKey(pluginId), false)
        PrefsBridge.putInt(OfficialProviderCatalog.installedVersionKey(pluginId), 0)
        PrefsBridge.putString(OfficialProviderCatalog.installedVersionNameKey(pluginId), null)
        definition.targetPackages.forEach { packageName ->
            PrefsBridge.putString(OfficialProviderCatalog.activeFileKey(packageName), null)
        }
    }

    fun readInstalledManifest(
        context: Context,
        pluginId: String,
        versionCode: Int,
    ): ProviderPackManifest? {
        requireNotNull(OfficialProviderCatalog.definitionForId(pluginId))
        if (versionCode <= 0) return null
        val remoteName = OfficialProviderCatalog.remoteFileName(pluginId, versionCode)
        val packBytes = runCatching {
            val service = RootApplication.xposedService
            if (service != null) {
                if (remoteName !in service.listRemoteFiles()) return@runCatching null
                service.openRemoteFile(remoteName).use { pfd ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
                }
            } else {
                File(context.filesDir, remoteName).takeIf(File::isFile)?.readBytes()
            }
        }.onFailure { error ->
            OfficialProviderAcquisitionLog.warn(
                "已安装插件读取异常: id=$pluginId version=$versionCode file=$remoteName " +
                    "error=${OfficialProviderAcquisitionLog.describe(error)}",
            )
        }.getOrNull()
        if (packBytes == null) {
            OfficialProviderAcquisitionLog.warn(
                "已安装插件文件缺失，页面将提示修复: id=$pluginId version=$versionCode " +
                    "file=$remoteName",
            )
            return null
        }

        val manifest = runCatching {
            ProviderPackVerifier.verify(packBytes).manifest
        }.onFailure { error ->
            OfficialProviderAcquisitionLog.warn(
                "已安装插件校验失败，页面将提示修复: id=$pluginId version=$versionCode " +
                    "error=${OfficialProviderAcquisitionLog.describe(error)}",
            )
        }.getOrNull() ?: return null
        if (manifest.pluginId != pluginId || manifest.versionCode != versionCode) {
            OfficialProviderAcquisitionLog.warn(
                "已安装插件与本地记录不一致，页面将提示修复: " +
                    "记录(id=$pluginId version=$versionCode) " +
                    "实际(id=${manifest.pluginId} version=${manifest.versionCode})",
            )
            return null
        }
        return manifest
    }

    private fun writeRemoteFile(context: Context, remoteName: String, packBytes: ByteArray) {
        val service = RootApplication.xposedService
        if (service != null) {
            val existing = service.listRemoteFiles().contains(remoteName)
            if (existing) {
                val current = service.openRemoteFile(remoteName).use { pfd ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
                }
                require(current.contentEquals(packBytes)) {
                    "同一 Provider 版本对应了不同内容"
                }
                OfficialProviderAcquisitionLog.info(
                    "插件文件已存在且内容一致，跳过写入: name=$remoteName bytes=${packBytes.size}",
                )
                return
            }

            OfficialProviderAcquisitionLog.info(
                "插件写入 Remote Files: name=$remoteName bytes=${packBytes.size}",
            )
            service.openRemoteFile(remoteName).use { pfd ->
                android.os.ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { output ->
                    output.write(packBytes)
                    output.flush()
                }
            }
            val written = service.openRemoteFile(remoteName).use { pfd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
            }
            require(written.contentEquals(packBytes)) {
                "Provider Pack Remote Files 写入校验失败"
            }
            return
        }

        OfficialProviderAcquisitionLog.info(
            "插件写入应用私有目录: name=$remoteName bytes=${packBytes.size}",
        )
        val target = File(context.filesDir, remoteName)
        val temporary = File(context.filesDir, "$remoteName.tmp")
        temporary.outputStream().use { it.write(packBytes) }
        require(temporary.readBytes().contentEquals(packBytes)) {
            "Provider Pack 写入校验失败"
        }
        if (target.exists()) {
            require(target.readBytes().contentEquals(packBytes)) {
                "同一 Provider 版本对应了不同内容"
            }
            check(temporary.delete()) { "Provider Pack 临时文件清理失败" }
        } else {
            check(temporary.renameTo(target)) { "Provider Pack 原子替换失败" }
        }
    }
}
