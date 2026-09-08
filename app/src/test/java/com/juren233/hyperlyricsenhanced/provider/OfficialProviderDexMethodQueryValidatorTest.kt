/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.provider

import kotlin.jvm.internal.DefaultConstructorMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialProviderDexMethodQueryValidatorTest {
    @Test
    fun `keeps method result hook provider ABI`() {
        assertNotNull(
            OfficialProviderHost::class.java.getDeclaredMethod(
                "hookMethodResult",
                OfficialProviderMethodTarget::class.java,
                OfficialProviderMethodResultCallback::class.java,
            ),
        )
        assertNotNull(
            OfficialProviderMethodResultCallback::class.java.getDeclaredMethod(
                "onMethodReturned",
                Any::class.java,
                Array<Any?>::class.java,
                Any::class.java,
            ),
        )
    }

    @Test
    fun `keeps exact constructor hook provider ABI`() {
        assertNotNull(
            OfficialProviderHost::class.java.getDeclaredMethod(
                "hookAfterConstructor",
                OfficialProviderConstructorTarget::class.java,
                OfficialProviderConstructorCallback::class.java,
            ),
        )
        assertNotNull(
            OfficialProviderConstructorTarget::class.java.getDeclaredConstructor(
                String::class.java,
                List::class.java,
            ),
        )
        assertNotNull(
            OfficialProviderConstructorCallback::class.java.getDeclaredMethod(
                "onConstructed",
                Any::class.java,
                Array<Any?>::class.java,
            ),
        )
    }

    @Test
    fun `accepts exact declaring class without required strings`() {
        OfficialProviderDexMethodQueryValidator.validate(
            OfficialProviderDexMethodQuery(
                cacheKey = "kugou-lyric-manager-path-loader-v1",
                declaringClassName = "com.kugou.framework.lyric.LyricManager",
                requiredStrings = emptyList(),
                parameterTypeNames = listOf("java.lang.String"),
                returnTypeName = "com.kugou.framework.lyric.k",
                isStatic = false,
            )
        )
    }

    @Test
    fun `rejects query without class or required strings`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "unanchored-query",
                    requiredStrings = emptyList(),
                )
            )
        }
    }

    @Test
    fun `rejects blank required string`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "blank-string-query",
                    requiredStrings = listOf(" "),
                )
            )
        }
    }

    @Test
    fun `accepts an ordered query type reference as the anchor`() {
        OfficialProviderDexMethodQueryValidator.validate(
            OfficialProviderDexMethodQuery(
                cacheKey = "next-node",
                declaringClassReference = OfficialProviderDexTypeReference(
                    queryCacheKey = "previous-node",
                    source = OfficialProviderDexTypeSource.RETURN_TYPE,
                ),
                parameterTypeNames = emptyList(),
            ),
        )
    }

    @Test
    fun `rejects invalid parameter type reference`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "invalid-parameter-reference",
                    declaringClassName = "example.Owner",
                    parameterTypeNames = emptyList(),
                    parameterTypeReferences = mapOf(
                        0 to OfficialProviderDexTypeReference(
                            queryCacheKey = "previous-node",
                            source = OfficialProviderDexTypeSource.RETURN_TYPE,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `accepts caller method name as semantic anchor`() {
        OfficialProviderDexMethodQueryValidator.validate(
            OfficialProviderDexMethodQuery(
                cacheKey = "kugou-lite-next-media-v2",
                requiredCallerMethodNames = listOf("getNextMedia"),
                parameterTypeNames = emptyList(),
                returnTypeName = "com.kugou.common.player.manager.IMedia",
                isStatic = false,
            ),
        )
    }

    @Test
    fun `rejects blank caller method name`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "blank-caller",
                    requiredCallerMethodNames = listOf(" "),
                ),
            )
        }
    }

    @Test
    fun `rejects preferred target with caller semantic constraint`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "unsafe-preferred-caller",
                    preferredTarget = OfficialProviderMethodTarget(
                        className = "example.QueuePlayerManager",
                        methodName = "P0",
                        returnTypeName = "example.IMedia",
                        isStatic = false,
                    ),
                    requiredCallerMethodNames = listOf("getNextMedia"),
                ),
            )
        }
    }

    @Test
    fun `accepts forbidden invoke descriptor as semantic anchor`() {
        OfficialProviderDexMethodQueryValidator.validate(
            OfficialProviderDexMethodQuery(
                cacheKey = "qqmusic-hd-current-song-v4",
                forbiddenInvokedMethodDescriptors = listOf(
                    "Lcom/tencent/qqmusic/openapisdk/model/SongInfo;->getSongId()J",
                ),
                parameterTypeNames = emptyList(),
                returnTypeName = "com.tencent.qqmusic.openapisdk.model.SongInfo",
                isStatic = false,
            ),
        )
    }

    @Test
    fun `rejects blank forbidden invoke descriptor`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "blank-forbidden-invoke",
                    forbiddenInvokedMethodDescriptors = listOf(" "),
                ),
            )
        }
    }

    @Test
    fun `rejects preferred target with forbidden invoke constraint`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "unsafe-preferred-forbidden-invoke",
                    preferredTarget = OfficialProviderMethodTarget(
                        className = "example.MusicPlayerHelper",
                        methodName = "l0",
                        returnTypeName = "example.SongInfo",
                        isStatic = false,
                    ),
                    forbiddenInvokedMethodDescriptors = listOf(
                        "Lexample/SongInfo;->getSongId()J",
                    ),
                ),
            )
        }
    }

    @Test
    fun `keeps the pre-reference provider pack constructors`() {
        val oldParameters = arrayOf(
            String::class.java,
            OfficialProviderMethodTarget::class.java,
            String::class.java,
            String::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaObjectType,
        )
        assertNotNull(
            OfficialProviderDexMethodQuery::class.java.getDeclaredConstructor(*oldParameters),
        )
        assertNotNull(
            OfficialProviderDexMethodQuery::class.java.getDeclaredConstructor(
                *oldParameters,
                Int::class.javaPrimitiveType!!,
                DefaultConstructorMarker::class.java,
            ),
        )
    }

    @Test
    fun `keeps the plugin api v3 provider pack constructors`() {
        val v3Parameters = arrayOf(
            String::class.java,
            OfficialProviderMethodTarget::class.java,
            String::class.java,
            String::class.java,
            OfficialProviderDexTypeReference::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            Map::class.java,
            String::class.java,
            String::class.java,
            OfficialProviderDexTypeReference::class.java,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaObjectType,
        )
        assertNotNull(
            OfficialProviderDexMethodQuery::class.java.getDeclaredConstructor(*v3Parameters),
        )
        assertNotNull(
            OfficialProviderDexMethodQuery::class.java.getDeclaredConstructor(
                *v3Parameters,
                Int::class.javaPrimitiveType!!,
                DefaultConstructorMarker::class.java,
            ),
        )
    }

    @Test
    fun `keeps the caller constraint provider pack constructors`() {
        val callerConstraintParameters = arrayOf(
            String::class.java,
            OfficialProviderMethodTarget::class.java,
            String::class.java,
            String::class.java,
            OfficialProviderDexTypeReference::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            Map::class.java,
            String::class.java,
            String::class.java,
            OfficialProviderDexTypeReference::class.java,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaObjectType,
            List::class.java,
        )
        assertNotNull(
            OfficialProviderDexMethodQuery::class.java.getDeclaredConstructor(
                *callerConstraintParameters,
            ),
        )
        assertNotNull(
            OfficialProviderDexMethodQuery::class.java.getDeclaredConstructor(
                *callerConstraintParameters,
                Int::class.javaPrimitiveType!!,
                DefaultConstructorMarker::class.java,
            ),
        )
    }

    @Test
    fun `keeps the frozen primary query constructors before annotation anchors`() {
        // 注解锚扩展前已分发的全部 Pack 都按这个签名调用主构造器。
        val frozenParameters = arrayOf(
            String::class.java,
            OfficialProviderMethodTarget::class.java,
            String::class.java,
            String::class.java,
            OfficialProviderDexTypeReference::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            Map::class.java,
            String::class.java,
            String::class.java,
            OfficialProviderDexTypeReference::class.java,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaObjectType,
            List::class.java,
            List::class.java,
        )
        assertNotNull(
            OfficialProviderDexMethodQuery::class.java.getDeclaredConstructor(
                *frozenParameters,
            ),
        )
        assertNotNull(
            OfficialProviderDexMethodQuery::class.java.getDeclaredConstructor(
                *frozenParameters,
                Int::class.javaPrimitiveType!!,
                DefaultConstructorMarker::class.java,
            ),
        )
    }

    @Test
    fun `keeps the pre first parameter constructor target ABI`() {
        assertNotNull(
            OfficialProviderConstructorTarget::class.java.getDeclaredConstructor(
                String::class.java,
                List::class.java,
                Int::class.javaPrimitiveType!!,
                DefaultConstructorMarker::class.java,
            ),
        )
    }

    @Test
    fun `accepts annotation value as semantic anchor`() {
        OfficialProviderDexMethodQueryValidator.validate(
            OfficialProviderDexMethodQuery(
                cacheKey = "spotify-lyrics-service-v2",
                requiredMethodAnnotation = OfficialProviderMethodAnnotationConstraint(
                    elementValue = "color-lyrics/v2/track/{trackId}",
                ),
            ),
        )
    }

    @Test
    fun `accepts declaring class field reference as anchor`() {
        OfficialProviderDexMethodQueryValidator.validate(
            OfficialProviderDexMethodQuery(
                cacheKey = "spotify-lyrics-client-v2",
                declaringClassFieldReferences = listOf(
                    OfficialProviderDexTypeReference(
                        queryCacheKey = "spotify-lyrics-service-v2",
                        source = OfficialProviderDexTypeSource.DECLARING_CLASS,
                    ),
                ),
                parameterTypeNames = listOf("java.lang.String", "java.lang.String"),
                returnTypeName = "io.reactivex.rxjava3.core.Single",
            ),
        )
    }

    @Test
    fun `accepts builder built annotation query`() {
        OfficialProviderDexMethodQueryValidator.validate(
            OfficialProviderDexMethodQueryBuilder("builder-annotation-query").apply {
                requiredMethodAnnotation = OfficialProviderMethodAnnotationConstraint(
                    annotationTypeName = "retrofit2.http.GET",
                    elementName = "value",
                    elementValue = "color-lyrics/v3/track/{trackId}",
                )
                isStatic = false
            }.build(),
        )
    }

    @Test
    fun `rejects blank annotation element value`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "blank-annotation-value",
                    requiredMethodAnnotation = OfficialProviderMethodAnnotationConstraint(
                        elementValue = " ",
                    ),
                ),
            )
        }
    }

    @Test
    fun `rejects preferred target with annotation constraint`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "unsafe-preferred-annotation",
                    preferredTarget = OfficialProviderMethodTarget(
                        className = "p.vja0",
                        methodName = "b",
                        parameterTypeNames = listOf(
                            "java.lang.String",
                            "java.lang.String",
                        ),
                        returnTypeName = "io.reactivex.rxjava3.core.Single",
                        isStatic = false,
                    ),
                    requiredMethodAnnotation = OfficialProviderMethodAnnotationConstraint(
                        elementValue = "color-lyrics/v3/track/{trackId}",
                    ),
                ),
            )
        }
    }

    @Test
    fun `rejects field type constraint with declaring class name`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "conflicting-class-and-fields",
                    declaringClassName = "p.gea0",
                    declaringClassFieldTypeNames = listOf("p.h7a0"),
                ),
            )
        }
    }

    @Test
    fun `rejects blank declaring class field type`() {
        assertThrows(IllegalArgumentException::class.java) {
            OfficialProviderDexMethodQueryValidator.validate(
                OfficialProviderDexMethodQuery(
                    cacheKey = "blank-field-type",
                    declaringClassFieldTypeNames = listOf(" "),
                ),
            )
        }
    }

    @Test
    fun `builder mirrors every query field`() {
        val reference = OfficialProviderDexTypeReference(
            queryCacheKey = "service",
            source = OfficialProviderDexTypeSource.DECLARING_CLASS,
        )
        val annotation = OfficialProviderMethodAnnotationConstraint(
            annotationTypeName = "retrofit2.http.GET",
            elementName = "value",
            elementValue = "color-lyrics/v2/track/{trackId}",
        )
        val built = OfficialProviderDexMethodQueryBuilder("mirror").apply {
            preferredTarget = OfficialProviderMethodTarget(
                className = "p.am80",
                methodName = "b",
                returnTypeName = "io.reactivex.rxjava3.core.Single",
                isStatic = false,
            )
            declaringClassNamePrefix = "p."
            requiredStrings = listOf("anchor")
            requiredInvokedMethodDescriptors = listOf("Lp/xl80;->a()V")
            requiredInvokedMethodNames = listOf("prevTracks")
            parameterTypeNames = listOf("java.lang.String")
            parameterTypeReferences = mapOf(0 to reference)
            returnTypeNamePrefix = "io.reactivex."
            returnTypeMatchesDeclaringClass = true
            isStatic = false
            requiredCallerMethodNames = listOf("caller")
            forbiddenInvokedMethodDescriptors = listOf("Lp/x;->f()V")
            requiredMethodAnnotation = annotation
            declaringClassFieldTypeNames = listOf("p.sja0")
            declaringClassFieldReferences = listOf(reference)
        }.build()
        assertEquals("mirror", built.cacheKey)
        assertEquals("p.am80", built.preferredTarget?.className)
        assertEquals("p.", built.declaringClassNamePrefix)
        assertEquals(listOf("anchor"), built.requiredStrings)
        assertEquals(listOf("Lp/xl80;->a()V"), built.requiredInvokedMethodDescriptors)
        assertEquals(listOf("prevTracks"), built.requiredInvokedMethodNames)
        assertEquals(listOf("java.lang.String"), built.parameterTypeNames)
        assertEquals(mapOf(0 to reference), built.parameterTypeReferences)
        assertEquals("io.reactivex.", built.returnTypeNamePrefix)
        assertTrue(built.returnTypeMatchesDeclaringClass)
        assertEquals(false, built.isStatic)
        assertEquals(listOf("caller"), built.requiredCallerMethodNames)
        assertEquals(listOf("Lp/x;->f()V"), built.forbiddenInvokedMethodDescriptors)
        assertEquals(annotation, built.requiredMethodAnnotation)
        assertEquals(listOf("p.sja0"), built.declaringClassFieldTypeNames)
        assertEquals(listOf(reference), built.declaringClassFieldReferences)
    }
}
