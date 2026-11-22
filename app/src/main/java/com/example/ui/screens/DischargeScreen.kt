package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import com.example.domain.ocr.OcrDocumentType
import com.example.domain.ocr.OcrExtractionResult
import com.example.domain.pdf.ClinicalPdfExporter
import com.example.domain.pdf.PdfExportOutput
import com.example.ui.components.OcrScanActionChip
import com.example.ui.components.PdfPreviewExportDialog
import com.example.ui.ocr.OcrImageCaptureDialog
import com.example.ui.ocr.OcrReviewDraftScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DischargeStatus
import com.example.data.model.DischargeSummaryEntity
import com.example.data.model.FollowUpTaskEntity
import com.example.data.model.InvestigationOrderEntity
import com.example.data.model.InvestigationStatus
import com.example.data.model.PatientEntity
import com.example.data.model.SyncStatus
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.ui.components.WardRoundTopBar
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DeepRoyalNavy
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.MutedSurface
import com.example.ui.theme.PeriwinkleBlue
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SoftCloudWhite
import com.example.ui.theme.StatusAlert
import com.example.ui.theme.StatusPending
import com.example.ui.viewmodel.WardRoundViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DischargePlanningScreen(
    patientId: String,
    viewModel: WardRoundViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val patientState by viewModel.getPatient(patientId).collectAsStateWithLifecycle(initialValue = null)
    val patient = patientState ?: return
    val existingSummary by viewModel.getDischargeSummaryForPatient(patientId).collectAsStateWithLifecycle(initialValue = null)
    val patientOrders by viewModel.getOrdersForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())
    val patientTasks by viewModel.getFollowUpTasksForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    // Unresolved tasks & pending tests warning check (PDF page 24)
    val pendingOrders = remember(patientOrders) {
        patientOrders.filter { it.currentStatus !in listOf(InvestigationStatus.RESULT_REVIEWED, InvestigationStatus.COMPLETED, InvestigationStatus.CANCELLED) }
    }
    val openTasks = remember(patientTasks) {
        patientTasks.filter { it.status != TaskStatus.COMPLETED && it.status != TaskStatus.CANCELLED }
    }

    var dischargeStatus by remember { mutableStateOf(DischargeStatus.UNDER_CONSIDERATION) }
    var admissionDiagnosis by remember { mutableStateOf(patient.primaryDiagnosis) }
    var finalDiagnosis by remember { mutableStateOf("") }
    var clinicalCourse by remember { mutableStateOf("") }
    var examFindings by remember { mutableStateOf("") }
    var conditionAtDischarge by remember { mutableStateOf("Stable, Afebrile, Ambulatory") }
    var medications by remember { mutableStateOf("") }
    var dietAdvice by remember { mutableStateOf("Normal diet, adequate hydration") }
    var activityAdvice by remember { mutableStateOf("Gradual resumption of normal activities") }
    var warningSigns by remember { mutableStateOf("High fever recurrence, breathlessness, chest pain") }
    var urgentCareInstructions by remember { mutableStateOf("Report immediately to Emergency Department") }
    var followUpDept by remember { mutableStateOf("Internal Medicine OPD") }
    var followUpDate by remember { mutableStateOf("") }
    var followUpInvestigations by remember { mutableStateOf("Repeat CBC, CRP in 7 days") }
    var isFinalized by remember { mutableStateOf(false) }

    // Prepopulate if existing summary exists
    LaunchedEffect(existingSummary) {
        existingSummary?.let { s ->
            dischargeStatus = s.dischargeStatus
            admissionDiagnosis = s.admissionDiagnosis
            finalDiagnosis = s.finalDiagnosis
            clinicalCourse = s.relevantClinicalCourse
            examFindings = s.importantExamFindings
            conditionAtDischarge = s.conditionAtDischarge
            medications = s.dischargeMedications
            dietAdvice = s.dietAdvice
            activityAdvice = s.activityAdvice
            warningSigns = s.warningSigns
            urgentCareInstructions = s.urgentCareInstructions
            followUpDept = s.followUpDepartment
            followUpDate = s.followUpDate
            followUpInvestigations = s.followUpInvestigations
            isFinalized = s.isFinalized
        }
    }

    // OCR Scan states
    var showOcrCaptureDialog by remember { mutableStateOf(false) }
    var ocrReviewData by remember { mutableStateOf<Triple<Uri, Int, OcrExtractionResult>?>(null) }

    // PDF Export states
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfOutput by remember { mutableStateOf<PdfExportOutput?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }

    fun buildCurrentSnapshot(finalized: Boolean = isFinalized): DischargeSummaryEntity {
        return DischargeSummaryEntity(
            id = existingSummary?.id ?: UUID.randomUUID().toString(),
            patientId = patient.id,
            admissionId = patient.admissionNumber,
            admissionDiagnosis = admissionDiagnosis,
            finalDiagnosis = finalDiagnosis,
            relevantClinicalCourse = clinicalCourse,
            importantExamFindings = examFindings,
            importantInvestigationResults = existingSummary?.importantInvestigationResults.orEmpty(),
            proceduresPerformed = existingSummary?.proceduresPerformed.orEmpty(),
            conditionAtDischarge = conditionAtDischarge,
            treatmentReceived = existingSummary?.treatmentReceived.orEmpty(),
            dischargeMedications = medications,
            dietAdvice = dietAdvice,
            activityAdvice = activityAdvice,
            warningSigns = warningSigns,
            urgentCareInstructions = urgentCareInstructions,
            followUpDepartment = followUpDept,
            followUpDate = followUpDate,
            followUpInvestigations = followUpInvestigations,
            pendingInvestigationReports = existingSummary?.pendingInvestigationReports.orEmpty(),
            responsibleClinician = currentUser.name,
            dischargeStatus = dischargeStatus,
            isFinalized = finalized,
            finalizedAt = existingSummary?.finalizedAt,
            syncStatus = SyncStatus.SAVED_LOCALLY
        )
    }

    fun launchDischargePdfExport() {
        if (isExportingPdf) return
        isExportingPdf = true
        scope.launch {
            try {
                val snapshot = buildCurrentSnapshot()
                pdfOutput = ClinicalPdfExporter.generateDischargeSummaryPdf(
                    context = context,
                    patient = patient,
                    summary = snapshot,
                    clinicianName = currentUser.name
                )
            } catch (e: Exception) {
                Toast.makeText(context, "PDF generation failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                isExportingPdf = false
            }
        }
    }

    if (pdfOutput != null) {
        PdfPreviewExportDialog(
            pdfOutput = pdfOutput!!,
            onDismiss = { pdfOutput = null }
        )
    }

    if (ocrReviewData != null) {
        val (uri, rot, result) = ocrReviewData!!
        OcrReviewDraftScreen(
            imageUri = uri,
            rotationDegrees = rot,
            extractionResult = result,
            onApplyFields = { applied ->
                applied["diagnosis"]?.let { finalDiagnosis = it }
                applied["plan"]?.let { medications = it }
                applied["assessment"]?.let { clinicalCourse = it }
                applied["symptoms"]?.let { conditionAtDischarge = it }
                ocrReviewData = null
            },
            onDiscard = { ocrReviewData = null }
        )
        return
    }

    if (showOcrCaptureDialog) {
        OcrImageCaptureDialog(
            targetDocType = OcrDocumentType.DISCHARGE_SUMMARY,
            existingFormValues = mapOf(
                "diagnosis" to finalDiagnosis,
                "plan" to medications,
                "assessment" to clinicalCourse
            ),
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
                title = "Discharge Planning",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // OCR Scan & Export PDF Action Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OcrScanActionChip(
                    onClick = { showOcrCaptureDialog = true },
                    label = "Scan Referral",
                    testTag = "btn_ocr_scan_discharge",
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    onClick = { launchDischargePdfExport() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_export_discharge_pdf_top")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Export Discharge PDF",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isExportingPdf) "Rendering..." else "Export PDF",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Patient Identification
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = patient.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = PrimaryText
                        )
                        Text(
                            text = "UHID: ${patient.uhid} • Admitted: ${patient.admissionDate}",
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                    }

                    if (isFinalized) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricLime.copy(alpha = 0.35f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Discharge Finalized", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeepRoyalNavy)
                        }
                    }
                }
            }

            // Warning Banner for unresolved tasks & pending labs (Mandated by PDF page 24)
            if (pendingOrders.isNotEmpty() || openTasks.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StatusPending.copy(alpha = 0.2f))
                        .border(1.dp, StatusPending, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFF9A6700), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pending Items Before Discharge",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF9A6700)
                            )
                        }
                        if (pendingOrders.isNotEmpty()) {
                            Text(
                                text = "• ${pendingOrders.size} lab/imaging order(s) still awaiting results or review.",
                                fontSize = 11.sp,
                                color = PrimaryText
                            )
                        }
                        if (openTasks.isNotEmpty()) {
                            Text(
                                text = "• ${openTasks.size} follow-up / inpatient task(s) currently open.",
                                fontSize = 11.sp,
                                color = PrimaryText
                            )
                        }
                    }
                }
            }

            // Discharge Status Progression Tracker (PDF page 23)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Discharge Status",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepRoyalNavy
                    )

                    val statuses = listOf(
                        DischargeStatus.NOT_PLANNED,
                        DischargeStatus.UNDER_CONSIDERATION,
                        DischargeStatus.PLANNED,
                        DischargeStatus.AWAITING_INVESTIGATION,
                        DischargeStatus.READY_FOR_CLINICIAN_REVIEW,
                        DischargeStatus.DISCHARGED
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        statuses.chunked(2).forEach { rowList ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowList.forEach { st ->
                                    val isSelected = dischargeStatus == st
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { dischargeStatus = st },
                                        label = { Text(st.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = DeepRoyalNavy,
                                            selectedLabelColor = PureWhite
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Discharge Summary Form Fields (PDF page 23-24)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Clinical Summary Fields",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepRoyalNavy
                    )

                    OutlinedTextField(
                        value = admissionDiagnosis,
                        onValueChange = { admissionDiagnosis = it },
                        label = { Text("Admission Diagnosis") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 112.dp),
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = finalDiagnosis,
                        onValueChange = { finalDiagnosis = it },
                        label = { Text("Final Diagnosis *") },
                        placeholder = { Text("e.g. Lobar Pneumonia (Streptococcus pneumoniae) - Resolved") },
                        modifier = Modifier
                            .testTag("discharge_final_diagnosis_input")
                            .fillMaxWidth()
                            .heightIn(max = 112.dp),
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = clinicalCourse,
                        onValueChange = { clinicalCourse = it },
                        label = { Text("Relevant Clinical Course & Management") },
                        placeholder = { Text("Summarize inpatient trajectory, IV treatment response, and weaning") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = conditionAtDischarge,
                        onValueChange = { conditionAtDischarge = it },
                        label = { Text("Condition at Discharge") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = medications,
                        onValueChange = { medications = it },
                        label = { Text("Discharge Medications (Dose, Route, Freq, Duration)") },
                        placeholder = { Text("1. Tab Cefpodoxime 200mg BD x 5 days\n2. Tab Paracetamol 650mg SOS") },
                        modifier = Modifier.testTag("discharge_medications_input").fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Advice & Follow-up Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Advice & Instructions",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepRoyalNavy
                    )

                    OutlinedTextField(
                        value = dietAdvice,
                        onValueChange = { dietAdvice = it },
                        label = { Text("Diet Advice") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = warningSigns,
                        onValueChange = { warningSigns = it },
                        label = { Text("Red Flag / Warning Signs") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = urgentCareInstructions,
                        onValueChange = { urgentCareInstructions = it },
                        label = { Text("Urgent Care Instructions") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = followUpDept,
                            onValueChange = { followUpDept = it },
                            label = { Text("Follow-up Dept") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = followUpDate,
                            onValueChange = { followUpDate = it },
                            label = { Text("Follow-up Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = followUpInvestigations,
                        onValueChange = { followUpInvestigations = it },
                        label = { Text("Scheduled Follow-up Investigations") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Save & Finalize Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val summary = DischargeSummaryEntity(
                            id = existingSummary?.id ?: UUID.randomUUID().toString(),
                            patientId = patient.id,
                            admissionId = patient.admissionNumber,
                            admissionDiagnosis = admissionDiagnosis,
                            finalDiagnosis = finalDiagnosis,
                            relevantClinicalCourse = clinicalCourse,
                            importantExamFindings = examFindings,
                            conditionAtDischarge = conditionAtDischarge,
                            dischargeMedications = medications,
                            dietAdvice = dietAdvice,
                            activityAdvice = activityAdvice,
                            warningSigns = warningSigns,
                            urgentCareInstructions = urgentCareInstructions,
                            followUpDepartment = followUpDept,
                            followUpDate = followUpDate,
                            followUpInvestigations = followUpInvestigations,
                            responsibleClinician = currentUser.name,
                            dischargeStatus = dischargeStatus,
                            isFinalized = false,
                            syncStatus = SyncStatus.SAVED_LOCALLY
                        )
                        viewModel.saveDischargeSummary(summary)
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .testTag("save_draft_summary_btn")
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Text("Save Draft", color = SecondaryText, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                        val summary = DischargeSummaryEntity(
                            id = existingSummary?.id ?: UUID.randomUUID().toString(),
                            patientId = patient.id,
                            admissionId = patient.admissionNumber,
                            admissionDiagnosis = admissionDiagnosis,
                            finalDiagnosis = finalDiagnosis,
                            relevantClinicalCourse = clinicalCourse,
                            importantExamFindings = examFindings,
                            conditionAtDischarge = conditionAtDischarge,
                            dischargeMedications = medications,
                            dietAdvice = dietAdvice,
                            activityAdvice = activityAdvice,
                            warningSigns = warningSigns,
                            urgentCareInstructions = urgentCareInstructions,
                            followUpDepartment = followUpDept,
                            followUpDate = followUpDate,
                            followUpInvestigations = followUpInvestigations,
                            responsibleClinician = currentUser.name,
                            dischargeStatus = DischargeStatus.DISCHARGED,
                            isFinalized = true,
                            finalizedAt = now,
                            syncStatus = SyncStatus.SAVED_LOCALLY
                        )
                        viewModel.saveDischargeSummary(summary)

                        // If follow-up date is provided, create follow-up task automatically (PDF page 24 & 25)
                        if (followUpDate.isNotBlank()) {
                            viewModel.saveFollowUpTask(
                                FollowUpTaskEntity(
                                    patientId = patient.id,
                                    uhid = patient.uhid,
                                    taskType = "Outpatient Follow-up",
                                    description = "Post-discharge clinical review with $followUpDept",
                                    dueDate = followUpDate,
                                    responsibleDepartmentOrDoctor = followUpDept,
                                    priority = TaskPriority.HIGH,
                                    status = TaskStatus.SCHEDULED
                                )
                            )
                        }

                        onNavigateBack()
                    },
                    modifier = Modifier
                        .testTag("finalize_discharge_btn")
                        .weight(1.4f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRoyalNavy, contentColor = PureWhite)
                ) {
                    Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign & Finalize", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            OutlinedButton(
                onClick = { launchDischargePdfExport() },
                modifier = Modifier
                    .testTag("export_discharge_pdf_bottom_btn")
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isFinalized) "Preview, Print & Share Discharge PDF" else "Preview Draft Discharge PDF",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

