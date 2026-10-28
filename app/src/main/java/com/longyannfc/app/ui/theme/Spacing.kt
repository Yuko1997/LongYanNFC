package com.longyannfc.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 4dp 基础、8dp 主节奏的间距系统。页面只允许引用这里的 token。
 */

object AppSpacing {
    val space4: Dp = 4.dp
    val space8: Dp = 8.dp
    val space12: Dp = 12.dp
    val space16: Dp = 16.dp
    val space20: Dp = 20.dp
    val space24: Dp = 24.dp
    val space32: Dp = 32.dp
    val space40: Dp = 40.dp
    val space48: Dp = 48.dp

    /** 手机页面左右边距 */
    val pageHorizontal: Dp = 20.dp

    /** 密集型工具页左右边距 */
    val denseHorizontal: Dp = 16.dp

    /** 标准主按钮高度 */
    val buttonHeight: Dp = 52.dp

    /** 标准输入控件高度 */
    val fieldHeight: Dp = 56.dp

    /** 最小可点击目标 */
    val minTouchTarget: Dp = 48.dp
}

