package com.example.ui.ocr

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.ocr.ClinicalCategory
import com.example.domain.ocr.ConflictResolutionChoice
import com.example.domain.ocr.ExtractedClinicalField
import com.example.domain.ocr.OcrExtractionResult
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.OnLimeText
import com.example.ui.theme.StatusAlert
import com.example.ui.theme.StatusPending

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrReviewDraftScreen(
    imageUri: Uri,
    rotationDegrees: Int,
    extractionResult: OcrExtractionResult,
    onApplyFields: (Map<String, String>) -> Unit,
    onDiscard: () -> Unit
) {
    BackHandler { onDiscard() }

    val fieldStates = remember {
        mutableStateListOf<ExtractedClinicalField>().apply {
            addAll(extractionResult.fields)
        }
    }

    var showRawText by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Review OCR Draft",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${fieldStates.count { it.isAccepted }} fields selected for import",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDiscard, modifier = Modifier.testTag("btn_ocr_back_discard")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Discard and Go Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    Button(
                        onClick = {
                            val resultMap = mutableMapOf<String, String>()
                            fieldStates.filter { it.isAccepted }.forEach { field ->
                                val finalVal = field.finalEffectiveValue
                                if (finalVal.isNotBlank()) {
                                    resultMap[field.key] = finalVal
                                }
                            }
                            onApplyFields(resultMap)
                        },
                        enabled = fieldStates.any { it.isAccepted },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricLime,
                            contentColor = OnLimeText
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_apply_ocr_confirmed_fields")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Apply (${fieldStates.count { it.isAccepted }})",
                            fontWeight = FontWeight.Bold,
                            color = OnLimeText
                        )
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isWideScreen = maxWidth >= 700.dp

            if (isWideScreen) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.9f))
                    ) {
                        ZoomableImagePane(
                            imageUri = imageUri,
                            rotationDegrees = rotationDegrees,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    ) {
                        ReviewFieldsPane(
                            fieldStates = fieldStates,
                            rawText = extractionResult.rawText,
                            showRawText = showRawText,
                            onToggleRawText = { showRawText = !showRawText },
                            onFieldUpdated = { index, updated ->
                                fieldStates[index] = updated
                            },
                            onRemoveField = { index ->
                                fieldStates.removeAt(index)
                            }
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.42f)
                            .background(Color.Black.copy(alpha = 0.9f))
                    ) {
                        ZoomableImagePane(
                            imageUri = imageUri,
                            rotationDegrees = rotationDegrees,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.58f)
                    ) {
                        ReviewFieldsPane(
                            fieldStates = fieldStates,
                            rawText = extractionResult.rawText,
                            showRawText = showRawText,
                            onToggleRawText = { showRawText = !showRawText },
                            onFieldUpdated = { index, updated ->
                                fieldStates[index] = updated
                            },
                            onRemoveField = { index ->
                                fieldStates.removeAt(index)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImagePane(
    imageUri: Uri,
    rotationDegrees: Int,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 5f)
        offset += panChange
    }

    Box(modifier = modifier.clip(RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = imageUri,
            contentDescription = "Source Clinical Document",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotationDegrees.toFloat())
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = state)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { scale = (scale + 0.3f).coerceAtMost(5f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
            }
            IconButton(
                onClick = { scale = (scale - 0.3f).coerceAtLeast(0.8f) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
            }
            Text(
                text = "${(scale * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        scale = 1f
                        offset = Offset.Zero
                    }
                    .padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun ReviewFieldsPane(
    fieldStates: List<ExtractedClinicalField>,
    rawText: String,
    showRawText: Boolean,
    onToggleRawText: () -> Unit,
    onFieldUpdated: (Int, ExtractedClinicalField) -> Unit,
    onRemoveField: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Assistive transcription draft. Verify numbers and units against the document before confirming.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val categories = listOf(
            ClinicalCategory.DEMOGRAPHICS,
            ClinicalCategory.VITALS,
            ClinicalCategory.INVESTIGATIONS,
            ClinicalCategory.CLINICAL_NOTES,
            ClinicalCategory.OTHER
        )

        for (category in categories) {
            val matching = fieldStates.mapIndexed { index, field -> index to field }
                .filter { it.second.category == category }

            if (matching.isNotEmpty()) {
                item {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(matching, key = { it.second.id }) { (index, field) ->
                    FieldReviewCard(
                        field = field,
                        onUpdate = { updated -> onFieldUpdated(index, updated) },
                        onDelete = { onRemoveField(index) }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleRawText() }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Raw OCR Transcription",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (showRawText) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = showRawText) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = rawText.ifBlank { "No text recognized." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FieldReviewCard(
    field: ExtractedClinicalField,
    onUpdate: (ExtractedClinicalField) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (field.isAccepted) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = BorderStroke(
            width = if (field.hasConflict) 1.5.dp else 1.dp,
            color = when {
                field.hasConflict -> StatusAlert
                field.isLowConfidence -> StatusPending
                field.isAccepted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_ocr_field_${field.key}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = field.isAccepted,
                    onCheckedChange = { checked ->
                        onUpdate(field.copy(isAccepted = checked))
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("chk_ocr_accept_${field.key}")
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = field.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (field.unit != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${field.unit})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (field.referenceRange != null) {
                        Text(
                            text = "Normal ref: ${field.referenceRange}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove field",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (field.isLowConfidence) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = StatusPending.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusAlert,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = field.uncertaintyReason ?: "Low confidence detection. Please verify.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (field.hasConflict) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = StatusAlert.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StatusAlert.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Conflict with Existing Form Value",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatusAlert
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Current in Form: \"${field.currentFormValue}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Extracted from Image: \"${field.extractedValue}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ResolutionChip(
                                title = "Use OCR",
                                isSelected = field.conflictChoice == ConflictResolutionChoice.USE_OCR,
                                onClick = {
                                    onUpdate(field.copy(conflictChoice = ConflictResolutionChoice.USE_OCR))
                                }
                            )
                            ResolutionChip(
                                title = "Keep Form",
                                isSelected = field.conflictChoice == ConflictResolutionChoice.KEEP_EXISTING,
                                onClick = {
                                    onUpdate(field.copy(conflictChoice = ConflictResolutionChoice.KEEP_EXISTING))
                                }
                            )
                            ResolutionChip(
                                title = "Append Both",
                                isSelected = field.conflictChoice == ConflictResolutionChoice.APPEND_BOTH,
                                onClick = {
                                    onUpdate(field.copy(conflictChoice = ConflictResolutionChoice.APPEND_BOTH))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = field.userEditedValue,
                onValueChange = { newValue ->
                    onUpdate(field.copy(userEditedValue = newValue))
                },
                enabled = field.isAccepted,
                singleLine = field.category != ClinicalCategory.CLINICAL_NOTES,
                maxLines = if (field.category == ClinicalCategory.CLINICAL_NOTES) 4 else 1,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_ocr_edit_${field.key}"),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun ResolutionChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
