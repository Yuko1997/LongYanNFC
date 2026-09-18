package com.longyannfc.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.longyannfc.app.R
import com.longyannfc.app.data.ble.BleManager
import com.longyannfc.app.data.ble.MifareInfo
import com.longyannfc.app.ui.LiveCardHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToCard: () -> Unit = {},
    onNavigateToCardDump: (Long) -> Unit = {},
    onNavigateToLiveCard: (MifareInfo) -> Unit = {},
    onNavigateToEditor: () -> Unit = {},
    onNavigateToEditorDump: (Long) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToDetection: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LongYan NFC") },
                actions = {
                    IconButton(onClick = { showDeviceList = true }) {
                        Icon(
                            imageVector = if (connectionState is BleManager.ConnectionState.Connected) {
                                Icons.Default.BluetoothConnected
                            } else {
                                Icons.Default.Bluetooth
                            },
                            contentDescription = "Bluetooth"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (connectionState is BleManager.ConnectionState.Connected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (connectionState) {
                            is BleManager.ConnectionState.Connected -> Icons.Default.CheckCircle
                            is BleManager.ConnectionState.Connecting -> Icons.Default.BluetoothSearching
                            is BleManager.ConnectionState.Scanning -> Icons.Default.Search
                            else -> Icons.Default.BluetoothDisabled
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (connectionState) {
                                is BleManager.ConnectionState.Connected -> context.getString(R.string.connected)
                                is BleManager.ConnectionState.Connecting -> context.getString(R.string.connecting)
                                is BleManager.ConnectionState.Scanning -> context.getString(R.string.scanning)
                                is BleManager.ConnectionState.Disconnected -> context.getString(R.string.disconnected)
                                is BleManager.ConnectionState.Error -> context.getString(R.string.unknown)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        when (val state = connectionState) {
                            is BleManager.ConnectionState.Connected -> {
                                Text(
                                    text = "${state.name} (${state.address})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            is BleManager.ConnectionState.Error -> {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            else -> Unit
                        }
                    }
                    if (connectionState is BleManager.ConnectionState.Connected) {
                        TextButton(onClick = { viewModel.disconnect() }) {
                            Text(context.getString(R.string.disconnect))
                        }
                    } else {
                        Button(onClick = {
                            viewModel.startScan()
                            showDeviceList = true
                        }) {
                            Text(context.getString(R.string.scan))
                        }
                    }
                }
            }

            if (progress.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(progress, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.detectCard() },
                enabled = connectionState is BleManager.ConnectionState.Connected && !isDetecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDetecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(context.getString(R.string.detect_card))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToEditor,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(context.getString(R.string.editor))
                }
                OutlinedButton(
                    onClick = onNavigateToDetection,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Nfc, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(context.getString(R.string.detection))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(context.getString(R.string.history))
                }
                OutlinedButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(context.getString(R.string.settings))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            cardInfo?.let { card ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(context.getString(R.string.card_detected), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${context.getString(R.string.card_uid)}: ${card.uidHex}")
                        Text("${context.getString(R.string.card_type)}: ${card.cardType}")
                        Text("${context.getString(R.string.card_size)}: ${card.size} bytes")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(
                                onClick = { viewModel.readCard(onNavigateToEditorDump) },
                                enabled = !isReading
                            ) {
                                Text(if (isReading) context.getString(R.string.reading) else context.getString(R.string.read))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    if (lastDumpId != null) {
                                        onNavigateToCardDump(lastDumpId!!)
                                    } else if (cardInfo != null) {
                                        onNavigateToLiveCard(cardInfo!!)
                                    } else {
                                        viewModel.detectAndShowDetails { info ->
                                            onNavigateToLiveCard(info)
                                        }
                                    }
                                },
                                enabled = connectionState is BleManager.ConnectionState.Connected && !isDetecting
                            ) {
                                Text(context.getString(R.string.details))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeviceList) {
        AlertDialog(
            onDismissRequest = { showDeviceList = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(context.getString(R.string.select_device))
                    IconButton(onClick = { viewModel.refreshDeviceList() }) {
                        Icon(Icons.Default.Refresh, contentDescription = context.getString(R.string.refresh))
                    }
                }
            },
            text = {
                if (devices.isEmpty()) {
                    Text(context.getString(R.string.scanning_nearby))
                } else {
                    LazyColumn {
                        items(
                            items = devices,
                            key = { it.address }
                        ) { device ->
                            val isLastConnected = device.address == lastConnectedAddr
                            ListItem(
                                headlineContent = {
                                    Text(
                                        device.name,
                                        color = if (isLastConnected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                        fontWeight = if (isLastConnected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        "${device.address}  RSSI ${device.rssi}  ${device.type}" +
                                            if (isLastConnected) "  ✓ ${context.getString(R.string.last_connected)}" else "",
                                        color = if (isLastConnected) MaterialTheme.colorScheme.primary else Color.Unspecified
                                    )
                                },
                                trailingContent = {
                                    TextButton(onClick = {
                                        viewModel.connect(device.address)
                                        showDeviceList = false
                                    }) {
                                        Text(context.getString(R.string.connect))
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceList = false }) {
                    Text(context.getString(R.string.close))
                }
            }
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(context.getString(R.string.error)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
}
