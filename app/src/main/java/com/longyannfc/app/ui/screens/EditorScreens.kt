package com.longyannfc.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.longyannfc.app.R
import com.longyannfc.app.ui.components.AppCard
import com.longyannfc.app.ui.components.AppScaffold
import com.longyannfc.app.ui.components.EmptyState
import com.longyannfc.app.ui.components.InlineNotice
import com.longyannfc.app.ui.components.NoticeTone
import com.longyannfc.app.ui.components.PrimaryActionButton
import com.longyannfc.app.ui.components.SecondaryButton
import com.longyannfc.app.ui.theme.AppShape
import com.longyannfc.app.ui.theme.AppSpacing
import com.longyannfc.app.ui.theme.appColors
import com.longyannfc.app.utils.HexLine
import com.longyannfc.app.utils.HexUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    dumpId: Long = 0L,
    data: ByteArray = byteArrayOf(),
    onDataChanged: (ByteArray) -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: CardDumpViewModel = hiltViewModel()
) {
    val dump by if (dumpId > 0) {
        viewModel.observeDump(dumpId).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }
    var editorData by remember(dumpId, dump?.updatedAt, data) { mutableStateOf(dump?.data ?: data) }
    val editorBlocks by remember(editorData) { mutableStateOf(editorData.toDumpBlocks()) }
    var selectedBlock by remember { mutableStateOf<Int?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showWriteDialog by remember { mutableStateOf(false) }

    val isWriting by viewModel.isWriting.collectAsState()
    val writeProgress by viewModel.writeProgress.collectAsState()
    val writeResult by viewModel.writeResult.collectAsState()
    val writeError by viewModel.errorMessage.collectAsState()

    val title = dump?.name?.ifBlank { null } ?: stringResource(R.string.hex_editor)

    AppScaffold(
        title = title,
        onBack = onBack,
        actions = {
            if (dumpId > 0) {
                IconButton(
                    onClick = { showWriteDialog = true },
                    enabled = !isWriting
                ) {
                    Icon(
                        Icons.Default.Nfc,
                        contentDescription = stringResource(R.string.editor_write_to_card),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = { editMode = !editMode }) {
                Icon(
                    imageVector = if (editMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = stringResource(R.string.editor_edit)
                )
            }
            IconButton(
                onClick = {
                    if (dumpId > 0) viewModel.updateData(dumpId, editorData)
                    onDataChanged(editorData)
                }
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = stringResource(R.string.editor_save)
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.editor_menu)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_save_changes)) },
                        onClick = {
                            if (dumpId > 0) viewModel.updateData(dumpId, editorData)
                            onDataChanged(editorData)
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_load_from_file)) },
                        onClick = { showMenu = false },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_save_to_file)) },
                        onClick = { showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_export_bin)) },
                        onClick = { showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_import_bin)) },
                        onClick = { showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                    )
                }
            }
        }
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
        ) {
            if (editorData.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.editor_empty),
                    description = stringResource(R.string.editor_empty_desc),
                    icon = Icons.Default.Save,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppSpacing.pageHorizontal)
                )
            } else {
                DumpEditorLegend()
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (editMode && selectedBlock != null) {
                    BlockEditBar(
                        blockIndex = selectedBlock!!,
                        value = editValue,
                        error = editError,
                        onValueChange = {
                            editValue = it
                            editError = null
                        },
                        onApply = {
                            val bytes = HexUtils.fromHex(editValue.replace(" ", ""))
                            val blockIndex = selectedBlock
                            if (bytes.isEmpty()) {
                                editError = "请输入有效的十六进制数据"
                            } else if (blockIndex != null) {
                                val start = blockIndex * 16
                                if (start >= editorData.size) {
                                    editError = "该块超出数据范围"
                                } else {
                                    val next = editorData.copyOf()
                                    bytes.copyInto(
                                        next,
                                        destinationOffset = start,
                                        endIndex = minOf(bytes.size, 16, next.size - start)
                                    )
                                    editorData = next
                                    editMode = false
                                    selectedBlock = null
                                    editError = null
                                }
                            }
                        },
                        onCancel = {
                            editMode = false
                            selectedBlock = null
                            editError = null
                        }
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    editorBlocks.groupBy { it.sector }.forEach { (sector, blocks) ->
                        item(key = "sector-$sector") {
                            SectorHeader(sector = sector, blocks = blocks.size)
                        }
                        items(blocks, key = { it.absoluteBlock }) { block ->
                            DumpBlockRow(
                                block = block,
                                isSelected = selectedBlock == block.absoluteBlock,
                                onClick = {
                                    selectedBlock = block.absoluteBlock
                                    editValue = block.hex
                                    editMode = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWriteDialog) {
        AlertDialog(
            onDismissRequest = { showWriteDialog = false },
            title = { Text(stringResource(R.string.write_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.write_confirm_body))
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
                        showWriteDialog = false
                        viewModel.writeToCard(editorData)
                    }
                ) {
                    Text(stringResource(R.string.write_confirm_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWriteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (isWriting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.write_card_in_progress)) },
            text = {
                Column {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.space12))
                    Text(
                        text = if (writeProgress.isBlank()) {
                            stringResource(R.string.loading)
                        } else {
                            writeProgress
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { }
        )
    }

    writeResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearWriteResult() },
            title = {
                Text(
                    text = if (result.success) {
                        stringResource(R.string.write_result_success)
                    } else {
                        stringResource(R.string.write_result_partial)
                    }
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.write_result_written_blocks, result.writtenBlocks),
                        style = MaterialTheme.typography.bodyMedium
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
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearWriteResult() }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }

    writeError?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.write_failed)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

/** 块编辑栏：校验信息紧贴字段，不依赖 Toast */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockEditBar(
    blockIndex: Int,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(AppSpacing.space12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.editor_block_label, blockIndex),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(AppSpacing.space8))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    isError = error != null,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = AppSpacing.minTouchTarget)
                )
                Spacer(modifier = Modifier.width(AppSpacing.space8))
                TextButton(onClick = onApply) {
                    Text(stringResource(R.string.apply))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(AppSpacing.space4))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DumpEditorLegend() {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.denseHorizontal, vertical = AppSpacing.space8),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.space8)
    ) {
        LegendChip(stringResource(R.string.legend_uid_manuf), colors.blockManufacturer)
        LegendChip(stringResource(R.string.key_a), colors.blockTrailer)
        LegendChip(stringResource(R.string.legend_ac), colors.blockAccess)
        LegendChip(stringResource(R.string.key_b), colors.blockKeyB)
        LegendChip(stringResource(R.string.value_block), colors.blockValue)
    }
}

