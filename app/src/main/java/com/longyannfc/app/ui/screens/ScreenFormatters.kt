package com.longyannfc.app.ui.screens

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 统一的时间格式化，避免各页面各自 new 格式器 */
fun formatTimestamp(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))

/** 十六进制预览（按无符号字节格式化，避免负值被打印成 FFFFFFFF） */
fun formatHexPreview(data: ByteArray, limit: Int = 8): String =
    data.take(limit).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

