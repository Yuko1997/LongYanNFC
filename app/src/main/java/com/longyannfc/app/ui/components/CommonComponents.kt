package com.longyannfc.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.longyannfc.app.ui.theme.AppShape
import com.longyannfc.app.ui.theme.AppSpacing
import com.longyannfc.app.ui.theme.appColors

/** 分区标题：用于内容分组，不加多余装饰 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/**
 * 标准内容面。靠底色差与细边框形成层次，阴影仅留给真正浮起的场景。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.space16),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(AppShape.large)
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = shape,
        color = container,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/** 键值信息行：标签定宽对齐，值自适应宽度并可换行 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    monospace: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.space12)
    ) {
        Text(
            text = label,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = if (monospace) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = valueColor
        )
    }
}

/** 设置项行：整行可点击，图标 + 标题 + 说明 + 尾部控件 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = AppSpacing.minTouchTarget)
            .padding(horizontal = AppSpacing.pageHorizontal, vertical = AppSpacing.space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(
                imageVector = leading,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
            Spacer(modifier = Modifier.width(AppSpacing.space16))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(AppSpacing.space4))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(AppSpacing.space12))
            trailing()
        }
    }
}

/** 主按钮：同一屏只应存在一个视觉最突出的操作 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    // 禁用态需要可辨识但仍可读：M3 默认禁用色在浅色主题下与按钮底色过于接近，
    // 这里改用中性容器 + 弱化前景色，保持清晰对比度。
    val disabledContainer = MaterialTheme.colorScheme.surfaceVariant
    val disabledContent = MaterialTheme.colorScheme.onSurfaceVariant
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppSpacing.buttonHeight),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(AppShape.medium),
        colors = if (enabled || loading) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.buttonColors(
                containerColor = disabledContainer,
                contentColor = disabledContent
            )
        },
        contentPadding = PaddingValues(
            horizontal = AppSpacing.space16,
            vertical = AppSpacing.space12
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                // 使用内容色，加载态下按钮已进入禁用配色，避免指示器与底色同色
                color = LocalContentColor.current
            )
            Spacer(modifier = Modifier.width(AppSpacing.space8))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(AppSpacing.space8))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 次按钮：描边样式，视觉权重低于主按钮 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    destructive: Boolean = false
) {
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = AppSpacing.minTouchTarget),
        enabled = enabled,
        shape = RoundedCornerShape(AppShape.medium),
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> MaterialTheme.colorScheme.outline
                destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(AppSpacing.space8))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 状态标签：颜色 + 图标 + 文案三重表达，不依赖颜色单独承载语义 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Info,
    icon: ImageVector? = null
) {
    val colors = MaterialTheme.appColors
    val (container, content) = when (tone) {
        NoticeTone.Info -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        NoticeTone.Success -> colors.successContainer to colors.onSuccessContainer
        NoticeTone.Warning -> colors.warningContainer to colors.onWarningContainer
        NoticeTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    val resolvedIcon = icon ?: when (tone) {
        NoticeTone.Success -> Icons.Default.CheckCircle
        NoticeTone.Warning -> Icons.Default.Warning
        NoticeTone.Error -> Icons.Default.Warning
        NoticeTone.Info -> Icons.Default.Info
    }
    Row(
        modifier = modifier
            .background(container, RoundedCornerShape(AppShape.small))
            // 高度由文字内容决定（单行 Text 不会撑到 lineHeight），
            // 上下各留 2dp，使标签与相邻标题的实际高度一致、基线协调，
            // 且在系统字体放大时不会被固定高度裁切。
            .padding(horizontal = AppSpacing.space8, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.space4)
    ) {
        Icon(
            imageVector = resolvedIcon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = content
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 图标底座：统一图标容器的底色与尺寸 */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, RoundedCornerShape(AppShape.medium)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.5f),
            tint = tint
        )
    }
}

/** 快捷入口：图标 + 文字的紧凑单元，整块可点击 */
@Composable
fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = AppSpacing.minTouchTarget)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AppShape.medium),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppSpacing.space12,
                vertical = AppSpacing.space12
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(AppSpacing.space8))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
