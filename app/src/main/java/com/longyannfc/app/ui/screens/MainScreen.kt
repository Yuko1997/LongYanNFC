package com.longyannfc.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.longyannfc.app.R
import com.longyannfc.app.data.ble.BleDevice
import com.longyannfc.app.data.ble.BleManager
import com.longyannfc.app.data.ble.MifareInfo
import com.longyannfc.app.ui.components.AppCard
import com.longyannfc.app.ui.components.AppScaffold
import com.longyannfc.app.ui.components.EmptyState
import com.longyannfc.app.ui.components.IconBadge
import com.longyannfc.app.ui.components.InlineNotice
import com.longyannfc.app.ui.components.NoticeTone
import com.longyannfc.app.ui.components.PrimaryActionButton
import com.longyannfc.app.ui.components.QuickAction
import com.longyannfc.app.ui.components.SecondaryButton
import com.longyannfc.app.ui.components.SectionTitle
import com.longyannfc.app.ui.components.StatusPill
import com.longyannfc.app.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToCard: () -> Unit = {},
    onNavigateToCardDump: (Long) -> Unit = {},
    onNavigateToLiveCard: (MifareInfo) -> Unit = {},
    onNavigateToEditor: () -> Unit = {},
    onNavigateToEditorDump: (Long) -> Unit = {},
    onNavigateToWriteCard: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val lastConnectedAddr by viewModel.lastConnectedAddress.collectAsState()
    val isDetecting by viewModel.isDetecting.collectAsState()
    val isReading by viewModel.isReading.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val cardInfo by viewModel.cardInfo.collectAsState()
    val lastDumpId by viewModel.lastDumpId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var showDeviceList by remember { mutableStateOf(false) }

    val isConnected = connectionState is BleManager.ConnectionState.Connected
    val isBusy = isDetecting || isReading

    Box(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            title = stringResource(R.string.app_name),
            actions = {
                IconButton(onClick = { showDeviceList = true }) {
                    Icon(
                        imageVector = if (isConnected) {
                            Icons.Default.BluetoothConnected
                        } else {
                            Icons.Default.Bluetooth
                        },
                        contentDescription = stringResource(R.string.bluetooth),
                        tint = if (isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        ) { modifier ->
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.space16)
            ) {
            item {
                Spacer(modifier = Modifier.height(AppSpacing.space4))
                DeviceStatusCard(
                    connectionState = connectionState,
                    onConnect = {
                        viewModel.startScan()
                        showDeviceList = true
                    },
                    onDisconnect = { viewModel.disconnect() }
                )
            }

            if (progress.isNotBlank()) {
                item {
                    InlineNotice(
                        text = progress,
                        tone = NoticeTone.Info,
                        icon = Icons.Default.BluetoothSearching
                    )
                }
            }

            item {
                PrimaryActionButton(
                    text = stringResource(R.string.detect_card),
                    onClick = { viewModel.detectCard() },
                    enabled = isConnected && !isBusy,
                    loading = isDetecting,
                    icon = Icons.Default.Nfc
                )
            }

            cardInfo?.let { card ->
                item {
                    SectionTitle(text = stringResource(R.string.card_detected))
                }
                item {
                    AppCard {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconBadge(
                                    icon = Icons.Default.Nfc,
                                    tint = MaterialTheme.colorScheme.primary,
                                    background = MaterialTheme.colorScheme.primaryContainer
                                )
                                Spacer(modifier = Modifier.width(AppSpacing.space12))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = card.uidHex,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(AppSpacing.space4))
                                    Text(
                                        text = "${card.cardType} · ${card.size} bytes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(AppSpacing.space16))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(AppSpacing.space16))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.space8)
                            ) {
                                PrimaryActionButton(
                                    text = if (isReading) {
                                        stringResource(R.string.reading)
                                    } else {
                                        stringResource(R.string.read)
                                    },
                                    onClick = { viewModel.readCard(onNavigateToEditorDump) },
                                    enabled = !isBusy,
                                    loading = isReading,
                                    modifier = Modifier.weight(1f)
                                )
                                SecondaryButton(
                                    text = stringResource(R.string.details),
                                    onClick = {
                                        val dumpId = lastDumpId
                                        if (dumpId != null) {
                                            onNavigateToCardDump(dumpId)
                                        } else {
                                            viewModel.detectAndShowDetails { info ->
                                                onNavigateToLiveCard(info)
                                            }
                                        }
                                    },
                                    enabled = isConnected && !isBusy,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(AppSpacing.space8))
                            SecondaryButton(
                                text = stringResource(R.string.write_card),
                                onClick = onNavigateToWriteCard,
                                enabled = isConnected,
                                icon = Icons.Default.Nfc,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                SectionTitle(text = stringResource(R.string.quick_actions))
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.space8)
                ) {
                    QuickAction(
                        label = stringResource(R.string.editor),
                        icon = Icons.Default.Edit,
                        onClick = onNavigateToEditor,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAction(
                        label = stringResource(R.string.write_card),
                        icon = Icons.Default.Nfc,
                        onClick = onNavigateToWriteCard,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(AppSpacing.space16))
            }
        }
        }

        // 设备列表置顶展示：覆盖在页面内容之上，从顶部展开
        if (showDeviceList) {
            DevicePickerPanel(
                devices = devices,
                lastConnectedAddress = lastConnectedAddr,
                onRefresh = { viewModel.refreshDeviceList() },
                onConnect = { address ->
                    viewModel.connect(address)
                    showDeviceList = false
                },
                onDismiss = { showDeviceList = false }
            )
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

/** 设备连接状态卡：状态同时由图标、颜色与文字表达 */
@Composable
private fun DeviceStatusCard(
    connectionState: BleManager.ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = connectionState is BleManager.ConnectionState.Connected
    val statusText = when (connectionState) {
        is BleManager.ConnectionState.Connected -> stringResource(R.string.connected)
        is BleManager.ConnectionState.Connecting -> stringResource(R.string.connecting)
        is BleManager.ConnectionState.Scanning -> stringResource(R.string.scanning)
        is BleManager.ConnectionState.Disconnected -> stringResource(R.string.disconnected)
        is BleManager.ConnectionState.Error -> stringResource(R.string.unknown)
    }
    val tone = when (connectionState) {
        is BleManager.ConnectionState.Connected -> NoticeTone.Success
        is BleManager.ConnectionState.Connecting, is BleManager.ConnectionState.Scanning -> NoticeTone.Info
        is BleManager.ConnectionState.Error -> NoticeTone.Error
        else -> NoticeTone.Info
    }
    val icon = when (connectionState) {
        is BleManager.ConnectionState.Connected -> Icons.Default.BluetoothConnected
        is BleManager.ConnectionState.Connecting -> Icons.Default.BluetoothSearching
        is BleManager.ConnectionState.Scanning -> Icons.Default.Search
        is BleManager.ConnectionState.Error -> Icons.Default.BluetoothDisabled
        else -> Icons.Default.BluetoothDisabled
    }
    val detail = when (connectionState) {
        is BleManager.ConnectionState.Connected -> "${connectionState.name} (${connectionState.address})"
        is BleManager.ConnectionState.Error -> connectionState.message
        else -> stringResource(R.string.device_not_connected_hint)
    }

    AppCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(
                    icon = icon,
                    tint = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    background = if (isConnected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(AppSpacing.space12))
                Column(modifier = Modifier.weight(1f)) {
                    StatusPill(text = statusText, tone = tone)
                    Spacer(modifier = Modifier.height(AppSpacing.space8))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            if (isConnected) {
                SecondaryButton(
                    text = stringResource(R.string.disconnect),
                    onClick = onDisconnect,
                    icon = Icons.Default.BluetoothDisabled,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                PrimaryActionButton(
                    text = stringResource(R.string.scan),
                    onClick = onConnect,
                    icon = Icons.Default.Search
                )
            }
        }
    }
}