@Composable
private fun LegendChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.14f),
                shape = RoundedCornerShape(AppShape.small)
            )
            .padding(horizontal = AppSpacing.space8, vertical = AppSpacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.space4)
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .background(color = color, shape = RoundedCornerShape(2.dp))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun SectorHeader(sector: Int, blocks: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.editor_sector_label, sector, blocks),
            modifier = Modifier.padding(
                horizontal = AppSpacing.denseHorizontal,
                vertical = AppSpacing.space8
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DumpBlockRow(
    block: DumpBlock,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.appColors
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val markerColor = when (block.kind) {
        BlockKind.MANUFACTURER -> colors.blockManufacturer
        BlockKind.TRAILER -> colors.blockTrailer
        BlockKind.VALUE -> colors.blockValue
        BlockKind.DATA -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppSpacing.denseHorizontal,
                vertical = AppSpacing.space8
            )
    ) {
        // 横向滚动保证大字号下十六进制内容不被裁切
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "B%03d.%02d".format(block.absoluteBlock, block.blockInSector),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(64.dp),
                color = markerColor
            )
            Text(
                text = block.kind.label,
                style = MaterialTheme.typography.labelSmall,
                color = markerColor,
                modifier = Modifier.width(92.dp)
            )
            Text(
                text = block.ascii,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (block.kind == BlockKind.TRAILER) {
            TrailerLine(block.hex)
        } else {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 64.dp, top = AppSpacing.space4)
            ) {
                Text(
                    text = block.hex,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun TrailerLine(hex: String) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 64.dp, top = AppSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.space8)
    ) {
        Text(
            text = hex.take(17),
            color = colors.blockTrailer,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = hex.drop(18).take(11),
            color = colors.blockAccess,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = hex.takeLast(17),
            color = colors.blockKeyB,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            softWrap = false
        )
    }
}

