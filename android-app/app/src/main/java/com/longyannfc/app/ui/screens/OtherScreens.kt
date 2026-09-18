package com.longyannfc.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.longyannfc.app.R
import com.longyannfc.app.data.ble.MifareInfo
import com.longyannfc.app.utils.CardTagInfo
import com.longyannfc.app.utils.HexUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {}
) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Login") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Verification Code") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { isLoading = true },
                enabled = !isLoading && phone.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Login")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToKeys: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    var debugMode by remember { mutableStateOf(false) }
    var uidMode by remember { mutableStateOf(false) }
    var autoDetect by remember { mutableStateOf(true) }
    var currentLanguage by remember { mutableStateOf(com.longyannfc.app.utils.LocaleHelper.getLanguage(context)) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.settings)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ListItem(
                headlineContent = { Text(context.getString(R.string.language_setting)) },
                supportingContent = { Text(com.longyannfc.app.utils.LocaleHelper.getDisplayLanguage(currentLanguage)) },
                leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )
            Divider()
            ListItem(
                headlineContent = { Text(context.getString(R.string.debug_mode)) },
                supportingContent = { Text(context.getString(R.string.enable_debug_logging)) },
                trailingContent = {
                    Switch(
                        checked = debugMode,
                        onCheckedChange = { debugMode = it }
                    )
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text(context.getString(R.string.uid_mode)) },
                supportingContent = { Text(context.getString(R.string.enable_uid_mode)) },
                trailingContent = {
                    Switch(
                        checked = uidMode,
                        onCheckedChange = { uidMode = it }
                    )
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text(context.getString(R.string.auto_detect)) },
                supportingContent = { Text(context.getString(R.string.auto_detect_card)) },
                trailingContent = {
                    Switch(
                        checked = autoDetect,
                        onCheckedChange = { autoDetect = it }
                    )
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text(context.getString(R.string.local_key_management)) },
                supportingContent = { Text(context.getString(R.string.cached_keys)) },
                leadingContent = { Icon(Icons.Default.Key, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToKeys() }
            )
            Divider()
            ListItem(
                headlineContent = { Text(context.getString(R.string.about)) },
                supportingContent = { Text(context.getString(R.string.version)) },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null)
                }
            )
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(context.getString(R.string.language_setting)) },
            text = {
                Column {
                    listOf(
                        com.longyannfc.app.utils.LocaleHelper.LANGUAGE_SYSTEM to context.getString(R.string.follow_system),
                        com.longyannfc.app.utils.LocaleHelper.LANGUAGE_ENGLISH to "English",
                        com.longyannfc.app.utils.LocaleHelper.LANGUAGE_CHINESE to "中文"
                    ).forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    com.longyannfc.app.utils.LocaleHelper.setLanguage(context, code)
                                    currentLanguage = code
                                    showLanguageDialog = false
                                    // 即时生效：重建 Activity
                                    activity?.recreate()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLanguage == code,
                                onClick = {
                                    com.longyannfc.app.utils.LocaleHelper.setLanguage(context, code)
                                    currentLanguage = code
                                    showLanguageDialog = false
                                    // 即时生效：重建 Activity
                                    activity?.recreate()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupercardScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.supercard)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(context.getString(R.string.supercard_management))
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(context.getString(R.string.supercard_info))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(context.getString(R.string.firmware_unknown))
                    Text(context.getString(R.string.battery_unknown))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DfuScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isUpdating by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.dfu_title)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(context.getString(R.string.dfu_title))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = context.getString(R.string.dfu_desc),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (isUpdating) {
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("$progress%")
            } else {
                Button(
                    onClick = { isUpdating = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(context.getString(R.string.select_firmware_btn))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VCardScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.virtual_card)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(context.getString(R.string.virtual_card))
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(context.getString(R.string.card_data))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(context.getString(R.string.no_virtual_card))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VCardSimpleScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.simple_virtual_card)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(context.getString(R.string.simple_virtual_card))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.user_center)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = context.getString(R.string.guest),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = context.getString(R.string.not_logged_in),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.help)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(context.getString(R.string.help_documentation))
            Spacer(modifier = Modifier.height(16.dp))
            Text(context.getString(R.string.help_step_1))
            Text(context.getString(R.string.help_step_2))
            Text(context.getString(R.string.help_step_3))
            Text(context.getString(R.string.help_step_4))
            Text(context.getString(R.string.help_step_5))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.operation_log_title)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(context.getString(R.string.operation_log_title))
            Spacer(modifier = Modifier.height(16.dp))
            Text(context.getString(R.string.no_logs))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.functions)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(context.getString(R.string.available_functions))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadCardScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.read_card)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(context.getString(R.string.read_card))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteCardScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.write_card)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(context.getString(R.string.write_card))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RwCardScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(context.getString(R.string.read_write_card)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(context.getString(R.string.read_write_card))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen() {
    CardScreen(dumpId = 0L)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(
    dumpId: Long,
    liveInfo: MifareInfo? = null,
    viewModel: CardDumpViewModel = hiltViewModel()
) {
    val dump by if (dumpId > 0) {
        viewModel.observeDump(dumpId).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    val hasLiveData = liveInfo != null

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Card Info") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (dump == null && !hasLiveData) {
                Text("暂无卡片数据，请先读卡或从历史记录打开。")
            } else if (hasLiveData && dump == null) {
                // Show live tag info from quick detect
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Card Detected", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                tagInfo.rows.forEach { (label, value) ->
                                    InfoRow(label = label, value = value)
                                }
                                tagInfo.warnings.forEach { warning ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "提示：点击主界面「Read」按钮可读取完整卡片数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val currentDump = dump!!
                val tagInfo = remember(currentDump.id, currentDump.updatedAt) {
                    CardTagInfo.fromDump(currentDump.uid, currentDump.cardType, currentDump.size, currentDump.data)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(currentDump.name.ifBlank { currentDump.uid }, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("UID: ${currentDump.uid}")
                                Text("Type: ${currentDump.cardType}")
                                Text("Size: ${currentDump.data.size} bytes")
                                Text("Fail blocks: ${currentDump.failBlocks.ifBlank { "None" }}")
                                Text("Updated: ${formatTime(currentDump.updatedAt)}")
                            }
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("通用标签信息", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                tagInfo.rows.forEach { (label, value) ->
                                    InfoRow(label = label, value = value)
                                }
                                tagInfo.warnings.forEach { warning ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardHistoryScreen(
    onOpen: (Long) -> Unit,
    viewModel: CardDumpViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val dumps by viewModel.dumps.collectAsState()
    var deleteTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var exportTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    // SAF 导出文件
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val target = exportTarget ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            viewModel.exportDump(context, target.first, uri)
        }
        exportTarget = null
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("读卡历史") }) }
    ) { paddingValues ->
        if (dumps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无读卡记录")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dumps, key = { it.id }) { dump ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(dump.name.ifBlank { dump.uid }, style = MaterialTheme.typography.titleMedium)
                            Text("${dump.cardType}  ${dump.data.size} bytes  ${formatTime(dump.updatedAt)}")
                            Text(
                                text = HexUtils.toHex(dump.data.take(8).map { it.toInt() }.toIntArray(), " "),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onOpen(dump.id) }) { Text("编辑") }
                                OutlinedButton(onClick = {
                                    val fileName = "${dump.uid}_${dump.cardType}.bin"
                                    exportTarget = dump.id to fileName
                                    exportLauncher.launch(fileName)
                                }) { Text("导出") }
                                OutlinedButton(onClick = {
                                    deleteTarget = dump.id to (dump.name.ifBlank { dump.uid })
                                }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除确认弹窗
    deleteTarget?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「$name」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(id)
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
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.width(124.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatTime(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
