/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.lyric.view

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RichLyricHugMeasureContractTest {
    @Test
    fun `both rich line containers invalidate child measurement cache between hug passes`() {
        listOf("RichLyricLineView.kt", "SpaceGateRichLyricLineView.kt").forEach { fileName ->
            val source = source(fileName)
            val measureBlock = source.substringAfter("override fun onMeasure")
                .substringBefore("private var lastLayoutWidth")

            assertTrue("$fileName must prepare the intrinsic pass",
                measureBlock.contains("prepareHugMeasurePass(null)"))
            assertTrue("$fileName must prepare the shared-floor pass",
                measureBlock.contains("prepareHugMeasurePass(floor)"))
            assertTrue("$fileName must remeasure the main row",
                measureBlock.contains("main.forceLayout()"))
            assertTrue("$fileName must remeasure the secondary row",
                measureBlock.contains("secondary.forceLayout()"))
        }
    }

    private fun source(fileName: String): String {
        val relative = "src/main/java/com/juren233/hyperlyricsenhanced/lyric/view/$fileName"
        return listOf(File(relative), File("app/$relative")).first(File::isFile).readText()
    }
}
