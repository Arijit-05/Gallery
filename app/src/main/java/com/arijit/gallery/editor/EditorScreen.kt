package com.arijit.gallery.editor

import android.app.Activity
import android.content.IntentSender
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arijit.gallery.editor.engine.ImageEditRenderer
import com.arijit.gallery.editor.model.AdjustmentType
import com.arijit.gallery.editor.model.CropAspectRatio
import com.arijit.gallery.editor.model.DoodlePath
import com.arijit.gallery.editor.model.DoodleText
import com.arijit.gallery.editor.model.EditorTab
import com.arijit.gallery.editor.model.EditorUiState
import com.arijit.gallery.editor.model.PhotoFilter
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    uiState: EditorUiState,
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showSaveMenu by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var currentDoodlePoints = remember { mutableStateListOf<Offset>() }

    val intentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onIntentSenderFinished(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- TOP BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        tint = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = uiState.canUndo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Undo",
                            tint = if (uiState.canUndo) Color.White else Color.White.copy(alpha = 0.35f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = uiState.canRedo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Redo,
                            contentDescription = "Redo",
                            tint = if (uiState.canRedo) Color.White else Color.White.copy(alpha = 0.35f)
                        )
                    }
                    IconButton(onClick = { viewModel.resetAll() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reset",
                            tint = Color.White
                        )
                    }
                }

                Box {
                    Button(
                        onClick = { showSaveMenu = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }

                    DropdownMenu(
                        expanded = showSaveMenu,
                        onDismissRequest = { showSaveMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save as copy") },
                            onClick = {
                                showSaveMenu = false
                                viewModel.saveAsCopy { onBack() }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save & overwrite") },
                            onClick = {
                                showSaveMenu = false
                                viewModel.overwrite(
                                    onRequiresConfirmation = { sender ->
                                        intentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                    },
                                    onSuccess = { onBack() }
                                )
                            }
                        )
                    }
                }
            }

            // --- VIEWPORT / LIVE PREVIEW ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val preview = viewModel.previewBitmap
                if (preview != null) {
                    val colorMatrix = remember(uiState.adjustments, uiState.selectedFilter, uiState.filterIntensity) {
                        ImageEditRenderer.buildColorMatrix(
                            uiState.adjustments,
                            uiState.selectedFilter,
                            uiState.filterIntensity
                        )
                    }

                    val rotation = uiState.cropState.rotationDegrees.toFloat() + uiState.cropState.straightenDegrees
                    val scaleX = if (uiState.cropState.flipHorizontal) -1f else 1f
                    val scaleY = if (uiState.cropState.flipVertical) -1f else 1f

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(uiState.activeTab, uiState.activeColor, uiState.activeBrushSize) {
                                if (uiState.activeTab == EditorTab.Markup) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentDoodlePoints.clear()
                                            currentDoodlePoints.add(
                                                Offset(
                                                    offset.x / size.width.toFloat(),
                                                    offset.y / size.height.toFloat()
                                                )
                                            )
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentDoodlePoints.add(
                                                Offset(
                                                    change.position.x / size.width.toFloat(),
                                                    change.position.y / size.height.toFloat()
                                                )
                                            )
                                        },
                                        onDragEnd = {
                                            if (currentDoodlePoints.size >= 2) {
                                                viewModel.addMarkupPath(
                                                    DoodlePath(
                                                        points = currentDoodlePoints.toList(),
                                                        color = uiState.activeColor,
                                                        strokeWidth = uiState.activeBrushSize
                                                    )
                                                )
                                            }
                                            currentDoodlePoints.clear()
                                        }
                                    )
                                }
                            }
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height

                        // Calculate aspect fit sizing for preview bitmap
                        val bmpW = preview.width.toFloat()
                        val bmpH = preview.height.toFloat()
                        val scale = minOf(canvasW / bmpW, canvasH / bmpH)
                        val drawW = bmpW * scale
                        val drawH = bmpH * scale
                        val left = (canvasW - drawW) / 2f
                        val top = (canvasH - drawH) / 2f

                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            native.save()
                            native.translate(left + drawW / 2f, top + drawH / 2f)
                            native.rotate(rotation)
                            native.scale(scaleX, scaleY)
                            native.translate(-(drawW / 2f), -(drawH / 2f))

                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                                colorFilter = ColorMatrixColorFilter(colorMatrix)
                            }
                            val destRect = android.graphics.Rect(0, 0, drawW.roundToInt(), drawH.roundToInt())
                            native.drawBitmap(preview, null, destRect, paint)

                            native.restore()

                            // Draw live and saved markup paths
                            val allPaths = uiState.markupPaths + if (currentDoodlePoints.size >= 2) {
                                listOf(
                                    DoodlePath(
                                        points = currentDoodlePoints.toList(),
                                        color = uiState.activeColor,
                                        strokeWidth = uiState.activeBrushSize
                                    )
                                )
                            } else emptyList()

                            for (path in allPaths) {
                                if (path.points.size < 2) continue
                                val pPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = path.color.toInt()
                                    strokeWidth = path.strokeWidth * (canvasW / 1080f).coerceAtLeast(1f)
                                    style = Paint.Style.STROKE
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                }
                                val p = android.graphics.Path()
                                val pt0 = path.points.first()
                                p.moveTo(pt0.x * canvasW, pt0.y * canvasH)
                                for (i in 1 until path.points.size) {
                                    val pt = path.points[i]
                                    p.lineTo(pt.x * canvasW, pt.y * canvasH)
                                }
                                native.drawPath(p, pPaint)
                            }

                            // Draw markup texts
                            for (txt in uiState.markupTexts) {
                                val tPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = txt.color.toInt()
                                    textSize = txt.fontSize * (canvasW / 1080f).coerceAtLeast(1f)
                                    isFakeBoldText = true
                                }
                                native.drawText(txt.text, txt.xPercent * canvasW, txt.yPercent * canvasH, tPaint)
                            }
                        }

                        // If in Crop tab, draw rule of thirds grid overlay
                        if (uiState.activeTab == EditorTab.Crop) {
                            val cropRect = uiState.cropState.cropRect
                            val cropLeft = canvasW * cropRect.left
                            val cropTop = canvasH * cropRect.top
                            val cropRight = canvasW * cropRect.right
                            val cropBottom = canvasH * cropRect.bottom
                            val cropW = cropRight - cropLeft
                            val cropH = cropBottom - cropTop

                            // Dimmed outside
                            drawRect(
                                color = Color.Black.copy(alpha = 0.5f),
                                topLeft = Offset.Zero,
                                size = Size(canvasW, cropTop)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.5f),
                                topLeft = Offset(0f, cropBottom),
                                size = Size(canvasW, canvasH - cropBottom)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.5f),
                                topLeft = Offset(0f, cropTop),
                                size = Size(cropLeft, cropH)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.5f),
                                topLeft = Offset(cropRight, cropTop),
                                size = Size(canvasW - cropRight, cropH)
                            )

                            // Grid border
                            drawRect(
                                color = Color.White.copy(alpha = 0.85f),
                                topLeft = Offset(cropLeft, cropTop),
                                size = Size(cropW, cropH),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                            // 3x3 grid lines
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(cropLeft + cropW / 3f, cropTop),
                                end = Offset(cropLeft + cropW / 3f, cropBottom),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(cropLeft + cropW * 2f / 3f, cropTop),
                                end = Offset(cropLeft + cropW * 2f / 3f, cropBottom),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(cropLeft, cropTop + cropH / 3f),
                                end = Offset(cropRight, cropTop + cropH / 3f),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(cropLeft, cropTop + cropH * 2f / 3f),
                                end = Offset(cropRight, cropTop + cropH * 2f / 3f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            // --- TOOL ADJUSTMENT CONTROLS & SUB-BAR ---
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    // 1. Tool context slider/controls
                    when (uiState.activeTab) {
                        EditorTab.Crop -> {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Straighten: ${uiState.cropState.straightenDegrees.roundToInt()}°",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(onClick = { viewModel.rotate90() }) {
                                            Icon(
                                                imageVector = Icons.Rounded.RotateRight,
                                                contentDescription = "Rotate 90"
                                            )
                                        }
                                        IconButton(onClick = { viewModel.flipHorizontal() }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Flip,
                                                contentDescription = "Flip"
                                            )
                                        }
                                    }
                                }
                                Slider(
                                    value = uiState.cropState.straightenDegrees,
                                    onValueChange = { viewModel.setStraighten(it) },
                                    valueRange = -45f..45f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CropAspectRatio.entries.forEach { ratio ->
                                        val isSelected = uiState.cropState.aspectRatio == ratio
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.setCropAspectRatio(ratio) },
                                            label = { Text(ratio.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        EditorTab.Adjust -> {
                            val activeAdj = uiState.activeAdjustment
                            val currentValue = uiState.adjustments[activeAdj] ?: activeAdj.defaultValue
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = activeAdj.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${currentValue.roundToInt()}${activeAdj.unit}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = currentValue,
                                    onValueChange = { viewModel.updateAdjustment(activeAdj, it) },
                                    valueRange = activeAdj.min..activeAdj.max,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AdjustmentType.entries.forEach { adj ->
                                        val isSelected = uiState.activeAdjustment == adj
                                        val valForAdj = uiState.adjustments[adj] ?: adj.defaultValue
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.setActiveAdjustment(adj) },
                                            label = {
                                                Text(
                                                    if (valForAdj != 0f) "${adj.label} • ${valForAdj.roundToInt()}"
                                                    else adj.label
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        EditorTab.Filters -> {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${uiState.selectedFilter.name} Intensity",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = "${(uiState.filterIntensity * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = uiState.filterIntensity,
                                    onValueChange = { viewModel.setFilterIntensity(it) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PhotoFilter.ALL_FILTERS.forEach { filter ->
                                        val isSelected = uiState.selectedFilter == filter
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { viewModel.selectFilter(filter) }
                                                .padding(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .background(
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceVariant,
                                                        shape = CircleShape
                                                    )
                                                    .border(
                                                        width = if (isSelected) 2.dp else 0.dp,
                                                        color = Color.White,
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = filter.name.take(2).uppercase(),
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = filter.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        EditorTab.Markup -> {
                            val colors = listOf(
                                0xFFFFFFFF, 0xFF000000, 0xFFFF3B30, 0xFFFF9500,
                                0xFFFFCC00, 0xFF34C759, 0xFF00C7BE, 0xFF007AFF, 0xFFAF52DE
                            )
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Brush Size: ${uiState.activeBrushSize.roundToInt()}px",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    TextButton(onClick = { showTextDialog = true }) {
                                        Icon(
                                            imageVector = Icons.Rounded.TextFields,
                                            contentDescription = "Add Text",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Text")
                                    }
                                }
                                Slider(
                                    value = uiState.activeBrushSize,
                                    onValueChange = { viewModel.setActiveBrushSize(it) },
                                    valueRange = 4f..48f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    colors.forEach { colorValue ->
                                        val isSelected = uiState.activeColor == colorValue
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorValue))
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.setActiveColor(colorValue) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. Primary bottom tab bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EditorTab.entries.forEach { tab ->
                            val isSelected = uiState.activeTab == tab
                            val icon = when (tab) {
                                EditorTab.Crop -> Icons.Rounded.Crop
                                EditorTab.Adjust -> Icons.Rounded.Tune
                                EditorTab.Filters -> Icons.Rounded.Palette
                                EditorTab.Markup -> Icons.Rounded.TextFields
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setActiveTab(tab) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 120.dp)
        )
    }

    if (showTextDialog) {
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text to Photo") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Enter text...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.addMarkupText(
                                DoodleText(
                                    id = java.util.UUID.randomUUID().toString(),
                                    text = textInput.trim(),
                                    xPercent = 0.5f,
                                    yPercent = 0.5f,
                                    color = uiState.activeColor,
                                    fontSize = 42f
                                )
                            )
                        }
                        showTextDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