private data class DumpBlock(
    val absoluteBlock: Int,
    val sector: Int,
    val blockInSector: Int,
    val hex: String,
    val ascii: String,
    val kind: BlockKind
)

private enum class BlockKind(val label: String) {
    MANUFACTURER("UID/Manuf"),
    TRAILER("Sector Trailer"),
    VALUE("Value Block"),
    DATA("Data")
}

private fun ByteArray.toDumpBlocks(): List<DumpBlock> = asList().chunked(16).mapIndexed { index, chunk ->
    val bytes = chunk.toByteArray()
    val sector = sectorForBlock(index)
    val blockInSector = blockInSector(index)
    DumpBlock(
        absoluteBlock = index,
        sector = sector,
        blockInSector = blockInSector,
        hex = HexUtils.toHex(bytes),
        ascii = bytes.map { if ((it.toInt() and 0xFF) in 32..126) it.toInt().toChar() else '.' }
                .joinToString(""),
        kind = when {
            index == 0 -> BlockKind.MANUFACTURER
            isTrailerBlock(index) -> BlockKind.TRAILER
            bytes.isValueBlock() -> BlockKind.VALUE
            else -> BlockKind.DATA
        }
    )
}

private fun sectorForBlock(block: Int): Int = if (block < 128) block / 4 else 32 + (block - 128) / 16

private fun blockInSector(block: Int): Int = if (block < 128) block % 4 else (block - 128) % 16

private fun isTrailerBlock(block: Int): Boolean =
    if (block < 128) block % 4 == 3 else (block - 128) % 16 == 15

private fun ByteArray.isValueBlock(): Boolean {
    if (size < 16) return false
    fun complementEquals(left: Byte, right: Byte): Boolean =
        ((left.toInt() xor right.toInt()) and 0xFF) == 0xFF
    return this[0] == this[8] && this[1] == this[9] && this[2] == this[10] && this[3] == this[11] &&
        complementEquals(this[4], this[0]) && complementEquals(this[5], this[1]) &&
        complementEquals(this[6], this[2]) && complementEquals(this[7], this[3]) &&
        this[12] == this[14] && this[13] == this[15] && complementEquals(this[12], this[13])
}

@Composable
fun HexLineRow(
    line: HexLine,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.denseHorizontal, vertical = AppSpacing.space4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%04X".format(line.offset),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.width(48.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(AppSpacing.space8))
        Text(
            text = line.hex,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
        Spacer(modifier = Modifier.width(AppSpacing.space8))
        Text(
            text = line.ascii,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.width(120.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    original: ByteArray = byteArrayOf(),
    modified: ByteArray = byteArrayOf(),
    onBack: (() -> Unit)? = null
) {
    val diffs = remember(original, modified) { HexUtils.diff(original, modified) }

    AppScaffold(
        title = stringResource(R.string.compare_dumps),
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
                AppCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.original_bytes, original.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.modified_bytes, modified.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.differences, diffs.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.same_bytes, original.size - diffs.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (diffs.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.same_bytes, original.size),
                        icon = Icons.Default.Check,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                itemsIndexed(diffs) { _, diff ->
                    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.space12)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "0x%04X".format(diff.offset),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.width(72.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "%02X".format(diff.oldValue.toInt() and 0xFF),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.error
                            )
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.padding(horizontal = AppSpacing.space4)
                            )
                            Text(
                                text = "%02X".format(diff.newValue.toInt() and 0xFF),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(AppSpacing.space16)) }
        }
    }
}

