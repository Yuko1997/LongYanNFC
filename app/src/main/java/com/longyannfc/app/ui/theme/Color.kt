package com.longyannfc.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Quiet Premium 语义色板。
 *
 * 规则：组件只允许通过 MaterialTheme.colorScheme 或 LocalAppColors 取色，
 * 页面中不得出现散落的硬编码颜色。
 */

// ---- 品牌强调色（单一靛蓝，保留原蓝色家族识别）----
private val BrandIndigo = Color(0xFF4C5BD4)
private val BrandIndigoDark = Color(0xFFB8C0FF)

internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9ECFF),
    onPrimaryContainer = Color(0xFF2A2F73),
    inversePrimary = BrandIndigoDark,

    // 单一强调色体系下，secondary 仅作主色的低饱和变体，不引入第二种品牌色
    secondary = Color(0xFF5C6BA8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E7F7),
    onSecondaryContainer = Color(0xFF2A2F73),

    tertiary = Color(0xFF946200),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBEBC8),
    onTertiaryContainer = Color(0xFF4A3100),

    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF171A22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A22),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF646B78),

    outline = Color(0xFFDDE1E8),
    outlineVariant = Color(0xFFEBEEF3),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2B303A),
    inverseOnSurface = Color(0xFFF1F2F6)
)

internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = BrandIndigoDark,
    onPrimary = Color(0xFF22295D),
    primaryContainer = Color(0xFF2B3158),
    onPrimaryContainer = Color(0xFFDDE1FF),
    inversePrimary = BrandIndigo,

    secondary = Color(0xFFB6BDE0),
    onSecondary = Color(0xFF212743),
    secondaryContainer = Color(0xFF383E5E),
    onSecondaryContainer = Color(0xFFDDE1FF),

    tertiary = Color(0xFFF2C879),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFE4A3),

    background = Color(0xFF101116),
    onBackground = Color(0xFFF1F2F6),
    surface = Color(0xFF171920),
    onSurface = Color(0xFFF1F2F6),
    surfaceVariant = Color(0xFF222630),
    onSurfaceVariant = Color(0xFFB4BAC6),

    outline = Color(0xFF383E4A),
    outlineVariant = Color(0xFF2A303A),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF1F2F6),
    inverseOnSurface = Color(0xFF2B303A)
)

/**
 * Material ColorScheme 未覆盖的语义色。
 * 通过 [LocalAppColors] 注入，避免页面散落颜色常量。
 */
@Immutable
data class AppColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val textTertiary: Color,
    val surfaceSecondary: Color,
    val outline: Color,
    val outlineSubtle: Color,
    /** 等宽代码/十六进制视图的底色 */
    val codeSurface: Color,
    /** 卡片类型标识：制造商块 */
    val blockManufacturer: Color,
    /** 卡片类型标识：尾块 / Key A */
    val blockTrailer: Color,
    /** 卡片类型标识：访问控制 */
    val blockAccess: Color,
    /** 卡片类型标识：Key B */
    val blockKeyB: Color,
    /** 卡片类型标识：值块 */
    val blockValue: Color
)

internal val LightAppColors = AppColors(
    success = Color(0xFF267A57),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFD8F0E3),
    onSuccessContainer = Color(0xFF0B3D29),
    warning = Color(0xFF946200),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFBEBC8),
    onWarningContainer = Color(0xFF4A3100),
    textTertiary = Color(0xFF858C99),
    surfaceSecondary = Color(0xFFEEF0F4),
    outline = Color(0xFFDDE1E8),
    outlineSubtle = Color(0xFFEBEEF3),
    codeSurface = Color(0xFFF2F4F7),
    blockManufacturer = Color(0xFF5B4BB8),
    blockTrailer = Color(0xFF1F6B3A),
    blockAccess = Color(0xFFA85B00),
    blockKeyB = Color(0xFF0A6154),
    blockValue = Color(0xFF8A6A00)
)

internal val DarkAppColors = AppColors(
    success = Color(0xFF83D6AE),
    onSuccess = Color(0xFF00391F),
    successContainer = Color(0xFF12452F),
    onSuccessContainer = Color(0xFFA0F2C9),
    warning = Color(0xFFF2C879),
    onWarning = Color(0xFF412D00),
    warningContainer = Color(0xFF5C4300),
    onWarningContainer = Color(0xFFFFE4A3),
    textTertiary = Color(0xFF8991A1),
    surfaceSecondary = Color(0xFF222630),
    outline = Color(0xFF383E4A),
    outlineSubtle = Color(0xFF2A303A),
    codeSurface = Color(0xFF1D2029),
    blockManufacturer = Color(0xFFC3B8FF),
    blockTrailer = Color(0xFF9AD6A8),
    blockAccess = Color(0xFFFFB870),
    blockKeyB = Color(0xFF6FD5C4),
    blockValue = Color(0xFFF0C64B)
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** 便捷访问：[LocalAppColors] 当前值 */
val MaterialTheme.appColors: AppColors
    @Composable
    get() = LocalAppColors.current

