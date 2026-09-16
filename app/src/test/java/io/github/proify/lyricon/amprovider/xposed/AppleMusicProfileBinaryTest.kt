/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 */
package io.github.proify.lyricon.amprovider.xposed

import java.io.File
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional original-APK test: no proprietary APK is checked into the repository. */
class AppleMusicProfileBinaryTest {
    @Test
    fun `all current 1599 targets and member chains match the original APK`() {
        val apk = System.getenv("HLE_APPLE_MUSIC_APK")
        assumeTrue("Set HLE_APPLE_MUSIC_APK to the original 6.5.3 (1599) base.apk", !apk.isNullOrBlank())
        assertTrue("APK does not exist", File(requireNotNull(apk)).isFile)
        val version = AppleMusicVersion("6.5.3", 1599L)
        val json = buildJsonObject {
            put("id", AppleMusicHookProfiles.profileFor(version)!!.id)
            put("hookPoints", buildJsonObject {
                AppleMusicHookPoint.entries.forEach { point ->
                    put(point.name, buildJsonArray {
                        AppleMusicHookProfiles.exactTargets(version, point).forEach { target ->
                            add(buildJsonObject {
                                put("className", target.className)
                                target.methodName?.let { put("methodName", it) }
                                target.parameterCount?.let { put("parameterCount", it) }
                                target.parameterTypeNames?.let { names ->
                                    put("parameterTypeNames", buildJsonArray {
                                        names.forEach { add(it?.let(::JsonPrimitive) ?: JsonNull) }
                                    })
                                }
                                target.returnTypeName?.let { put("returnTypeName", it) }
                                target.isStatic?.let { put("isStatic", it) }
                                put("includeSynthetic", target.includeSynthetic)
                                put("allowFirstMatch", target.allowFirstMatch)
                                put("runtimeMemberNames", buildJsonObject {
                                    target.runtimeMemberNames.forEach { (key, value) -> put(key.name, value) }
                                })
                            })
                        }
                    })
                }
            })
        }
        val export = System.getenv("HLE_APPLE_PROFILE_EXPORT")?.let(::File)
            ?: File.createTempFile("apple-1599-profile-", ".json").apply { deleteOnExit() }
        export.writeText(json.toString())
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "scripts/verify_apple_music_profile_export.py").isFile }
        val process = ProcessBuilder(
            "python3", File(root, "scripts/verify_apple_music_profile_export.py").absolutePath,
            "--apk", requireNotNull(apk), "--profiles-json", export.absolutePath,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())
        println(output)
    }
}
