package com.longyannfc.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.longyannfc.app.R
import com.longyannfc.app.data.repository.CardRepository
import com.longyannfc.app.data.repository.SmartDetectionPhase
import com.longyannfc.app.utils.HexLine
import com.longyannfc.app.utils.HexUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    dumpId: Long = 0L,
    data: ByteArray = byteArrayOf(),
    onDataChanged: (ByteArray) -> Unit = {},
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
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dump?.name?.ifBlank { "Hex Editor" } ?: "Hex Editor") },
                actions = {
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(
                            if (editMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Edit"
                        )
                    }
                    IconButton(onClick = {
                        if (dumpId > 0) viewModel.updateData(dumpId, editorData)
                        onDataChanged(editorData)
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save changes") },
                                onClick = {
                                    if (dumpId > 0) viewModel.updateData(dumpId, editorData)
                                    onDataChanged(editorData)
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Load from file") },
                                onClick = { showMenu = false },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Save to file") },
                                onClick = { showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as bin") },
                                onClick = { showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Import from bin") },
                                onClick = { showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            DumpEditorLegend()

            if (editMode && selectedBlock != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Block ${selectedBlock}:",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(100.dp)
                        )
                        BasicTextField(
                            value = editValue,
                            onValueChange = { editValue = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                        )
                        TextButton(onClick = {
                            val bytes = HexUtils.fromHex(editValue.replace(" ", ""))
                            selectedBlock?.let { blockIndex ->
                                val start = blockIndex * 16
                                if (bytes.isNotEmpty() && start < editorData.size) {
                                    val next = editorData.copyOf()
                                    bytes.copyInto(next, destinationOffset = start, endIndex = minOf(bytes.size, 16, next.size - start))
                                    editorData = next
                                }
                            }
                            editMode = false
                            selectedBlock = null
                        }) {
                            Text("Apply")
                        }
                    }
                }
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
                        }
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun DumpEditorLegend() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendChip("UID/Manuf", Color(0xFF7E57C2))
        LegendChip("Key A", Color(0xFF2E7D32))
        LegendChip("AC", Color(0xFFEF6C00))
        LegendChip("Key B", Color(0xFF00695C))
        LegendChip(context.getString(R.string.value_block), Color(0xFFF9A825))
    }
}

@Composable
private fun LegendChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), contentColor = color) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SectorHeader(sector: Int, blocks: Int) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Sector: $sector  ($blocks blocks)",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun DumpBlockRow(
    block: DumpBlock,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val markerColor = when (block.kind) {
        BlockKind.MANUFACTURER -> Color(0xFF7E57C2)
        BlockKind.TRAILER -> Color(0xFF2E7D32)
        BlockKind.VALUE -> Color(0xFFF9A825)
        BlockKind.DATA -> MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "B%03d.%02d".format(block.absoluteBlock, block.blockInSector),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(58.dp),
                color = markerColor
            )
            Text(
                text = block.kind.label,
                style = MaterialTheme.typography.labelSmall,
                color = markerColor,
                modifier = Modifier.width(88.dp)
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
            Text(
                text = block.hex,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                modifier = Modifier.padding(start = 58.dp, top = 2.dp),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun TrailerLine(hex: String) {
    Row(modifier = Modifier.padding(start = 58.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(hex.take(17), color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
        Text(hex.drop(18).take(11), color = Color(0xFFEF6C00), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
        Text(hex.takeLast(17), color = Color(0xFF00695C), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
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
        ascii = bytes.map { if ((it.toInt() and 0xFF) in 32..126) it.toInt().toChar() else '.' }.joinToString(""),
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

private fun isTrailerBlock(block: Int): Boolean = if (block < 128) block % 4 == 3 else (block - 128) % 16 == 15

private fun ByteArray.isValueBlock(): Boolean {
    if (size < 16) return false
    fun complementEquals(left: Byte, right: Byte): Boolean = ((left.toInt() xor right.toInt()) and 0xFF) == 0xFF
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
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%04X".format(line.offset),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.width(48.dp),
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = line.hex,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = line.ascii,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.width(120.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    original: ByteArray = byteArrayOf(),
    modified: ByteArray = byteArrayOf()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val diffs = remember(original, modified) { HexUtils.diff(original, modified) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(context.getString(R.string.compare_dumps)) }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column {
                        Text(context.getString(R.string.original_bytes, original.size))
                        Text(context.getString(R.string.modified_bytes, modified.size))
                    }
                    Column {
                        Text(context.getString(R.string.differences, diffs.size))
                        Text(context.getString(R.string.same_bytes, original.size - diffs.size))
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(diffs) { _, diff ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "0x%04X".format(diff.offset),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.width(60.dp)
                            )
                            Text(
                                text = "%02X".format(diff.oldValue),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.error
                            )
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Text(
                                text = "%02X".format(diff.newValue),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionScreen(
    viewModel: DetectionViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDetecting by viewModel.isDetecting.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val progressPercent by viewModel.progressPercent.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val phaseLabel by viewModel.phaseLabel.collectAsState()
    val smartResult by viewModel.smartResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text(context.getString(R.string.smart_detection)) }) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 统一入口按钮
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(context.getString(R.string.one_click_detection), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(context.getString(R.string.detection_desc), style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.startSmartDetection() },
                            enabled = !isDetecting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isDetecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isDetecting) context.getString(R.string.detecting) else context.getString(R.string.start_detection))
                        }
                    }
                }
            }

            // 进度显示
            if (isDetecting) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 阶段指示
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        phaseLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (progress.isNotBlank()) {
                                        Text(
                                            progress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            // 进度条
                            LinearProgressIndicator(
                                progress = { progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${progressPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }

                // 阶段时间线
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(context.getString(R.string.detection_flow), style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            val phases = listOf(
                                SmartDetectionPhase.CARD_DETECT to context.getString(R.string.phase_card_detect),
                                SmartDetectionPhase.KEY_SCAN to context.getString(R.string.phase_key_scan),
                                SmartDetectionPhase.READ_CARD to context.getString(R.string.phase_read_card),
                                SmartDetectionPhase.ANALYZE to context.getString(R.string.phase_analyze),
                                SmartDetectionPhase.ENTER_DETECTION_MODE to context.getString(R.string.phase_enter_detection),
                                SmartDetectionPhase.WAIT_READER to context.getString(R.string.phase_wait_reader),
                                SmartDetectionPhase.COLLECT_NONCE to context.getString(R.string.phase_collect_nonce),
                                SmartDetectionPhase.NESTED_ATTACK to context.getString(R.string.phase_nested_attack),
                                SmartDetectionPhase.COMPLETE to context.getString(R.string.phase_complete)
                            )
                            phases.forEach { (p, label) ->
                                val isActive = p == phase
                                val isPast = phases.indexOfFirst { it.first == phase } > phases.indexOfFirst { it.first == p }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isPast -> Icons.Default.CheckCircle
                                            isActive -> Icons.Default.Search
                                            else -> Icons.Default.Nfc
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = when {
                                            isPast -> MaterialTheme.colorScheme.primary
                                            isActive -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            isPast -> MaterialTheme.colorScheme.onSurface
                                            isActive -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        },
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 侦测结果
            smartResult?.let { result ->
                // 安全分析卡片
                result.securityAnalysis?.let { analysis ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (analysis.hasStrongEncryption)
                                    MaterialTheme.colorScheme.errorContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    context.getString(R.string.security_analysis),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (analysis.hasStrongEncryption)
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    analysis.reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (analysis.hasStrongEncryption)
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "${context.getString(R.string.known_sectors)}: ${analysis.knownSectors}/${analysis.totalSectors} | " +
                                            "${context.getString(R.string.encrypted_sectors)}: ${analysis.encryptedSectors} | " +
                                            "${context.getString(R.string.dead_zones)}: ${analysis.deadZones.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (analysis.hasStrongEncryption)
                                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                if (analysis.distance >= 0) {
                                    Text(
                                        "LFSR Distance: ${analysis.distance}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (analysis.hasStrongEncryption)
                                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                        else
                                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 密钥恢复结果
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(context.getString(R.string.recovered_keys), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (result.initialKeys.isNotEmpty()) {
                                Text(
                                    context.getString(R.string.initial_scan, result.initialKeys.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                result.initialKeys.forEach { key ->
                                    Text(
                                        "  Block ${key.block} ${key.type}: ${key.key}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (result.nestedKeys.isNotEmpty()) {
                                Text(
                                    context.getString(R.string.nested_recovered, result.nestedKeys.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                result.nestedKeys.forEach { key ->
                                    Text(
                                        "  Block ${key.block} ${key.type}: ${key.key}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (result.allKeys.isEmpty()) {
                                Text(
                                    context.getString(R.string.no_keys_recovered),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                context.getString(R.string.total_keys, result.allKeys.size),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                context.getString(R.string.read_status, if (result.readSuccess) context.getString(R.string.read_complete) else context.getString(R.string.read_partial)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.readSuccess)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // 完整报告
                item {
                    var expanded by remember { mutableStateOf(false) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(context.getString(R.string.detail_report), style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(if (expanded) context.getString(R.string.collapse) else context.getString(R.string.expand))
                                }
                            }
                            if (expanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    result.report,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(context.getString(R.string.detection_failed)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(
    viewModel: KeysViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val keysByUid by viewModel.keysByUid.collectAsState()
    val dictionaryKeys by viewModel.dictionaryKeys.collectAsState()
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.local_key_management)) })
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 按卡号分组的历史密钥
            item {
                Text(
                    text = context.getString(R.string.cached_keys),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }
            if (keysByUid.isEmpty()) {
                item { Text(context.getString(R.string.no_cached_keys), modifier = Modifier.padding(8.dp)) }
            }
            keysByUid.forEach { (uid, keys) ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "UID: $uid",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { deleteTarget = uid }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            keys.forEach { key ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    if (key.keyA != null) {
                                        Text(
                                            text = "S${key.sector} A: ${key.keyA}",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (key.keyB != null) {
                                        Text(
                                            text = "S${key.sector} B: ${key.keyB}",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 本地字典
            item {
                Text(
                    text = context.getString(R.string.local_dictionary),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }
            items(dictionaryKeys) { key ->
                KeyItem(key = key, type = "Dictionary")
            }
        }
    }

    // 删除确认弹窗
    deleteTarget?.let { uid ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除卡号 $uid 的所有历史密钥吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteKeysByUid(uid)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun KeyItem(key: String, type: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = key,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = type,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}


