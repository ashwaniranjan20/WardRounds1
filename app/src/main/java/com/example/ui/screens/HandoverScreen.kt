package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HandoverEntryEntity
import com.example.data.model.HandoverStatus
import com.example.data.model.IllnessSeverity
import com.example.data.model.PatientEntity
import com.example.data.model.ShiftType
import com.example.domain.pdf.ClinicalPdfExporter
import com.example.domain.pdf.PdfExportOutput
import com.example.ui.components.PdfPreviewExportDialog
import com.example.ui.components.WardRoundTopBar
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.OnLimeText
import com.example.ui.theme.StatusAlert
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.WardRoundViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HandoverFilterOption(val label: String) {
    ALL("All"),
    PENDING_SIGNOFF("Pending Sign-Off"),
    UNSTABLE("Unstable"),
    WATCHER("Watcher"),
    STABLE("Stable")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoverScreen(
    viewModel: WardRoundViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPatient: (String) -> Unit,
    initialPatientId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allHandovers by viewModel.allHandovers.collectAsStateWithLifecycle()
    val activePatients by viewModel.activePatients.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var selectedShift by remember { mutableStateOf(ShiftType.EVENING_TO_NIGHT) }
    var selectedFilter by remember { mutableStateOf(HandoverFilterOption.ALL) }

    var editorDraft by remember { mutableStateOf<HandoverEntryEntity?>(null) }
    var signOffTarget by remember { mutableStateOf<HandoverEntryEntity?>(null) }
    var pdfOutput by remember { mutableStateOf<PdfExportOutput?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }

    // If launched from a specific patient profile, open/prepare their handover automatically once
    var handledInitialPatient by remember { mutableStateOf(false) }
    LaunchedEffect(initialPatientId, activePatients, allHandovers) {
        if (!initialPatientId.isNullOrBlank() && !handledInitialPatient && activePatients.isNotEmpty()) {
            handledInitialPatient = true
            val existing = allHandovers.find { it.patientId == initialPatientId && it.shiftType == selectedShift }
                ?: allHandovers.find { it.patientId == initialPatientId }
            if (existing != null) {
                selectedShift = existing.shiftType
                editorDraft = existing
            } else {
                val pt = activePatients.find { it.id == initialPatientId }
                if (pt != null) {
                    editorDraft = viewModel.buildAutoPopulatedHandoverDraft(pt, todayDate, selectedShift)
                }
            }
        }
    }

    val shiftHandovers = remember(allHandovers, selectedShift) {
        allHandovers.filter { it.shiftType == selectedShift }
            .sortedWith(
                compareBy<HandoverEntryEntity> {
                    when (it.illnessSeverity) {
                        IllnessSeverity.UNSTABLE -> 0
                        IllnessSeverity.WATCHER -> 1
                        IllnessSeverity.STABLE -> 2
                    }
                }.thenBy { it.ward }.thenBy { it.bedNumber }
            )
    }

    val filteredHandovers = remember(shiftHandovers, selectedFilter) {
        when (selectedFilter) {
            HandoverFilterOption.ALL -> shiftHandovers
            HandoverFilterOption.PENDING_SIGNOFF -> shiftHandovers.filter { it.status != HandoverStatus.ACKNOWLEDGED }
            HandoverFilterOption.UNSTABLE -> shiftHandovers.filter { it.illnessSeverity == IllnessSeverity.UNSTABLE }
            HandoverFilterOption.WATCHER -> shiftHandovers.filter { it.illnessSeverity == IllnessSeverity.WATCHER }
            HandoverFilterOption.STABLE -> shiftHandovers.filter { it.illnessSeverity == IllnessSeverity.STABLE }
        }
    }

    val acknowledgedCount = shiftHandovers.count { it.status == HandoverStatus.ACKNOWLEDGED }
    val unstableCount = shiftHandovers.count { it.illnessSeverity == IllnessSeverity.UNSTABLE }
    val watcherCount = shiftHandovers.count { it.illnessSeverity == IllnessSeverity.WATCHER }

    if (pdfOutput != null) {
        PdfPreviewExportDialog(
            pdfOutput = pdfOutput!!,
            onDismiss = { pdfOutput = null }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WardRoundTopBar(
                title = "Shift Handover",
                subtitle = "SBAR • I-PASS Clinical Sign-Out",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val firstPt = activePatients.firstOrNull()
                    if (firstPt != null) {
                        scope.launch {
                            editorDraft = viewModel.buildAutoPopulatedHandoverDraft(firstPt, todayDate, selectedShift)
                        }
                    } else {
                        Toast.makeText(context, "No active inpatients available", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = ElectricLime,
                contentColor = OnLimeText,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "Prepare Handover", tint = OnLimeText) },
                text = { Text("New Handover", fontWeight = FontWeight.Bold, color = OnLimeText) },
                modifier = Modifier.testTag("fab_new_handover")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Shift Selector + PDF Export Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Shift Transition ($todayDate)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                onClick = {
                                    if (!isExportingPdf) {
                                        isExportingPdf = true
                                        scope.launch {
                                            try {
                                                pdfOutput = ClinicalPdfExporter.generateHandoverPacketPdf(
                                                    context = context,
                                                    handovers = shiftHandovers,
                                                    shiftDate = todayDate,
                                                    shiftType = selectedShift,
                                                    clinicianName = currentUser.name
                                                )
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "PDF export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isExportingPdf = false
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = ElectricLime,
                                contentColor = OnLimeText,
                                modifier = Modifier.testTag("btn_export_handover_pdf")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PictureAsPdf,
                                        contentDescription = "Export Handover PDF",
                                        tint = OnLimeText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isExportingPdf) "Exporting..." else "Export PDF",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OnLimeText
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShiftType.values().forEach { shift ->
                                val isSel = selectedShift == shift
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedShift = shift },
                                    label = {
                                        Text(
                                            text = shift.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Metrics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HandoverMetricPill("Patients", "${shiftHandovers.size}", MaterialTheme.colorScheme.primary)
                            HandoverMetricPill("Unstable", "$unstableCount", StatusAlert)
                            HandoverMetricPill("Watchers", "$watcherCount", StatusPending)
                            HandoverMetricPill("Signed Off", "$acknowledgedCount/${shiftHandovers.size}", StatusSuccess)
                        }
                    }
                }
            }

            // Severity & Status Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HandoverFilterOption.values().forEach { opt ->
                        val isSel = selectedFilter == opt
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedFilter = opt },
                            label = {
                                Text(
                                    text = opt.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            if (filteredHandovers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No Handovers for ${selectedShift.label}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Auto-generate SBAR & I-PASS drafts from the latest daily rounds, vitals, and pending labs for all active ward patients.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    viewModel.seedMissingShiftHandovers(todayDate, selectedShift) { count ->
                                        Toast.makeText(context, "Prepared $count patient handover draft(s)", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricLime,
                                    contentColor = OnLimeText
                                ),
                                modifier = Modifier.testTag("btn_auto_seed_handovers")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = OnLimeText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto-Prepare Ward Handovers", fontWeight = FontWeight.Bold, color = OnLimeText)
                            }
                        }
                    }
                }
            } else {
                items(filteredHandovers, key = { it.id }) { handover ->
                    HandoverPatientCard(
                        handover = handover,
                        onOpenPatient = { onNavigateToPatient(handover.patientId) },
                        onEdit = { editorDraft = handover },
                        onAcknowledgeClick = { signOffTarget = handover },
                        onExportSinglePdf = {
                            scope.launch {
                                pdfOutput = ClinicalPdfExporter.generateHandoverPacketPdf(
                                    context = context,
                                    handovers = listOf(handover),
                                    shiftDate = handover.shiftDate,
                                    shiftType = handover.shiftType,
                                    clinicianName = currentUser.name
                                )
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // I-PASS / SBAR Editor Bottom Sheet
    if (editorDraft != null) {
        HandoverEditorSheet(
            initialDraft = editorDraft!!,
            activePatients = activePatients,
            onPatientSwitch = { newPt ->
                viewModel.buildAutoPopulatedHandoverDraft(newPt, todayDate, selectedShift)
            },
            onDismiss = { editorDraft = null },
            onSave = { updated ->
                viewModel.saveHandover(updated) {
                    editorDraft = null
                    Toast.makeText(context, "Handover saved for ${updated.patientName}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Incoming Clinician Sign-Off Confirmation Dialog
    if (signOffTarget != null) {
        val target = signOffTarget!!
        var readBackConfirmed by remember(target.id) { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { signOffTarget = null },
            title = {
                Text(
                    text = "Confirm Handover Sign-Off",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Patient: ${target.patientName} (${target.uhid}) • ${target.ward} Bed ${target.bedNumber}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Severity: ${target.illnessSeverity.label} • Outgoing: ${target.outgoingClinician} (${target.outgoingTime})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { readBackConfirmed = !readBackConfirmed }
                            .padding(8.dp)
                    ) {
                        Checkbox(
                            checked = readBackConfirmed,
                            onCheckedChange = { readBackConfirmed = it },
                            modifier = Modifier.testTag("chk_synthesis_readback")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "I-PASS Synthesis Read-Back: I have reviewed illness severity, action list, pending labs, and overnight contingencies.",
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.acknowledgeHandover(target.id, readBackConfirmed) {
                            signOffTarget = null
                            Toast.makeText(context, "Signed off by ${currentUser.name}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLime,
                        contentColor = OnLimeText
                    ),
                    modifier = Modifier.testTag("btn_confirm_handover_signoff")
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = OnLimeText, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sign Off (${currentUser.name})", fontWeight = FontWeight.Bold, color = OnLimeText)
                }
            },
            dismissButton = {
                TextButton(onClick = { signOffTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HandoverMetricPill(label: String, count: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = tint
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HandoverPatientCard(
    handover: HandoverEntryEntity,
    onOpenPatient: () -> Unit,
    onEdit: () -> Unit,
    onAcknowledgeClick: () -> Unit,
    onExportSinglePdf: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(handover.illnessSeverity != IllnessSeverity.STABLE) }

    val severityColor = when (handover.illnessSeverity) {
        IllnessSeverity.UNSTABLE -> StatusAlert
        IllnessSeverity.WATCHER -> StatusPending
        IllnessSeverity.STABLE -> StatusSuccess
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("handover_card_${handover.patientId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (handover.illnessSeverity == IllnessSeverity.UNSTABLE) 1.5.dp else 1.dp,
            color = if (handover.illnessSeverity == IllnessSeverity.UNSTABLE) StatusAlert.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top Row: Patient + Severity Badge + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenPatient() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(severityColor.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = handover.illnessSeverity.label.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (handover.illnessSeverity == IllnessSeverity.WATCHER) Color(0xFF9A6700) else severityColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = handover.patientName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${handover.uhid} • ${handover.ward} Bed ${handover.bedNumber}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onExportSinglePdf, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = "Export Patient Handover PDF",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Handover",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                }
            }

            // Always show concise Situation preview
            SbarRow(tag = "S", title = "Situation", content = handover.situation)

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (handover.background.isNotBlank()) {
                        SbarRow(tag = "B", title = "Background", content = handover.background)
                    }
                    if (handover.assessment.isNotBlank()) {
                        SbarRow(tag = "A", title = "Assessment & Vitals", content = handover.assessment)
                    }
                    if (handover.recommendationAndActions.isNotBlank()) {
                        SbarRow(tag = "R", title = "Action List (I-PASS)", content = handover.recommendationAndActions)
                    }
                    if (handover.contingencyPlan.isNotBlank()) {
                        Surface(
                            color = StatusPending.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StatusPending.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFF9A6700),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Contingency Plan (If–Then)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = handover.contingencyPlan,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    if (handover.pendingCriticalItems.isNotBlank() && handover.pendingCriticalItems != "None urgent") {
                        Text(
                            text = "Pending Items: ${handover.pendingCriticalItems}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusAlert
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Sign-off footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Outgoing: ${handover.outgoingClinician} (${handover.outgoingTime})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (handover.status == HandoverStatus.ACKNOWLEDGED) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Signed off: ${handover.incomingClinician ?: "--"} (${handover.acknowledgedAt ?: "--"})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusSuccess
                            )
                        }
                    }
                }

                if (handover.status != HandoverStatus.ACKNOWLEDGED) {
                    Button(
                        onClick = onAcknowledgeClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricLime,
                            contentColor = OnLimeText
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("btn_signoff_handover_${handover.patientId}")
                    ) {
                        Icon(Icons.Default.HowToReg, contentDescription = null, tint = OnLimeText, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sign Off", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnLimeText)
                    }
                }
            }
        }
    }
}

@Composable
private fun SbarRow(tag: String, title: String, content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = content,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandoverEditorSheet(
    initialDraft: HandoverEntryEntity,
    activePatients: List<PatientEntity>,
    onPatientSwitch: suspend (PatientEntity) -> HandoverEntryEntity,
    onDismiss: () -> Unit,
    onSave: (HandoverEntryEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var currentEntry by remember(initialDraft.id) { mutableStateOf(initialDraft) }
    var severity by remember(initialDraft.id) { mutableStateOf(initialDraft.illnessSeverity) }
    var situation by remember(initialDraft.id) { mutableStateOf(initialDraft.situation) }
    var background by remember(initialDraft.id) { mutableStateOf(initialDraft.background) }
    var assessment by remember(initialDraft.id) { mutableStateOf(initialDraft.assessment) }
    var actions by remember(initialDraft.id) { mutableStateOf(initialDraft.recommendationAndActions) }
    var contingency by remember(initialDraft.id) { mutableStateOf(initialDraft.contingencyPlan) }
    var pendingItems by remember(initialDraft.id) { mutableStateOf(initialDraft.pendingCriticalItems) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Prepare SBAR / I-PASS Handover",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${currentEntry.patientName} (${currentEntry.uhid}) • ${currentEntry.shiftType.label}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedButton(
                    onClick = {
                        val pt = activePatients.find { it.id == currentEntry.patientId }
                        if (pt != null) {
                            scope.launch {
                                val fresh = onPatientSwitch(pt)
                                severity = fresh.illnessSeverity
                                situation = fresh.situation
                                background = fresh.background
                                assessment = fresh.assessment
                                actions = fresh.recommendationAndActions
                                contingency = fresh.contingencyPlan
                                pendingItems = fresh.pendingCriticalItems
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto-Fill Chart", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Patient Quick Selector
            if (activePatients.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activePatients.forEach { pt ->
                        val isSel = pt.id == currentEntry.patientId
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                scope.launch {
                                    val fresh = onPatientSwitch(pt)
                                    currentEntry = fresh
                                    severity = fresh.illnessSeverity
                                    situation = fresh.situation
                                    background = fresh.background
                                    assessment = fresh.assessment
                                    actions = fresh.recommendationAndActions
                                    contingency = fresh.contingencyPlan
                                    pendingItems = fresh.pendingCriticalItems
                                }
                            },
                            label = { Text("${pt.name} (${pt.bedNumber})", fontSize = 11.sp) }
                        )
                    }
                }
            }

            Text("I-PASS Illness Severity", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IllnessSeverity.values().forEach { sev ->
                    val isSel = severity == sev
                    FilterChip(
                        selected = isSel,
                        onClick = { severity = sev },
                        label = { Text(sev.label, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (sev) {
                                IllnessSeverity.UNSTABLE -> StatusAlert
                                IllnessSeverity.WATCHER -> StatusPending
                                IllnessSeverity.STABLE -> StatusSuccess
                            },
                            selectedLabelColor = if (sev == IllnessSeverity.WATCHER) Color.Black else Color.White
                        )
                    )
                }
            }

            OutlinedTextField(
                value = situation,
                onValueChange = { situation = it },
                label = { Text("S — Situation & Diagnosis") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 112.dp),
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = background,
                onValueChange = { background = it },
                label = { Text("B — Background, Allergies & Treatment") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = assessment,
                onValueChange = { assessment = it },
                label = { Text("A — Assessment & Latest Vitals/Labs") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = actions,
                onValueChange = { actions = it },
                label = { Text("R — Action List (To-Do for Incoming Team)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = contingency,
                onValueChange = { contingency = it },
                label = { Text("Contingency Plan (If–Then Triggers)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = pendingItems,
                onValueChange = { pendingItems = it },
                label = { Text("Pending Critical Results / Consults") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSave(
                            currentEntry.copy(
                                illnessSeverity = severity,
                                situation = situation,
                                background = background,
                                assessment = assessment,
                                recommendationAndActions = actions,
                                contingencyPlan = contingency,
                                pendingCriticalItems = pendingItems,
                                status = HandoverStatus.DRAFT
                            )
                        )
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Draft")
                }

                Button(
                    onClick = {
                        onSave(
                            currentEntry.copy(
                                illnessSeverity = severity,
                                situation = situation,
                                background = background,
                                assessment = assessment,
                                recommendationAndActions = actions,
                                contingencyPlan = contingency,
                                pendingCriticalItems = pendingItems,
                                status = HandoverStatus.HANDED_OVER,
                                incomingClinician = null,
                                acknowledgedAt = null,
                                synthesisConfirmed = false
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricLime,
                        contentColor = OnLimeText
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("btn_submit_handover"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Submit for Handover", fontWeight = FontWeight.Bold, color = OnLimeText)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
