package com.longyannfc.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.longyannfc.app.R
import com.longyannfc.app.data.repository.SmartDetectionPhase
import com.longyannfc.app.ui.components.AppCard
import com.longyannfc.app.ui.components.AppScaffold
import com.longyannfc.app.ui.components.EmptyState
import com.longyannfc.app.ui.components.InlineNotice
import com.longyannfc.app.ui.components.NoticeTone
import com.longyannfc.app.ui.components.PrimaryActionButton
import com.longyannfc.app.ui.components.SectionTitle
import com.longyannfc.app.ui.components.StatusPill
import com.longyannfc.app.ui.theme.AppSpacing
import com.longyannfc.app.ui.theme.MonospaceTextStyle
import com.longyannfc.app.ui.theme.appColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionScreen(
    onBack: (() -> Unit)? = null,
    viewModel: DetectionViewModel = hiltViewModel()
) {
    val isDetecting by viewModel.isDetecting.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val progressPercent by viewModel.progressPercent.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val phaseLabel by viewModel.phaseLabel.collectAsState()
    val smartResult by viewModel.smartResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    AppScaffold(
        title = stringResource(R.string.smart_detection),
        onBack = onBack
    ) { modifier ->
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
                            text = stringResource(R.string.one_click_detection),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.space4))
                        Text(
                            text = stringResource(R.string.detection_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.space16))
                        PrimaryActionButton(
                            text = if (isDetecting) {
                                stringResource(R.string.detecting)
                            } else {
                                stringResource(R.string.start_detection)
                            },
                            onClick = { viewModel.startSmartDetection() },
                            enabled = !isDetecting,
                            loading = isDetecting,
                            icon = Icons.Default.Search
                        )
                    }
                }
            }

            if (isDetecting) {
                item {
                    AppCard {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = phaseLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (progress.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(AppSpacing.space4))
                                        Text(
                                            text = progress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                StatusPill(
                                    text = "$progressPercent%",
                                    tone = NoticeTone.Info
                                )
                            }
                            Spacer(modifier = Modifier.height(AppSpacing.space16))
                            LinearProgressIndicator(
                                progress = { progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    AppCard {
                        Column {
                            SectionTitle(text = stringResource(R.string.detection_flow))
                            Spacer(modifier = Modifier.height(AppSpacing.space12))
                            val phases = listOf(
                                SmartDetectionPhase.CARD_DETECT to stringResource(R.string.phase_card_detect),
                                SmartDetectionPhase.KEY_SCAN to stringResource(R.string.phase_key_scan),
                                SmartDetectionPhase.READ_CARD to stringResource(R.string.phase_read_card),
                                SmartDetectionPhase.ANALYZE to stringResource(R.string.phase_analyze),
                                SmartDetectionPhase.ENTER_DETECTION_MODE to stringResource(R.string.phase_enter_detection),
                                SmartDetectionPhase.WAIT_READER to stringResource(R.string.phase_wait_reader),
                                SmartDetectionPhase.COLLECT_NONCE to stringResource(R.string.phase_collect_nonce),
                                SmartDetectionPhase.NESTED_ATTACK to stringResource(R.string.phase_nested_attack),
                                SmartDetectionPhase.COMPLETE to stringResource(R.string.phase_complete)
                            )
                            val currentIndex = phases.indexOfFirst { it.first == phase }
                            phases.forEachIndexed { index, (p, label) ->
                                val isActive = p == phase
                                val isPast = currentIndex > index
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = AppSpacing.space4)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isPast -> Icons.Default.CheckCircle
                                            isActive -> Icons.Default.Search
                                            else -> Icons.Default.Nfc
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = when {
                                            isPast -> MaterialTheme.colorScheme.primary
                                            isActive -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.outline
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.space12))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            isPast -> MaterialTheme.colorScheme.onSurface
                                            isActive -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            smartResult?.let { result ->
                result.securityAnalysis?.let { analysis ->
                    item {
                        AppCard {
                            Column {
                                Text(
                                    text = stringResource(R.string.security_analysis),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.space8))
                                StatusPill(
                                    text = if (analysis.hasStrongEncryption) {
                                        stringResource(R.string.encrypted_sectors)
                                    } else {
                                        stringResource(R.string.known_sectors)
                                    },
                                    tone = if (analysis.hasStrongEncryption) {
                                        NoticeTone.Error
                                    } else {
                                        NoticeTone.Success
                                    }
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.space12))
                                Text(
                                    text = analysis.reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.space12))
                                Text(
                                    text = "${stringResource(R.string.known_sectors)}: ${analysis.knownSectors}/${analysis.totalSectors} · " +
                                            "${stringResource(R.string.encrypted_sectors)}: ${analysis.encryptedSectors} · " +
                                            "${stringResource(R.string.dead_zones)}: ${analysis.deadZones.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (analysis.distance >= 0) {
                                    Spacer(modifier = Modifier.height(AppSpacing.space4))
                                    Text(
                                        text = "LFSR Distance: ${analysis.distance}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    AppCard {
                        Column {
                            Text(
                                text = stringResource(R.string.recovered_keys),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.space12))

                            if (result.initialKeys.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.initial_scan, result.initialKeys.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.space4))
                                result.initialKeys.forEach { key ->
                                    Text(
                                        text = "Block ${key.block} ${key.type}: ${key.key}",
                                        style = MonospaceTextStyle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(AppSpacing.space12))
                            }

                            if (result.nestedKeys.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.nested_recovered, result.nestedKeys.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.space4))
                                result.nestedKeys.forEach { key ->
                                    Text(
                                        text = "Block ${key.block} ${key.type}: ${key.key}",
                                        style = MonospaceTextStyle,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(AppSpacing.space12))
                            }

                            if (result.allKeys.isEmpty()) {
                                InlineNotice(
                                    text = stringResource(R.string.no_keys_recovered),
                                    tone = NoticeTone.Warning,
                                    icon = Icons.Default.Warning
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(AppSpacing.space12))
                            Text(
                                text = stringResource(R.string.total_keys, result.allKeys.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(AppSpacing.space4))
                            Text(
                                text = stringResource(
                                    R.string.read_status,
                                    if (result.readSuccess) {
                                        stringResource(R.string.read_complete)
                                    } else {
                                        stringResource(R.string.read_partial)
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.readSuccess) {
                                    MaterialTheme.appColors.success
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }

                item {
                    var expanded by remember { mutableStateOf(false) }
                    AppCard {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.detail_report),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(
                                        if (expanded) {
                                            stringResource(R.string.collapse)
                                        } else {
                                            stringResource(R.string.expand)
                                        }
                                    )
                                }
                            }
                            if (expanded) {
                                Spacer(modifier = Modifier.height(AppSpacing.space12))
                                Text(
                                    text = result.report,
                                    style = MonospaceTextStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (!isDetecting && smartResult == null) {
                item {
                    EmptyState(
                        title = stringResource(R.string.detection_idle_title),
                        description = stringResource(R.string.detection_idle_desc),
                        icon = Icons.Default.Search
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(AppSpacing.space16)) }
        }
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.detection_failed)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(
    onBack: (() -> Unit)? = null,
    viewModel: KeysViewModel = hiltViewModel()
) {
    val keysByUid by viewModel.keysByUid.collectAsState()
    val dictionaryKeys by viewModel.dictionaryKeys.collectAsState()
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    AppScaffold(
        title = stringResource(R.string.local_key_management),
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
                SectionTitle(text = stringResource(R.string.cached_keys))
            }

            if (keysByUid.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.no_cached_keys),
                        icon = Icons.Default.Key
                    )
                }
            }

            keysByUid.forEach { (uid, keys) ->
                item(key = "uid-$uid") {
                    AppCard {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.keys_uid_label, uid),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { deleteTarget = uid },
                                    modifier = Modifier.height(AppSpacing.minTouchTarget)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.keys_delete_icon),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(AppSpacing.space8))
                            keys.forEach { key ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = AppSpacing.space4),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.space16)
                                ) {
                                    key.keyA?.let {
                                        Text(
                                            text = "S${key.sector} A: $it",
                                            style = MonospaceTextStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    key.keyB?.let {
                                        Text(
                                            text = "S${key.sector} B: $it",
                                            style = MonospaceTextStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle(text = stringResource(R.string.local_dictionary))
            }

            if (dictionaryKeys.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.no_cached_keys),
                        icon = Icons.Default.Key
                    )
                }
            }

            items(dictionaryKeys) { key ->
                AppCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.space12)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = key,
                            style = MonospaceTextStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.keys_dictionary_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(AppSpacing.space16)) }
        }
    }

    deleteTarget?.let { uid ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_keys_confirm_title)) },
            text = { Text(stringResource(R.string.delete_keys_confirm, uid)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteKeysByUid(uid)
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

