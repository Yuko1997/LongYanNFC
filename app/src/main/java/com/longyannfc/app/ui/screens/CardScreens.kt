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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.longyannfc.app.data.ble.MifareInfo
import com.longyannfc.app.utils.CardTagInfo
import com.longyannfc.app.ui.components.AppCard
import com.longyannfc.app.ui.components.AppScaffold
import com.longyannfc.app.ui.components.EmptyState
import com.longyannfc.app.ui.components.InfoRow
import com.longyannfc.app.ui.components.InlineNotice
import com.longyannfc.app.ui.components.NoticeTone
import com.longyannfc.app.ui.components.PrimaryActionButton
import com.longyannfc.app.ui.components.SecondaryButton
import com.longyannfc.app.ui.components.SectionTitle
import com.longyannfc.app.ui.components.StatusPill
import com.longyannfc.app.ui.theme.AppSpacing
import com.longyannfc.app.ui.theme.MonospaceTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(
    dumpId: Long,
    liveInfo: MifareInfo? = null,
    onBack: (() -> Unit)? = null,
    viewModel: CardDumpViewModel = hiltViewModel()
) {
    val dump by if (dumpId > 0) {
        viewModel.observeDump(dumpId).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    val hasLiveData = liveInfo != null

    AppScaffold(
        title = stringResource(R.string.card_info_title),
        onBack = onBack
    ) { modifier ->
        if (dump == null && !hasLiveData) {
            EmptyState(
                title = stringResource(R.string.no_card_data),
                description = stringResource(R.string.read_full_data_hint),
                icon = Icons.Default.Inbox,
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.pageHorizontal)
            )
        } else if (hasLiveData && dump == null) {
            val tagInfo = remember(liveInfo) {
                CardTagInfo.fromLiveInfo(
                    uid = liveInfo!!.uidHex,
                    atqa = liveInfo.atqa,
                    sak = liveInfo.sak,
                    size = liveInfo.size,
                    cardType = liveInfo.cardType.name,
                    block0 = liveInfo.manufacturerBlock
                )
            }
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.space16)
            ) {
                item {
                    Spacer(modifier = Modifier.height(AppSpacing.space4))
                    SectionTitle(text = stringResource(R.string.live_card_title))
                }
                item {
                    AppCard {
                        Column {
                            tagInfo.rows.forEach { (label, value) ->
                                InfoRow(label = label, value = value, monospace = true)
                            }
                        }
                    }
                }
                tagInfo.warnings.forEach { warning ->
                    item {
                        InlineNotice(
                            text = warning,
                            tone = NoticeTone.Warning,
                            icon = Icons.Default.Warning
                        )
                    }
                }
                item {
                    InlineNotice(
                        text = stringResource(R.string.read_full_data_hint),
                        tone = NoticeTone.Info,
                        icon = Icons.Default.Nfc
                    )
                }
                item { Spacer(modifier = Modifier.height(AppSpacing.space16)) }
            }
        } else {
            val currentDump = dump!!
            val tagInfo = remember(currentDump.id, currentDump.updatedAt) {
                CardTagInfo.fromDump(
                    currentDump.uid,
                    currentDump.cardType,
                    currentDump.size,
                    currentDump.data
                )
            }
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.space16)
            ) {
                item {
                    Spacer(modifier = Modifier.height(AppSpacing.space4))
                    AppCard {
                        Column {
                            Text(
                                text = currentDump.name.ifBlank { currentDump.uid },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.space12))
                            InfoRow(
                                label = stringResource(R.string.card_uid),
                                value = currentDump.uid,
                                monospace = true
                            )
                            InfoRow(label = stringResource(R.string.card_type), value = currentDump.cardType)
                            InfoRow(
                                label = stringResource(R.string.card_size),
                                value = "${currentDump.data.size} bytes"
                            )
                            InfoRow(
                                label = stringResource(R.string.dump_fail_blocks),
                                value = currentDump.failBlocks.ifBlank {
                                    stringResource(R.string.none)
                                }
                            )
                            InfoRow(
                                label = stringResource(R.string.dump_updated_at),
                                value = formatTimestamp(currentDump.updatedAt)
                            )
                        }
                    }
                }

                item {
                    SectionTitle(text = stringResource(R.string.tag_info_title))
                }
                item {
                    AppCard {
                        Column {
                            tagInfo.rows.forEach { (label, value) ->
                                InfoRow(label = label, value = value, monospace = true)
                            }
                        }
                    }
                }
                tagInfo.warnings.forEach { warning ->
                    item {
                        InlineNotice(
                            text = warning,
                            tone = NoticeTone.Warning,
                            icon = Icons.Default.Warning
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(AppSpacing.space16)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardHistoryScreen(
    onOpen: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: CardDumpViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val dumps by viewModel.dumps.collectAsState()
    var deleteTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var exportTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val target = exportTarget ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            viewModel.exportDump(context, target.first, uri)
        }
        exportTarget = null
    }

    AppScaffold(
        title = stringResource(R.string.history_title),
        onBack = onBack
    ) { modifier ->
        if (dumps.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.history_empty_title),
                description = stringResource(R.string.history_empty_desc),
                icon = Icons.Default.Inbox,
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.pageHorizontal)
            )
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.space12)
            ) {
                item { Spacer(modifier = Modifier.height(AppSpacing.space4)) }
                items(dumps, key = { it.id }) { dump ->
                    AppCard {
                        Column {
                            Text(
                                text = dump.name.ifBlank { dump.uid },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.space4))
                            Text(
                                text = "${dump.cardType} · ${dump.data.size} bytes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatTimestamp(dump.updatedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.space8))
                            Text(
                                text = formatHexPreview(dump.data),
                                style = MonospaceTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.space16))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(AppSpacing.space8))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.space8)
                            ) {
                                SecondaryButton(
                                    text = stringResource(R.string.edit),
                                    onClick = { onOpen(dump.id) },
                                    icon = Icons.Default.Edit,
                                    modifier = Modifier.weight(1f)
                                )
                                SecondaryButton(
                                    text = stringResource(R.string.export),
                                    onClick = {
                                        val fileName = "${dump.uid}_${dump.cardType}.bin"
                                        exportTarget = dump.id to fileName
                                        exportLauncher.launch(fileName)
                                    },
                                    icon = Icons.Default.Download,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        deleteTarget = dump.id to (dump.name.ifBlank { dump.uid })
                                    },
                                    modifier = Modifier.height(AppSpacing.minTouchTarget)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(AppSpacing.space16)) }
            }
        }
    }

    deleteTarget?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_dump_confirm, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(id)
                        deleteTarget = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteCardScreen(
    onNavigateToEditor: (Long) -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: CardDumpViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val dumps by viewModel.dumps.collectAsState()
    val isWriting by viewModel.isWriting.collectAsState()
    val writeProgress by viewModel.writeProgress.collectAsState()
    val writeResult by viewModel.writeResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var selectedDumpId by remember { mutableStateOf<Long?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val selectedDump = remember(selectedDumpId, dumps) {
        selectedDumpId?.let { id -> dumps.find { it.id == id } }
    }

    AppScaffold(
        title = stringResource(R.string.write_card),
        onBack = onBack
    ) { modifier ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.space12)
        ) {
            item {
                Spacer(modifier = Modifier.height(AppSpacing.space4))
                SectionTitle(text = stringResource(R.string.write_card_select_source))
            }

            if (dumps.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.write_card_no_data),
                        icon = Icons.Default.Inbox
                    )
                }
            }

            items(dumps, key = { it.id }) { dump ->
                val isSelected = selectedDumpId == dump.id
                AppCard(
                    selected = isSelected,
                    onClick = { selectedDumpId = dump.id }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedDumpId = dump.id }
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.space8))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dump.name.ifBlank { dump.uid },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.space4))
                            Text(
                                text = "${dump.cardType} · ${dump.data.size} bytes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onNavigateToEditor(dump.id) },
                            modifier = Modifier.height(AppSpacing.minTouchTarget)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(AppSpacing.space8))
                PrimaryActionButton(
                    text = stringResource(R.string.write_card_start),
                    onClick = { showConfirmDialog = true },
                    enabled = selectedDump != null && !isWriting,
                    loading = isWriting,
                    icon = Icons.Default.Nfc
                )
            }

            if (isWriting && writeProgress.isNotBlank()) {
                item {
                    InlineNotice(
                        text = writeProgress,
                        tone = NoticeTone.Info,
                        icon = Icons.Default.Nfc
                    )
                }
            }

            writeResult?.let { result ->
                item {
                    AppCard {
                        Column {
                            StatusPill(
                                text = if (result.success) {
                                    stringResource(R.string.write_result_success)
                                } else {
                                    stringResource(R.string.write_result_partial)
                                },
                                tone = if (result.success) NoticeTone.Success else NoticeTone.Warning
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.space12))
                            Text(
                                text = stringResource(R.string.write_result_written_blocks, result.writtenBlocks),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (result.failBlocks.isNotEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.write_result_fail_blocks,
                                        result.failBlocks.joinToString(", ") { "B%03d".format(it) }
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (result.authFailedSectors.isNotEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.write_result_auth_failed,
                                        result.authFailedSectors.joinToString(", ") { "S$it" }
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (result.skippedBlocks.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.write_result_skipped, result.skippedBlocks.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            errorMessage?.let { msg ->
                item {
                    InlineNotice(
                        text = msg,
                        tone = NoticeTone.Error,
                        icon = Icons.Default.Warning
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(AppSpacing.space16)) }
        }
    }

    if (showConfirmDialog && selectedDump != null) {
        val dump = selectedDump!!
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.write_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.write_confirm_body))
                    Spacer(modifier = Modifier.height(AppSpacing.space12))
                    InfoRow(
                        label = stringResource(R.string.write_confirm_name),
                        value = dump.name.ifBlank { dump.uid }
                    )
                    InfoRow(
                        label = stringResource(R.string.write_confirm_type),
                        value = dump.cardType
                    )
                    InfoRow(
                        label = stringResource(R.string.write_confirm_size),
                        value = "${dump.data.size} bytes"
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.space12))
                    InlineNotice(
                        text = stringResource(R.string.write_confirm_warning),
                        tone = NoticeTone.Warning,
                        icon = Icons.Default.Warning
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.writeToCard(dump.data)
                    }
                ) {
                    Text(stringResource(R.string.write_confirm_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

