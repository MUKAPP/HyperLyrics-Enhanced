/* Copyright 2026 juren233 */
package com.juren233.hyperlyricsenhanced.root.island

/**
 * Verified in original DEX, not JADX aliases:
 * SystemUI OS4.0.0.6 classes2.dex: PhoneStatusBarView.onAttachedToWindow()V.
 * workspace/apks/miui-systemui-phone-20260912/plugin-dex/classes2.dex:
 * IslandContentViewCalculationResult.<init>(IIIIIIIII)V and all getters below ()I;
 * DynamicIslandBaseContentView.getStatusBarDatePosX()I. Constructor order verified
 * against its iput instructions. Pad x remains (screenWidth - width)/2, with the
 * native date-position translation applied by the host, as in the existing unlock hook.
 */
internal object IslandDynamicLimitProfile {
    const val STATUS_BAR_CLASS = "com.android.systemui.statusbar.phone.PhoneStatusBarView"
    const val ATTACH_METHOD = "onAttachedToWindow"
    const val DATE_POSITION_GETTER = "getStatusBarDatePosX"
    const val SCREEN_WIDTH_GETTER = "getScreenWidth"
    // Original phone plugin classes2.dex: BaseHelper declares public abstract ()I;
    // PhoneHelper and PadHelper implement public instance ()I. This is the capsule
    // height; DynamicIslandContentView.height becomes the screen height in AppExpanded.
    const val ISLAND_HEIGHT_GETTER = "getIslandViewHeight"

    fun readIslandHeight(helper: Any): Int {
        val method = helper.javaClass.getMethod(ISLAND_HEIGHT_GETTER)
        require(method.returnType == Int::class.javaPrimitiveType)
        return (method.invoke(helper) as Int).also { require(it > 0) }
    }
    val RESULT_GETTERS = listOf(
        "getBigIslandViewWidth", "getBigIslandLeftWidth", "getBigIslandRightWidth",
        "getBigIslandX", "getBigIslandMarginWidth", "getBigIslandViewWidthHasSmallIsland",
        "getBigIslandLeftWidthHasSmallIsland", "getBigIslandRightWidthHasSmallIsland",
        "getBigIslandXHasSmallIsland",
    )
}
