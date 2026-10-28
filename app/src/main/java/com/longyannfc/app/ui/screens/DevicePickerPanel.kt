package com.longyannfc.app.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.longyannfc.app.R
import com.longyannfc.app.data.ble.BleDevice
import com.longyannfc.app.ui.components.EmptyState
import com.longyannfc.app.ui.components.NoticeTone
import com.longyannfc.app.ui.components.StatusPill
import com.longyannfc.app.ui.theme.AppShape
import com.longyannfc.app.ui.theme.AppSpacing

/**
 * 设备选择面板：置顶覆盖在页面之上。
 *
 * 设备列表是主界面的操作入口而非独立对话框，置顶可保持与状态卡的空间关系，
 * 用户也能一眼看到当前连接状态。
 */
@Composable
fun DevicePickerPanel(
    devices: List<BleDevice>,
    lastConnectedAddress: String,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 从顶部滑入：入场时从上方偏移淡入
    val offsetY by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "devicePanelOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .offset(y = offsetY)
                .clip(
                    RoundedCornerShape(
                        bottomStart = AppShape.extraLarge,
                        bottomEnd = AppShape.extraLarge
                    )
                )
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { }
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = AppSpacing.pageHorizontal)
                .padding(bottom = AppSpacing.space16)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.space16, bottom = AppSpacing.space8),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.select_device),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh)
                    )
                }
            }

            if (devices.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.scanning_nearby),
                    description = stringResource(R.string.no_device_found_hint),
                    icon = Icons.Default.Search
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(devices, key = { it.address }) { device ->
                        val isLastConnected = device.address == lastConnectedAddress
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = AppSpacing.space12),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.space8)
                                ) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isLastConnected) {
                                        StatusPill(
                                            text = stringResource(R.string.last_connected),
                                            tone = NoticeTone.Success
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(AppSpacing.space4))
                                Text(
                                    text = "${device.address} · ${device.rssi} dBm · ${device.type}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(AppSpacing.space12))
                            TextButton(onClick = { onConnect(device.address) }) {
                                Text(stringResource(R.string.connect))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Spacer(modifier = Modifier.height(AppSpacing.space8))
        }
    }
}
