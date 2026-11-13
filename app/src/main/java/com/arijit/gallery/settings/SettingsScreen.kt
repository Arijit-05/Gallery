package com.arijit.gallery.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arijit.gallery.core.common.Formatters
import com.arijit.gallery.core.common.rememberHapticController
import com.arijit.gallery.core.domain.model.Album
import com.arijit.gallery.core.domain.model.SortOrder
import com.arijit.gallery.core.domain.model.ThemeMode
import com.arijit.gallery.core.domain.model.TimelineGranularity
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetGridColumns: (Int) -> Unit,
    onSetGranularity: (TimelineGranularity) -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
    onSetVideoAutoplay: (Boolean) -> Unit,
    onToggleAlbumHidden: (Album) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenVault: (() -> Unit)? = null,
    onClearCache: () -> Unit,
    onClearStatusMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberHapticController()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showGranularityDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showHiddenFoldersDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onClearStatusMessage()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.click()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsSectionHeader("Appearance")
            }

            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Palette,
                        title = "Theme",
                        subtitle = themeLabel(uiState.settings.themeMode),
                        onClick = { showThemeDialog = true }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsSwitchRow(
                            title = "Dynamic color",
                            subtitle = "Match system wallpaper colors",
                            checked = uiState.settings.dynamicColor,
                            onCheckedChange = onSetDynamicColor
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader("Timeline & Viewing")
            }

            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.GridView,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 14.dp)
                                )
                                Column {
                                    Text(
                                        text = "Default grid density",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "${uiState.settings.gridColumns} columns",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Slider(
                            value = uiState.settings.gridColumns.toFloat(),
                            onValueChange = { onSetGridColumns(it.roundToInt()) },
                            valueRange = 2f..6f,
                            steps = 3
                        )
                    }

                    SettingsRow(
                        icon = Icons.Rounded.Sort,
                        title = "Timeline grouping",
                        subtitle = granularityLabel(uiState.settings.timelineGranularity),
                        onClick = { showGranularityDialog = true }
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Sort,
                        title = "Sort order",
                        subtitle = sortLabel(uiState.settings.sortOrder),
                        onClick = { showSortDialog = true }
                    )
                    SettingsSwitchRow(
                        icon = Icons.Rounded.PlayCircleOutline,
                        title = "Auto-play videos",
                        subtitle = "Start playing videos when opened in Viewer",
                        checked = uiState.settings.videoAutoplay,
                        onCheckedChange = onSetVideoAutoplay
                    )
                }
            }

            item {
                SettingsSectionHeader("Library & Storage")
            }

            item {
                val hiddenCount = uiState.albums.count { it.isHiddenFromTimeline }
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.VisibilityOff,
                        title = "Hidden folders",
                        subtitle = if (hiddenCount == 0) {
                            "No folders hidden from Photos timeline"
                        } else {
                            "$hiddenCount folder(s) hidden from Photos timeline"
                        },
                        onClick = { showHiddenFoldersDialog = true }
                    )
                    SettingsRow(
                        icon = Icons.Rounded.DeleteOutline,
                        title = "Recently Deleted",
                        subtitle = "Manage trashed photos & videos (30-day retention)",
                        onClick = onOpenTrash
                    )
                    SettingsRow(
                        icon = Icons.Rounded.CleaningServices,
                        title = "Clear thumbnail & motion cache",
                        subtitle = "Using ${Formatters.formatFileSize(uiState.cacheSizeBytes)}",
                        onClick = onClearCache
                    )
                }
            }

            item {
                SettingsSectionHeader("Privacy & About")
            }

            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Lock,
                        title = "Private Safe & Biometric Lock",
                        subtitle = "Hardware Keystore AES-256 encrypted media safe",
                        onClick = onOpenVault
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Info,
                        title = "Gallery",
                        subtitle = "100% offline • No network permission • No tracking",
                        onClick = null
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = uiState.settings.themeMode,
            labelFor = ::themeLabel,
            onSelect = {
                showThemeDialog = false
                onSetThemeMode(it)
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showGranularityDialog) {
        SingleChoiceDialog(
            title = "Timeline grouping",
            options = TimelineGranularity.entries,
            selected = uiState.settings.timelineGranularity,
            labelFor = ::granularityLabel,
            onSelect = {
                showGranularityDialog = false
                onSetGranularity(it)
            },
            onDismiss = { showGranularityDialog = false }
        )
    }

    if (showSortDialog) {
        SingleChoiceDialog(
            title = "Sort order",
            options = SortOrder.entries,
            selected = uiState.settings.sortOrder,
            labelFor = ::sortLabel,
            onSelect = {
                showSortDialog = false
                onSetSortOrder(it)
            },
            onDismiss = { showSortDialog = false }
        )
    }

    if (showHiddenFoldersDialog) {
        AlertDialog(
            onDismissRequest = { showHiddenFoldersDialog = false },
            title = { Text("Hide folders from Photos") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(uiState.albums, key = { it.id }) { album ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleAlbumHidden(album) }
                                .padding(vertical = 6.dp)
                        ) {
                            Checkbox(
                                checked = album.isHiddenFromTimeline,
                                onCheckedChange = { onToggleAlbumHidden(album) }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(text = album.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = if (album.itemCount == 1) "1 item" else "${album.itemCount} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHiddenFoldersDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?
) {
    val haptics = rememberHapticController()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = {
                            haptics.click()
                            onClick()
                        })
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 14.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    val haptics = rememberHapticController()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.toggle()
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.toggle()
                onCheckedChange(it)
            }
        )
    }
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberHapticController()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.click()
                                onSelect(option)
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = {
                                haptics.click()
                                onSelect(option)
                            }
                        )
                        Text(
                            text = labelFor(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptics.click()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED Black"
}

private fun granularityLabel(granularity: TimelineGranularity): String = when (granularity) {
    TimelineGranularity.DAY -> "Day"
    TimelineGranularity.MONTH -> "Month"
    TimelineGranularity.YEAR -> "Year"
}

private fun sortLabel(sortOrder: SortOrder): String = when (sortOrder) {
    SortOrder.DATE_TAKEN_DESC -> "Date taken (newest first)"
    SortOrder.DATE_TAKEN_ASC -> "Date taken (oldest first)"
    SortOrder.DATE_MODIFIED_DESC -> "Date modified (newest first)"
}

