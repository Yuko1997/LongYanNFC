package com.longyannfc.app.utils

import androidx.compose.runtime.mutableStateOf
import com.longyannfc.app.BuildConfig

/**
 * 调试开关。
 *
 * 只存在于内存（进程级），不写入 SharedPreferences、不跨启动保留，
 * 且仅 Debug 构建允许开启，Release 构建始终关闭。
 */
object DebugFlags {

    private val verboseLoggingState = mutableStateOf(BuildConfig.DEBUG)

    /** 是否输出包含卡片数据、密钥等敏感内容的详细日志 */
    val verboseLogging: Boolean get() = verboseLoggingState.value

    /** Release 构建不可开启调试开关 */
    val canToggle: Boolean get() = BuildConfig.DEBUG

    fun setVerboseLogging(enabled: Boolean) {
        if (!canToggle) return
        verboseLoggingState.value = enabled
    }
}
