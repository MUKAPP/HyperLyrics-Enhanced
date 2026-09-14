/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.lyric.view

/**
 * 岛宽计算窗口标记（超级岛注入场景专用，由岛宽度 Hook 驱动）。
 *
 * 窗口内是原生 `calculateBigIslandWidth` 的探测测量：带宽度下限（对唱固定长度/
 * 组内最宽行）的 hug 行报告固有宽度，保证岛宽计算输入不随探测几何变化；
 * 窗口外是真实布局测量：仍按可用宽度截断，保证行布局与绘制不超出实际胶囊。
 * 只影响设置了 `hugWidthFloor` 的行，普通 hug 行为不变。
 */
internal object LyricHugMeasureWindow {
    @Volatile
    @JvmStatic
    var reportIntrinsicWidth: Boolean = false
}
