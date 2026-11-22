package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import com.example.domain.ocr.OcrDocumentType
import com.example.domain.ocr.OcrExtractionResult
import com.example.ui.components.OcrScanActionChip
import com.example.ui.ocr.OcrImageCaptureDialog
import com.example.ui.ocr.OcrReviewDraftScreen
import com.example.data.model.RoundEntryEntity
import com.example.data.model.RoundType
import com.example.data.model.VitalObservationEntity
import com.example.ui.components.CollapsibleRoundSection
import com.example.ui.components.RoundExpandCollapseControls
import com.example.ui.components.WardRoundTopBar
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.OnLimeText
import com.example.ui.theme.StatusAlert
import com.example.ui.theme.StatusPending
import com.example.ui.viewmodel.WardRoundViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRoundScreen(
    patientId: String,
    viewModel: WardRoundViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val patientState by viewModel.getPatient(patientId).collectAsStateWithLifecycle(initialValue = null)
    val patient = patientState ?: return
    val rounds by viewModel.getRoundsForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val orders by viewModel.getOrdersForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())
    val results by viewModel.getResultsForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())

    val currentDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val currentTimeStr = remember { SimpleDateFormat("HH:mm", Locale.US).format(Date()) }

    // Section 1 - Round & Patient Metadata
    var roundDate by remember { mutableStateOf(currentDateStr) }
    var roundTime by remember { mutableStateOf(currentTimeStr) }
    var roundType by remember { mutableStateOf(RoundType.MORNING) }
    val hospitalDay = remember(patient.admissionDate, roundDate) {
        viewModel.calculateHospitalDay(patient.admissionDate, roundDate)
    }

    // Section 2 - Today's Clinical Status
    var currentCondition by remember { mutableStateOf("Stable, alert, oriented") }
    var currentComplaints by remember { mutableStateOf("") }
    var symptomProgression by remember { mutableStateOf("") }
    var feverEvents by remember { mutableStateOf("") }

    // Section 3 - Vital Signs
    var bpSystolic by remember { mutableStateOf("") }
    var bpDiastolic by remember { mutableStateOf("") }
    var pulseRate by remember { mutableStateOf("") }
    var respiratoryRate by remember { mutableStateOf("") }
    var temperatureF by remember { mutableStateOf("") }
    var spO2 by remember { mutableStateOf("") }
    var o2Delivery by remember { mutableStateOf("Room Air") }
    var o2Flow by remember { mutableStateOf("") }
    var bloodGlucose by remember { mutableStateOf("") }
    var painScore by remember { mutableStateOf("") }

    // Section 4 - Clinical Examination
    var generalExam by remember { mutableStateOf("") }
    var respiratoryExam by remember { mutableStateOf("") }
    var cvsExam by remember { mutableStateOf("") }
    var abdomenExam by remember { mutableStateOf("") }
    var cnsExam by remember { mutableStateOf("") }
    var otherExam by remember { mutableStateOf("") }

    // Section 5 - Investigations
    var investigationsOrdered by remember { mutableStateOf("") }
    var samplesSentStatus by remember { mutableStateOf("Samples collected and sent to central lab") }

    // Section 6 - Investigation Trends
    var trendObservations by remember { mutableStateOf("") }

    // Section 7 - Clinical Assessment
    var currentDiagnosis by remember { mutableStateOf(patient.primaryDiagnosis) }
    var differentialDiagnosis by remember { mutableStateOf("") }
    var clinicalAssessment by remember { mutableStateOf("") }
    var problemsIdentified by remember { mutableStateOf("") }

    // Section 8 - Treatment and Management Plan
    var treatmentChanges by remember { mutableStateOf("") }
    var medicationPlan by remember { mutableStateOf("") }
    var monitoringPlan by remember { mutableStateOf("") }
    var proceduresPlan by remember { mutableStateOf("") }
    var dietFluidAdvice by remember { mutableStateOf("") }

    // Section 9 - Pending Tasks
    var pendingTasksText by remember { mutableStateOf("") }

    // Section 10 - Follow-up and Discharge Planning
    var expectedDischargeDate by remember { mutableStateOf("") }
    var dischargeReadiness by remember { mutableStateOf("Pending resolution of active problems") }
    var dischargeAdvice by remember { mutableStateOf("") }

    var dailySummary by remember { mutableStateOf("") }
    var copiedFromPrevious by remember { mutableStateOf(false) }

    // Default expand/collapse state: Logical clinical workflow per specification
    // Core clinical sections (Today's Clinical Status, Vitals, Assessment, Treatment) expanded by default; Patient Info collapsed once identified.
    var expandedSections by remember {
        mutableStateOf(setOf(2, 3, 7, 8))
    }

    fun toggleSection(sectionNumber: Int) {
        expandedSections = if (expandedSections.contains(sectionNumber)) {
            expandedSections - sectionNumber
        } else {
            expandedSections + sectionNumber
        }
    }

    val previousRound = rounds.firstOrNull()

    fun copyForwardFromPreviousRound() {
        if (previousRound == null) return
        copiedFromPrevious = true
        generalExam = previousRound.generalExam
        respiratoryExam = previousRound.respiratoryExam
        cvsExam = previousRound.cvsExam
        abdomenExam = previousRound.abdomenExam
        cnsExam = previousRound.cnsExam
        medicationPlan = previousRound.medicationPlan
        monitoringPlan = previousRound.monitoringPlan
        currentDiagnosis = previousRound.currentDiagnosis
        clinicalAssessment = previousRound.clinicalAssessment
        currentCondition = previousRound.currentCondition
    }

    // OCR Scan states
    var showOcrCaptureDialog by remember { mutableStateOf(false) }
    var ocrReviewData by remember { mutableStateOf<Triple<Uri, Int, OcrExtractionResult>?>(null) }

    if (ocrReviewData != null) {
        val (uri, rot, result) = ocrReviewData!!
        OcrReviewDraftScreen(
            imageUri = uri,
            rotationDegrees = rot,
            extractionResult = result,
            onApplyFields = { applied ->
                applied["bp"]?.let {
                    val parts = it.split("/")
                    if (parts.size == 2) {
                        bpSystolic = parts[0].trim()
                        bpDiastolic = parts[1].trim()
                    }
                }
                applied["pulse"]?.let { pulseRate = it }
                applied["spo2"]?.let { spO2 = it }
                applied["temperature"]?.let { temperatureF = it }
                applied["respiratory_rate"]?.let { respiratoryRate = it }
                applied["symptoms"]?.let { currentComplaints = it }
                applied["assessment"]?.let { clinicalAssessment = it }
                applied["plan"]?.let { treatmentChanges = it }

                val labs = applied.filterKeys { it.startsWith("lab_") }
                if (labs.isNotEmpty()) {
                    val labStr = labs.entries.joinToString("; ") { "${it.key.removePrefix("lab_").replace('_', ' ').uppercase()}: ${it.value}" }
                    trendObservations = if (trendObservations.isBlank()) labStr else "$trendObservations\n$labStr"
                }

                ocrReviewData = null
            },
            onDiscard = { ocrReviewData = null }
        )
        return
    }

    if (showOcrCaptureDialog) {
        val existingMap = mutableMapOf<String, String>()
        if (bpSystolic.isNotBlank() && bpDiastolic.isNotBlank()) {
            existingMap["bp"] = "$bpSystolic/$bpDiastolic"
        }
        if (pulseRate.isNotBlank()) existingMap["pulse"] = pulseRate
        if (spO2.isNotBlank()) existingMap["spo2"] = spO2
        if (temperatureF.isNotBlank()) existingMap["temperature"] = temperatureF
        if (respiratoryRate.isNotBlank()) existingMap["respiratory_rate"] = respiratoryRate
        if (currentComplaints.isNotBlank()) existingMap["symptoms"] = currentComplaints
        if (clinicalAssessment.isNotBlank()) existingMap["assessment"] = clinicalAssessment
        if (treatmentChanges.isNotBlank()) existingMap["plan"] = treatmentChanges

        OcrImageCaptureDialog(
            targetDocType = OcrDocumentType.DAILY_ROUND_NOTE,
            existingFormValues = existingMap,
            onDismiss = { showOcrCaptureDialog = false },
            onOcrResultReady = { uri, rot, res ->
                showOcrCaptureDialog = false
                ocrReviewData = Triple(uri, rot, res)
            }
        )
    }

    Scaffold(
        topBar = {
            WardRoundTopBar(
                title = "Daily Round — Day $hospitalDay",
                subtitle = "${patient.name} (${patient.uhid})",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Copy forward banner
            if (previousRound != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Previous Round on File",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Day ${previousRound.hospitalDay} (${previousRound.roundDate})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = { copyForwardFromPreviousRound() },
                            modifier = Modifier.testTag("copy_previous_round_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Copy Forward",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (copiedFromPrevious) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Baseline examination copied from previous round. Please verify all findings before finalization.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // OCR Scan Action Bar for Rounds
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Autofill Round from Document",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Scan bedside chart, vitals sheet, or paper note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OcrScanActionChip(
                    onClick = { showOcrCaptureDialog = true },
                    label = "Scan Round",
                    testTag = "btn_ocr_scan_round"
                )
            }

            // ==========================================
            // EXPAND ALL / COLLAPSE ALL GLOBAL CONTROLS
            // ==========================================
            RoundExpandCollapseControls(
                expandedCount = expandedSections.size,
                totalCount = 10,
                onExpandAll = { expandedSections = (1..10).toSet() },
                onCollapseAll = { expandedSections = emptySet() }
            )

            // ==========================================
            // 1. PATIENT INFORMATION
            // ==========================================
            val patientInfoSummary = "${patient.name} · UHID: ${patient.uhid} · ${patient.age}y/${patient.sex ?: "M"} · ${patient.ward} Bed ${patient.bedNumber} · ${patient.primaryDiagnosis}"
            CollapsibleRoundSection(
                sectionNumber = 1,
                title = "Patient Information",
                icon = Icons.Default.Person,
                isExpanded = expandedSections.contains(1),
                onToggle = { toggleSection(1) },
                collapsedSummary = patientInfoSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Full Name", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(patient.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("UHID", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(patient.uhid, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Age / Sex", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${patient.age} years • ${patient.sex ?: "M"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ward & Bed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${patient.ward} • Bed ${patient.bedNumber}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Admission Date", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(patient.admissionDate, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Primary Diagnosis", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = patient.primaryDiagnosis,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Session metadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = roundDate,
                            onValueChange = { roundDate = it },
                            label = { Text("Round Date") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = roundTime,
                            onValueChange = { roundTime = it },
                            label = { Text("Time") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(RoundType.MORNING, RoundType.EVENING, RoundType.EMERGENCY).forEach { type ->
                            FilterChip(
                                selected = roundType == type,
                                onClick = { roundType = type },
                                label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 2. TODAY'S CLINICAL STATUS
            // ==========================================
            val clinicalStatusSummary = listOfNotNull(
                "Condition: $currentCondition".takeIf { currentCondition.isNotBlank() },
                "Complaints: $currentComplaints".takeIf { currentComplaints.isNotBlank() },
                "Progression: $symptomProgression".takeIf { symptomProgression.isNotBlank() }
            ).joinToString(" · ").ifBlank { "No status recorded yet" }

            CollapsibleRoundSection(
                sectionNumber = 2,
                title = "Today's Clinical Status",
                icon = Icons.Default.EventNote,
                isExpanded = expandedSections.contains(2),
                onToggle = { toggleSection(2) },
                collapsedSummary = clinicalStatusSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentCondition,
                        onValueChange = { currentCondition = it },
                        label = { Text("General Condition / Status") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = currentComplaints,
                        onValueChange = { currentComplaints = it },
                        label = { Text("Symptoms & Active Complaints") },
                        placeholder = { Text("e.g. Cough reduced, no chest pain, afebrile") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = symptomProgression,
                        onValueChange = { symptomProgression = it },
                        label = { Text("Symptom Progression / Changes") },
                        placeholder = { Text("e.g. Sputum less purulent, breathlessness resolving") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = feverEvents,
                        onValueChange = { feverEvents = it },
                        label = { Text("Fever Events / Temperature Spikes") },
                        placeholder = { Text("e.g. No fever spikes overnight") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // ==========================================
            // 3. VITAL SIGNS
            // ==========================================
            val vitalsSummary = listOfNotNull(
                if (bpSystolic.isNotBlank() && bpDiastolic.isNotBlank()) "BP $bpSystolic/$bpDiastolic mmHg" else null,
                "PR $pulseRate/min".takeIf { pulseRate.isNotBlank() },
                "RR $respiratoryRate/min".takeIf { respiratoryRate.isNotBlank() },
                "Temp $temperatureF°F".takeIf { temperatureF.isNotBlank() },
                "SpO₂ $spO2% ($o2Delivery)".takeIf { spO2.isNotBlank() },
                "Glucose $bloodGlucose mg/dL".takeIf { bloodGlucose.isNotBlank() }
            ).joinToString(" · ").ifBlank { "Vitals pending entry" }

            CollapsibleRoundSection(
                sectionNumber = 3,
                title = "Vital Signs",
                icon = Icons.Default.Favorite,
                isExpanded = expandedSections.contains(3),
                onToggle = { toggleSection(3) },
                collapsedSummary = vitalsSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = bpSystolic,
                            onValueChange = { bpSystolic = it },
                            label = { Text("BP Sys") },
                            placeholder = { Text("120") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = bpDiastolic,
                            onValueChange = { bpDiastolic = it },
                            label = { Text("BP Dia") },
                            placeholder = { Text("80") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = pulseRate,
                            onValueChange = { pulseRate = it },
                            label = { Text("Pulse") },
                            placeholder = { Text("76") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = respiratoryRate,
                            onValueChange = { respiratoryRate = it },
                            label = { Text("RR") },
                            placeholder = { Text("18") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = temperatureF,
                            onValueChange = { temperatureF = it },
                            label = { Text("Temp °F") },
                            placeholder = { Text("98.4") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = spO2,
                            onValueChange = { spO2 = it },
                            label = { Text("SpO₂ %") },
                            placeholder = { Text("98") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = o2Delivery,
                            onValueChange = { o2Delivery = it },
                            label = { Text("O₂ Delivery") },
                            placeholder = { Text("Room Air / Nasal Cannula") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = bloodGlucose,
                            onValueChange = { bloodGlucose = it },
                            label = { Text("Glucose (mg/dL)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // ==========================================
            // 4. CLINICAL EXAMINATION
            // ==========================================
            val examSummary = listOfNotNull(
                "Resp: $respiratoryExam".takeIf { respiratoryExam.isNotBlank() },
                "CVS: $cvsExam".takeIf { cvsExam.isNotBlank() },
                "Abd: $abdomenExam".takeIf { abdomenExam.isNotBlank() },
                "CNS: $cnsExam".takeIf { cnsExam.isNotBlank() }
            ).joinToString(" · ").ifBlank { "Examination pending" }

            CollapsibleRoundSection(
                sectionNumber = 4,
                title = "Clinical Examination",
                icon = Icons.Default.MedicalServices,
                isExpanded = expandedSections.contains(4),
                onToggle = { toggleSection(4) },
                collapsedSummary = examSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = generalExam,
                        onValueChange = { generalExam = it },
                        label = { Text("General Examination") },
                        placeholder = { Text("Pallor, icterus, cyanosis, clubbing, lymphadenopathy, edema") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = respiratoryExam,
                        onValueChange = { respiratoryExam = it },
                        label = { Text("Respiratory System") },
                        placeholder = { Text("Bilateral air entry, crepitations, rhonchi") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = cvsExam,
                        onValueChange = { cvsExam = it },
                        label = { Text("Cardiovascular System") },
                        placeholder = { Text("S1 S2 heard, murmurs") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = abdomenExam,
                        onValueChange = { abdomenExam = it },
                        label = { Text("Abdominal Examination") },
                        placeholder = { Text("Soft, non-tender, bowel sounds present") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = cnsExam,
                        onValueChange = { cnsExam = it },
                        label = { Text("Central Nervous System") },
                        placeholder = { Text("GCS 15/15, pupils equal and reactive") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // ==========================================
            // 5. INVESTIGATIONS
            // ==========================================
            val investigationsSummary = "${orders.size} order(s) · ${results.size} result(s) available".let {
                if (investigationsOrdered.isNotBlank()) "$it · Ordered: $investigationsOrdered" else it
            }

            CollapsibleRoundSection(
                sectionNumber = 5,
                title = "Investigations",
                icon = Icons.Default.Biotech,
                isExpanded = expandedSections.contains(5),
                onToggle = { toggleSection(5) },
                collapsedSummary = investigationsSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = investigationsOrdered,
                        onValueChange = { investigationsOrdered = it },
                        label = { Text("Investigations Ordered Today") },
                        placeholder = { Text("e.g. Repeat CBC, Serum Electrolytes, Follow-up CXR") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = samplesSentStatus,
                        onValueChange = { samplesSentStatus = it },
                        label = { Text("Samples Collected & Sent Status") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Recent results preview
                    if (results.isNotEmpty()) {
                        Text(
                            text = "Recent Investigation Results on File:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        results.take(4).forEach { res ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${res.investigationName}: ${res.parameterName}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${res.originalTextValue} ${res.unit}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 6. INVESTIGATION TRENDS
            // ==========================================
            val trendsSummary = "Trends active · CBC, CRP, and inflammatory trajectory monitored"
            CollapsibleRoundSection(
                sectionNumber = 6,
                title = "Investigation Trends",
                icon = Icons.Default.Timeline,
                isExpanded = expandedSections.contains(6),
                onToggle = { toggleSection(6) },
                collapsedSummary = trendsSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Laboratory Parameters & Clinical Trajectory",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Serial observations are plotted automatically in the Trends dashboard. Note notable trajectory shifts below.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = trendObservations,
                        onValueChange = { trendObservations = it },
                        label = { Text("Trend Interpretations & Changes") },
                        placeholder = { Text("e.g. CRP down trending (85 -> 42.5 mg/L), Leucocytosis resolving") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // ==========================================
            // 7. CLINICAL ASSESSMENT
            // ==========================================
            val assessmentSummary = listOfNotNull(
                "Dx: $currentDiagnosis".takeIf { currentDiagnosis.isNotBlank() },
                clinicalAssessment.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { "Assessment pending documentation" }

            CollapsibleRoundSection(
                sectionNumber = 7,
                title = "Clinical Assessment",
                icon = Icons.Default.LocalHospital,
                isExpanded = expandedSections.contains(7),
                onToggle = { toggleSection(7) },
                collapsedSummary = assessmentSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentDiagnosis,
                        onValueChange = { currentDiagnosis = it },
                        label = { Text("Working Diagnosis") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 112.dp),
                        singleLine = false,
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = clinicalAssessment,
                        onValueChange = { clinicalAssessment = it },
                        label = { Text("Clinical Assessment & Interpretation") },
                        placeholder = { Text("e.g. Community Acquired Pneumonia showing good clinical and biochemical response") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = problemsIdentified,
                        onValueChange = { problemsIdentified = it },
                        label = { Text("Active Clinical Problems") },
                        placeholder = { Text("1. Resolving lower lobe pneumonia\n2. Mild dehydration") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // ==========================================
            // 8. TREATMENT AND MANAGEMENT PLAN
            // ==========================================
            val planSummary = listOfNotNull(
                treatmentChanges.takeIf { it.isNotBlank() },
                medicationPlan.takeIf { it.isNotBlank() }
            ).joinToString(" · ").ifBlank { "Treatment plan active" }

            CollapsibleRoundSection(
                sectionNumber = 8,
                title = "Treatment and Management Plan",
                icon = Icons.Default.CheckCircle,
                isExpanded = expandedSections.contains(8),
                onToggle = { toggleSection(8) },
                collapsedSummary = planSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = treatmentChanges,
                        onValueChange = { treatmentChanges = it },
                        label = { Text("Treatment Adjustments & Interventions") },
                        placeholder = { Text("e.g. Step down to oral antibiotics tomorrow if afebrile") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = medicationPlan,
                        onValueChange = { medicationPlan = it },
                        label = { Text("Medication Plan & Prescriptions") },
                        placeholder = { Text("e.g. IV Ceftriaxone 1g BD, Tab Paracetamol 650mg SOS") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = monitoringPlan,
                        onValueChange = { monitoringPlan = it },
                        label = { Text("Monitoring Instructions") },
                        placeholder = { Text("e.g. 4-hourly vitals, maintain SpO2 > 94%") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = dietFluidAdvice,
                        onValueChange = { dietFluidAdvice = it },
                        label = { Text("Diet & Fluid Advice") },
                        placeholder = { Text("e.g. High protein diet, oral fluids 2-2.5 L/day") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // ==========================================
            // 9. PENDING TASKS
            // ==========================================
            val pendingSummary = pendingTasksText.ifBlank { "Review pending lab reports · Confirm morning vitals" }

            CollapsibleRoundSection(
                sectionNumber = 9,
                title = "Pending Tasks",
                icon = Icons.Default.PendingActions,
                isExpanded = expandedSections.contains(9),
                onToggle = { toggleSection(9) },
                collapsedSummary = pendingSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pendingTasksText,
                        onValueChange = { pendingTasksText = it },
                        label = { Text("Outstanding Ward Tasks & Consultations") },
                        placeholder = { Text("e.g. Follow up with Pulmonology on spirometry\nCheck evening CRP report") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // ==========================================
            // 10. FOLLOW-UP AND DISCHARGE PLANNING
            // ==========================================
            val dischargeSummaryText = listOfNotNull(
                "Readiness: $dischargeReadiness".takeIf { dischargeReadiness.isNotBlank() },
                "Target: $expectedDischargeDate".takeIf { expectedDischargeDate.isNotBlank() }
            ).joinToString(" · ").ifBlank { "Discharge planning in progress" }

            CollapsibleRoundSection(
                sectionNumber = 10,
                title = "Follow-up and Discharge Planning",
                icon = Icons.Default.PlaylistAddCheck,
                isExpanded = expandedSections.contains(10),
                onToggle = { toggleSection(10) },
                collapsedSummary = dischargeSummaryText
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dischargeReadiness,
                        onValueChange = { dischargeReadiness = it },
                        label = { Text("Discharge Readiness Status") },
                        placeholder = { Text("e.g. Likely ready in 48h if clinical stability maintained") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = expectedDischargeDate,
                        onValueChange = { expectedDischargeDate = it },
                        label = { Text("Target Discharge Date") },
                        placeholder = { Text("e.g. 2026-10-30") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = dischargeAdvice,
                        onValueChange = { dischargeAdvice = it },
                        label = { Text("Discharge Advice & Follow-up Plan") },
                        placeholder = { Text("e.g. Chest OPD review in 7 days, complete oral antibiotics course") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Daily Summary note
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Daily Round Summary Note",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = dailySummary,
                        onValueChange = { dailySummary = it },
                        label = { Text("Consolidated Doctor's Round Summary") },
                        placeholder = { Text("Brief synthesis of round findings and plan for nurse handover...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Action Buttons: Finalize & Save Draft
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val round = RoundEntryEntity(
                            id = UUID.randomUUID().toString(),
                            patientId = patient.id,
                            admissionId = patient.admissionNumber,
                            roundDate = roundDate,
                            roundTime = roundTime,
                            doctorName = currentUser.name,
                            ward = patient.ward,
                            bedNumber = patient.bedNumber,
                            hospitalDay = hospitalDay,
                            roundType = roundType,
                            overnightEvents = currentComplaints,
                            currentComplaints = currentComplaints,
                            symptomProgression = symptomProgression,
                            feverEvents = feverEvents,
                            currentCondition = currentCondition,
                            responseToTreatment = treatmentChanges,
                            generalExam = generalExam,
                            respiratoryExam = respiratoryExam,
                            cvsExam = cvsExam,
                            abdomenExam = abdomenExam,
                            cnsExam = cnsExam,
                            clinicalAssessment = clinicalAssessment,
                            treatmentChanges = treatmentChanges,
                            medicationPlan = medicationPlan,
                            investigationsOrdered = investigationsOrdered,
                            dailySummary = dailySummary,
                            isDraft = true,
                            copiedFromPreviousRound = copiedFromPrevious
                        )
                        viewModel.saveRound(round) { onNavigateBack() }
                    },
                    modifier = Modifier
                        .testTag("save_draft_round_button")
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Save Draft",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        val round = RoundEntryEntity(
                            id = UUID.randomUUID().toString(),
                            patientId = patient.id,
                            admissionId = patient.admissionNumber,
                            roundDate = roundDate,
                            roundTime = roundTime,
                            doctorName = currentUser.name,
                            ward = patient.ward,
                            bedNumber = patient.bedNumber,
                            hospitalDay = hospitalDay,
                            roundType = roundType,
                            overnightEvents = currentComplaints,
                            currentComplaints = currentComplaints,
                            symptomProgression = symptomProgression,
                            feverEvents = feverEvents,
                            currentCondition = currentCondition,
                            responseToTreatment = treatmentChanges,
                            generalExam = generalExam,
                            respiratoryExam = respiratoryExam,
                            cvsExam = cvsExam,
                            abdomenExam = abdomenExam,
                            cnsExam = cnsExam,
                            clinicalAssessment = clinicalAssessment,
                            treatmentChanges = treatmentChanges,
                            medicationPlan = medicationPlan,
                            investigationsOrdered = investigationsOrdered,
                            dailySummary = dailySummary,
                            isDraft = false,
                            copiedFromPreviousRound = copiedFromPrevious
                        )

                        val vitalObservation = if (bpSystolic.isNotBlank() || pulseRate.isNotBlank() || temperatureF.isNotBlank() || spO2.isNotBlank()) {
                            VitalObservationEntity(
                                patientId = patient.id,
                                admissionId = patient.admissionNumber,
                                roundId = round.id,
                                measurementDate = roundDate,
                                measurementTime = roundTime,
                                bpSystolic = bpSystolic.toIntOrNull(),
                                bpDiastolic = bpDiastolic.toIntOrNull(),
                                pulseRate = pulseRate.toIntOrNull(),
                                respiratoryRate = respiratoryRate.toIntOrNull(),
                                temperatureF = temperatureF.toDoubleOrNull(),
                                spO2 = spO2.toIntOrNull(),
                                oxygenDeliveryMethod = o2Delivery,
                                bloodGlucoseMgDl = bloodGlucose.toIntOrNull()
                            )
                        } else null

                        viewModel.saveRound(round, vitalObservation) { onNavigateBack() }
                    },
                    modifier = Modifier
                        .testTag("finalize_round_button")
                        .weight(1.3f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Finalize Round", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
