package com.longyannfc.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.longyannfc.app.R
import com.longyannfc.app.ui.components.AppCard
import com.longyannfc.app.ui.components.AppScaffold
import com.longyannfc.app.ui.components.EmptyState
import com.longyannfc.app.ui.components.IconBadge
import com.longyannfc.app.ui.components.InlineNotice
import com.longyannfc.app.ui.components.NoticeTone
import com.longyannfc.app.ui.components.PrimaryActionButton
import com.longyannfc.app.ui.components.SecondaryButton
import com.longyannfc.app.ui.components.SettingRow
import com.longyannfc.app.ui.components.StatusPill
import com.longyannfc.app.ui.theme.AppShape
import com.longyannfc.app.ui.theme.AppSpacing
import com.longyannfc.app.utils.DebugFlags
import com.longyannfc.app.utils.LocaleHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToKeys: () -> Unit = {},
    onNavigateToLog: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onNavigateToSupercard: () -> Unit = {},
    onNavigateToDfu: () -> Unit = {},
    onNavigateToReadWrite: () -> Unit = {},
    onNavigateToFunctions: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var debugMode by remember { mutableStateOf(DebugFlags.verboseLogging) }
    var uidMode by remember { mutableStateOf(false) }
    var autoDetect by remember { mutableStateOf(true) }
    var currentLanguage by remember { mutableStateOf(LocaleHelper.getLanguage(context)) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    AppScaffold(
        title = stringResource(R.string.settings),
        onBack = onBack
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionTitle(text = stringResource(R.string.settings_section_general))
            SettingRow(
                title = stringResource(R.string.language_setting),
                subtitle = LocaleHelper.getDisplayLanguage(currentLanguage),
                leading = Icons.Default.Language,
                onClick = { showLanguageDialog = true }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingRow(
                title = stringResource(R.string.debug_mode),
                subtitle = stringResource(R.string.enable_debug_logging),
                leading = Icons.Default.BugReport,
                enabled = DebugFlags.canToggle,
                trailing = {
                    Switch(
                        checked = debugMode,
                        onCheckedChange = {
                            debugMode = it
                            DebugFlags.setVerboseLogging(it)
                        },
                        enabled = DebugFlags.canToggle
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingRow(
                title = stringResource(R.string.uid_mode),
                subtitle = stringResource(R.string.enable_uid_mode),
                leading = Icons.Default.Radio,
                trailing = {
                    Switch(
                        checked = uidMode,
                        onCheckedChange = { uidMode = it }
                    )
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingRow(
                title = stringResource(R.string.auto_detect),
                subtitle = stringResource(R.string.auto_detect_card),
                leading = Icons.Default.Usb,
                trailing = {
                    Switch(
                        checked = autoDetect,
                        onCheckedChange = { autoDetect = it }
                    )
                }
            )

            SettingsSectionTitle(text = stringResource(R.string.settings_section_data))
            SettingRow(
                title = stringResource(R.string.local_key_management),
                subtitle = stringResource(R.string.cached_keys),
                leading = Icons.Default.Key,
                onClick = onNavigateToKeys
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingRow(
                title = stringResource(R.string.operation_log_title),
                subtitle = stringResource(R.string.log_empty_desc),
                leading = Icons.Default.History,
                onClick = onNavigateToLog
            )

            SettingsSectionTitle(text = stringResource(R.string.settings_section_device))
            SettingRow(
                title = stringResource(R.string.supercard_management),
                subtitle = stringResource(R.string.supercard_info),
                leading = Icons.Default.CreditCard,
                onClick = onNavigateToSupercard
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingRow(
                title = stringResource(R.string.dfu_firmware_update),
                subtitle = stringResource(R.string.select_firmware_file),
                leading = Icons.Default.Usb,
                onClick = onNavigateToDfu
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingRow(
                title = stringResource(R.string.read_write_card),
                subtitle = stringResource(R.string.read_card),
                leading = Icons.Default.Article,
                onClick = onNavigateToReadWrite
            )

            SettingsSectionTitle(text = stringResource(R.string.settings_section_support))
            SettingRow(
                title = stringResource(R.string.help),
                subtitle = stringResource(R.string.help_documentation),
                leading = Icons.Default.Info,
                onClick = onNavigateToHelp
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingRow(
                title = stringResource(R.string.functions),
                subtitle = stringResource(R.string.available_functions),
                leading = Icons.Default.Article,
                onClick = onNavigateToFunctions
            )

            SettingsSectionTitle(text = stringResource(R.string.settings_section_about))
            SettingRow(
                title = stringResource(R.string.about),
                subtitle = stringResource(R.string.version),
                leading = Icons.Default.Info
            )
            Spacer(modifier = Modifier.height(AppSpacing.space24))
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_setting)) },
            text = {
                Column {
                    listOf(
                        LocaleHelper.LANGUAGE_SYSTEM to stringResource(R.string.follow_system),
                        LocaleHelper.LANGUAGE_ENGLISH to "English",
                        LocaleHelper.LANGUAGE_CHINESE to "中文"
                    ).forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = currentLanguage == code,
                                    onClick = {
                                        LocaleHelper.setLanguage(context, code)
                                        currentLanguage = code
                                        showLanguageDialog = false
                                        // 即时生效：重建 Activity
                                        activity?.recreate()
                                    }
                                )
                                .padding(vertical = AppSpacing.space12),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLanguage == code,
                                onClick = {
                                    LocaleHelper.setLanguage(context, code)
                                    currentLanguage = code
                                    showLanguageDialog = false
                                    // 即时生效：重建 Activity
                                    activity?.recreate()
                                }
                            )
                            Spacer(modifier = Modifier.width(AppSpacing.space8))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

/** 设置页分组标题 */
@Composable
fun SettingsSectionTitle(text: String) {
    Column {
        Spacer(modifier = Modifier.height(AppSpacing.space24))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                horizontal = AppSpacing.pageHorizontal,
                vertical = AppSpacing.space8
            )
        )
    }
}

@Composable
fun LogScreen(
    onBack: (() -> Unit)? = null,
    viewModel: LogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()

    AppScaffold(
        title = stringResource(R.string.operation_log_title),
        onBack = onBack
    ) { modifier ->
        if (logs.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.log_empty_title),
                description = stringResource(R.string.log_empty_desc),
                icon = Icons.Default.History,
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.pageHorizontal)
            )
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.pageHorizontal),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.space8)
            ) {
                item { Spacer(modifier = Modifier.height(AppSpacing.space4)) }
                items(logs.size) { index ->
                    val log = logs[index]
                    AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.space12)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(text = log.type, tone = NoticeTone.Info)
                                Text(
                                    text = formatTimestamp(log.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(AppSpacing.space8))
                            Text(
                                text = log.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(AppSpacing.space16)) }
            }
        }
    }
}

