package com.longyannfc.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.longyannfc.app.R
import com.longyannfc.app.data.ble.BleManager
import com.longyannfc.app.ui.components.AppCard
import com.longyannfc.app.ui.components.AppScaffold
import com.longyannfc.app.ui.components.EmptyState
import com.longyannfc.app.ui.components.IconBadge
import com.longyannfc.app.ui.components.InfoRow
import com.longyannfc.app.ui.components.InlineNotice
import com.longyannfc.app.ui.components.NoticeTone
import com.longyannfc.app.ui.components.PrimaryActionButton
import com.longyannfc.app.ui.components.SecondaryButton
import com.longyannfc.app.ui.components.SectionTitle
import com.longyannfc.app.ui.components.StatusPill
import com.longyannfc.app.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DfuScreen(
    onBack: (() -> Unit)? = null,
    viewModel: DfuViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isUpdating by viewModel.isUpdating.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                viewModel.clearError()
            } else {
                pendingBytes = bytes
                viewModel.onFirmwareSelected(uri.lastPathSegment ?: "firmware.zip", bytes)
            }
        }
    }

    AppScaffold(
        title = stringResource(R.string.dfu_title),
        onBack = onBack
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.pageHorizontal)
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.space24))
            IconBadge(
                icon = Icons.Default.SystemUpdate,
                size = 64.dp,
                tint = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            Text(
                text = stringResource(R.string.dfu_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(AppSpacing.space8))
            Text(
                text = stringResource(R.string.dfu_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(AppSpacing.space24))

            if (selectedFileName.isNotBlank()) {
                AppCard {
                    Column {
                        SectionTitle(text = stringResource(R.string.select_firmware))
                        Spacer(modifier = Modifier.height(AppSpacing.space12))
                        Text(
                            text = selectedFileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AppSpacing.space16))
            }

            errorMessage?.let { msg ->
                InlineNotice(
                    text = msg,
                    tone = NoticeTone.Error,
                    icon = Icons.Default.Warning
                )
                Spacer(modifier = Modifier.height(AppSpacing.space16))
            }

            if (isUpdating) {
                AppCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.firmware_update),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusPill(
                                text = "$progress%",
                                tone = NoticeTone.Info
                            )
                        }
                        Spacer(modifier = Modifier.height(AppSpacing.space16))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AppSpacing.space16))
            }

            PrimaryActionButton(
                text = if (selectedFileName.isBlank()) {
                    stringResource(R.string.select_firmware_btn)
                } else {
                    stringResource(R.string.firmware_update)
                },
                onClick = {
                    if (isUpdating) return@PrimaryActionButton
                    val bytes = pendingBytes
                    if (bytes == null) {
                        picker.launch(arrayOf("application/zip", "application/octet-stream"))
                    } else {
                        viewModel.startUpdate(bytes)
                    }
                },
                enabled = !isUpdating,
                loading = isUpdating,
                icon = Icons.Default.SystemUpdate
            )

            if (selectedFileName.isNotBlank() && !isUpdating) {
                Spacer(modifier = Modifier.height(AppSpacing.space8))
                SecondaryButton(
                    text = stringResource(R.string.select_firmware_btn),
                    onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.space24))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupercardScreen(
    onBack: (() -> Unit)? = null,
    viewModel: MainViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is BleManager.ConnectionState.Connected

    AppScaffold(
        title = stringResource(R.string.supercard),
        onBack = onBack
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.pageHorizontal)
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            SectionTitle(text = stringResource(R.string.supercard_info))
            AppCard {
                Column {
                    val connected = connectionState as? BleManager.ConnectionState.Connected
                    InfoRow(
                        label = stringResource(R.string.bluetooth),
                        value = if (isConnected) {
                            stringResource(R.string.connected)
                        } else {
                            stringResource(R.string.disconnected)
                        },
                        valueColor = if (isConnected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    InfoRow(
                        label = stringResource(R.string.device_info),
                        value = connected?.name ?: stringResource(R.string.disconnected)
                    )
                    InfoRow(
                        label = stringResource(R.string.firmware),
                        value = stringResource(R.string.firmware_unknown)
                    )
                    InfoRow(
                        label = stringResource(R.string.battery),
                        value = stringResource(R.string.battery_unknown)
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            InlineNotice(
                text = stringResource(R.string.feature_in_development_desc),
                tone = NoticeTone.Info,
                icon = Icons.Default.Info
            )
            Spacer(modifier = Modifier.height(AppSpacing.space24))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: (() -> Unit)? = null
) {
    AppScaffold(
        title = stringResource(R.string.help),
        onBack = onBack
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.pageHorizontal)
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            SectionTitle(text = stringResource(R.string.help_documentation))
            Spacer(modifier = Modifier.height(AppSpacing.space12))
            listOf(
                R.string.help_step_1,
                R.string.help_step_2,
                R.string.help_step_3,
                R.string.help_step_4,
                R.string.help_step_5
            ).forEachIndexed { index, resId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppSpacing.space12)
                ) {
                    IconBadge(
                        icon = Icons.Default.HelpOutline,
                        size = 32.dp,
                        tint = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.space12))
                    Text(
                        text = stringResource(resId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (index < 4) {
                    Spacer(modifier = Modifier.height(AppSpacing.space4))
                }
            }
            Spacer(modifier = Modifier.height(AppSpacing.space24))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionScreen(
    onBack: (() -> Unit)? = null
) {
    AppScaffold(
        title = stringResource(R.string.functions),
        onBack = onBack
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.pageHorizontal)
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            SectionTitle(text = stringResource(R.string.available_functions))
            Spacer(modifier = Modifier.height(AppSpacing.space12))
            AppCard {
                Column {
                    InfoRow(
                        label = stringResource(R.string.read_card),
                        value = stringResource(R.string.detect_card)
                    )
                    InfoRow(
                        label = stringResource(R.string.write_card),
                        value = stringResource(R.string.hex_editor)
                    )
                    InfoRow(
                        label = stringResource(R.string.detection),
                        value = stringResource(R.string.one_click_detection)
                    )
                    InfoRow(
                        label = stringResource(R.string.local_key_management),
                        value = stringResource(R.string.cached_keys)
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            InlineNotice(
                text = stringResource(R.string.feature_in_development_desc),
                tone = NoticeTone.Info,
                icon = Icons.Default.Info
            )
            Spacer(modifier = Modifier.height(AppSpacing.space24))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VCardScreen(
    onBack: (() -> Unit)? = null
) {
    AppScaffold(
        title = stringResource(R.string.virtual_card),
        onBack = onBack
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.pageHorizontal)
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            EmptyState(
                title = stringResource(R.string.no_virtual_card),
                description = stringResource(R.string.feature_in_development_desc),
                icon = Icons.Default.CreditCard,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VCardSimpleScreen(
    onBack: (() -> Unit)? = null
) {
    AppScaffold(
        title = stringResource(R.string.simple_virtual_card),
        onBack = onBack
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.pageHorizontal)
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            EmptyState(
                title = stringResource(R.string.feature_in_development),
                description = stringResource(R.string.feature_in_development_desc),
                icon = Icons.Default.CreditCard,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadCardScreen(
    onBack: (() -> Unit)? = null
) {
    AppScaffold(
        title = stringResource(R.string.read_card),
        onBack = onBack
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.pageHorizontal)
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            EmptyState(
                title = stringResource(R.string.feature_in_development),
                description = stringResource(R.string.feature_in_development_desc),
                icon = Icons.Default.Description,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RwCardScreen(
    onBack: (() -> Unit)? = null
) {
    AppScaffold(
        title = stringResource(R.string.read_write_card),
        onBack = onBack
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.pageHorizontal)
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.space16))
            EmptyState(
                title = stringResource(R.string.feature_in_development),
                description = stringResource(R.string.feature_in_development_desc),
                icon = Icons.Default.Build,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

