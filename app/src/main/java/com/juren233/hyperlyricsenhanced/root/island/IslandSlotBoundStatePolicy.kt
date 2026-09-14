/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.root.island

import com.juren233.hyperlyricsenhanced.lyric.model.interfaces.IRichLyricLine

/**
 * 判定已注入歌词槽视图的"实际内容"是否与目标行脱节（视图内容丢失）。
 *
 * 换绑去重只比对目标行签名，感知不到子视图模型已被清空的状态分歧：原生过渡期
 * 会对内容树做 detach/重置，或冻结快照落在数据暂缺的瞬间；此后 rawLine 为空或
 * 主行模型为空时，签名未变的更新会被永久跳过，主行以空模型参与 hug 测量被收缩
 * 成 0px 宽——视觉上即"收回过程中主行消失、第二行还在，回正后才恢复"。
 * 此判定作为内容刷新跳过逻辑的旁路依据。
 */
internal fun isLyricViewContentLostState(
    targetLine: IRichLyricLine?,
    boundLine: IRichLyricLine?,
    boundMainLineWidth: Float,
): Boolean {
    if (targetLine == null) return false
    // 主行理应承载文本/逐字；纯第二行内容（如翻译独占行）主行为空属正常，不能按丢失处理
    if (targetLine.text.isNullOrBlank() && targetLine.words.isNullOrEmpty()) return false
    return boundLine == null || boundMainLineWidth <= 0f
}
