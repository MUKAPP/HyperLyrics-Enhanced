/*
 * Copyright 2026 juren233
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.juren233.hyperlyricsenhanced.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles

/** 收起标题相对原字号的缩放比例，仅用于左右都有按钮的顶栏。 */
private const val COMPACT_BAR_TITLE_SCALE = 0.9f

/**
 * 顶栏左右都有按钮时使用的标题字号容器。
 *
 * miuix 顶栏的小标题固定使用 `textStyles.title3`（20sp）；这里只把该样式缩小一点点，
 * 其余文本样式保持原值。仅包裹顶栏本身，不影响页面内容。
 */
@Composable
fun CompactBarTitle(content: @Composable () -> Unit) {
    val textStyles = MiuixTheme.textStyles
    val compactTextStyles = remember(textStyles) {
        TextStyles(
            main = textStyles.main,
            paragraph = textStyles.paragraph,
            body1 = textStyles.body1,
            body2 = textStyles.body2,
            button = textStyles.button,
            footnote1 = textStyles.footnote1,
            footnote2 = textStyles.footnote2,
            headline1 = textStyles.headline1,
            headline2 = textStyles.headline2,
            subtitle = textStyles.subtitle,
            title1 = textStyles.title1,
            title2 = textStyles.title2,
            title3 = textStyles.title3.copy(
                fontSize = textStyles.title3.fontSize * COMPACT_BAR_TITLE_SCALE,
            ),
            title4 = textStyles.title4,
        )
    }
    // miuix 的 LocalTextStyles 为模块内部可见，使用公开的主题重载覆盖文本样式。
    MiuixTheme(
        colors = MiuixTheme.colorScheme,
        textStyles = compactTextStyles,
    ) {
        content()
    }
}
