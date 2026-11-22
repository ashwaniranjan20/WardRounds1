package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InvestigationStatus
import com.example.domain.pdf.ClinicalPdfExporter
import com.example.domain.pdf.PdfExportOutput
import com.example.ui.components.PdfPreviewExportDialog
import com.example.ui.components.WardRoundTopBar
import kotlinx.coroutines.launch
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReportType(val title: String) {
    DAILY_WARD_ROUND_LIST("Daily Ward-Round Register"),
    DAY_WISE_INVESTIGATIONS("Day-Wise Investigation Audit"),
    PENDING_INVESTIGATIONS("Pending Tests & Turnaround"),
    FOLLOW_UP_TASKS("Scheduled Follow-up Tasks"),
    PATIENT_CLINICAL_TIMELINE("Patient Clinical Summary")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: WardRoundViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val patients by viewModel.allPatients.collectAsStateWithLifecycle()
    val todayRounds by viewModel.todayRounds.collectAsStateWithLifecycle()
    val orders by viewModel.allOrders.collectAsStateWithLifecycle()
    val results by viewModel.allResults.collectAsStateWithLifecycle()
    val tasks by viewModel.allFollowUpTasks.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var selectedReportType by remember { mutableStateOf(ReportType.DAILY_WARD_ROUND_LIST) }
    var selectedPatientId by remember { mutableStateOf(patients.firstOrNull()?.id ?: "") }
    var exportedText by remember { mutableStateOf("") }
    var isCopied by remember { mutableStateOf(false) }
    var pdfOutput by remember { mutableStateOf<PdfExportOutput?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }

    val timestamp = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) }

    // Generate formatted clinical report text
    fun generateReport(): String {
        val sb = StringBuilder()
        sb.append("====================================================\n")
        sb.append("WARDROUNDS CLINICAL REPORT — CONFIDENTIAL MEDICAL RECORD\n")
        sb.append("====================================================\n")
        sb.append("Generated On: $timestamp\n")
        sb.append("Clinician: ${currentUser.name} (${currentUser.role})\n")
        sb.append("Hospital Dept: ${currentUser.department}\n")
        sb.append("----------------------------------------------------\n\n")

        when (selectedReportType) {
            ReportType.DAILY_WARD_ROUND_LIST -> {
                sb.append("REPORT: DAILY WARD ROUND LIST\n")
                sb.append("Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}\n\n")
                sb.append("Total Admitted Patients: ${patients.filter { !it.isDischarged }.size}\n")
                sb.append("Total Rounds Completed Today: ${todayRounds.size}\n\n")
                patients.filter { !it.isDischarged }.forEachIndexed { idx, pt ->
                    val round = todayRounds.find { it.patientId == pt.id }
                    sb.append("${idx + 1}. ${pt.name} (UHID: ${pt.uhid})\n")
                    sb.append("   Location: ${pt.ward} — Bed ${pt.bedNumber}\n")
                    sb.append("   Diagnosis: ${pt.primaryDiagnosis}\n")
                    sb.append("   Round Status: ${if (round != null) "COMPLETED (${round.roundTime} by ${round.doctorName})" else "PENDING ROUND"}\n")
                    if (round != null && round.clinicalAssessment.isNotBlank()) {
                        sb.append("   Assessment: ${round.clinicalAssessment}\n")
                    }
                    sb.append("\n")
                }
            }
            ReportType.DAY_WISE_INVESTIGATIONS -> {
                sb.append("REPORT: DAY-WISE INVESTIGATION AUDIT\n\n")
                val grouped = results.groupBy { it.measurementDate }
                grouped.forEach { (date, resList) ->
                    sb.append("--- Date: $date ---\n")
                    resList.forEach { res ->
                        val pt = patients.find { it.id == res.patientId }
                        sb.append("• [${res.category}] ${res.parameterName}: ${res.originalTextValue} ${res.unit} (Ref: ${res.refRangeText.ifBlank { "N/A" }})\n")
                        sb.append("  Patient: ${pt?.name ?: "Unknown"} (UHID: ${pt?.uhid ?: "--"})\n")
                        sb.append("  Review: ${if (res.isReviewed) "Verified by ${res.reviewingClinician}" else "AWAITING CLINICIAN REVIEW"}\n")
                    }
                    sb.append("\n")
                }
            }
            ReportType.PENDING_INVESTIGATIONS -> {
                sb.append("REPORT: PENDING INVESTIGATIONS & WORKFLOW TURNAROUND\n\n")
                val pending = orders.filter { it.currentStatus !in listOf(InvestigationStatus.RESULT_REVIEWED, InvestigationStatus.COMPLETED, InvestigationStatus.CANCELLED) }
                sb.append("Pending Order Count: ${pending.size}\n\n")
                pending.forEachIndexed { idx, ord ->
                    val pt = patients.find { it.id == ord.patientId }
                    sb.append("${idx + 1}. ${ord.investigationName} [Priority: ${ord.priority}]\n")
                    sb.append("   Patient: ${pt?.name} (UHID: ${pt?.uhid})\n")
                    sb.append("   Ward/Bed: ${pt?.ward} / Bed ${pt?.bedNumber}\n")
                    sb.append("   Current Workflow Status: ${ord.currentStatus.name}\n")
                    sb.append("   Specimen: ${ord.specimenInfo}\n\n")
                }
            }
            ReportType.FOLLOW_UP_TASKS -> {
                sb.append("REPORT: FOLLOW-UP AND PENDING CLINICAL TASKS\n\n")
                tasks.forEachIndexed { idx, t ->
                    val pt = patients.find { it.id == t.patientId }
                    sb.append("${idx + 1}. [${t.priority}] ${t.taskType}: ${t.description}\n")
                    sb.append("   Patient: ${pt?.name} (UHID: ${t.uhid})\n")
                    sb.append("   Due Date: ${t.dueDate} ${t.dueTime}\n")
                    sb.append("   Status: ${t.status}\n")
                    sb.append("   Responsible: ${t.responsibleDepartmentOrDoctor}\n\n")
                }
            }
            ReportType.PATIENT_CLINICAL_TIMELINE -> {
                val pt = patients.find { it.id == selectedPatientId } ?: patients.firstOrNull()
                if (pt != null) {
                    sb.append("REPORT: COMPREHENSIVE PATIENT CLINICAL TIMELINE\n")
                    sb.append("Patient: ${pt.name} (UHID: ${pt.uhid})\n")
                    sb.append("Ward: ${pt.ward} — Bed: ${pt.bedNumber}\n")
                    sb.append("Diagnosis: ${pt.primaryDiagnosis}\n")
                    sb.append("Admission Date: ${pt.admissionDate}\n")
                    sb.append("Allergies: ${pt.allergies}\n\n")
                    sb.append("Recent Investigation Results:\n")
                    val ptResults = results.filter { it.patientId == pt.id }
                    ptResults.forEach { r ->
                        sb.append("• ${r.measurementDate} ${r.measurementTime} | ${r.parameterName}: ${r.originalTextValue} ${r.unit}\n")
                    }
                }
            }
        }

        sb.append("\n====================================================\n")
        sb.append("END OF REPORT — SENSITIVE PATIENT HEALTH INFORMATION\n")
        sb.append("====================================================\n")
        return sb.toString()
    }

    if (pdfOutput != null) {
        PdfPreviewExportDialog(
            pdfOutput = pdfOutput!!,
            onDismiss = { pdfOutput = null }
        )
    }

    Scaffold(
        topBar = {
            WardRoundTopBar(
                title = "Reports & Audit Logs",
                subtitle = "Clinical Summaries & PDF Exports",
                showBackButton = onNavigateBack != null,
                onBackClick = { onNavigateBack?.invoke() }
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
            // Mandatory Privacy and Security Warning (PDF page 26 & 28)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DeepRoyalNavy.copy(alpha = 0.08f))
                    .border(1.dp, DeepRoyalNavy.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Confidentiality Warning",
                        tint = DeepRoyalNavy,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Confidential Medical Information Notice",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepRoyalNavy
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "This module contains protected patient health information (PHI). Reports and exports must strictly adhere to hospital data governance policies and authorized clinical workflows.",
                            fontSize = 11.sp,
                            color = SecondaryText,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Report Type Selection
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
                        text = "Select Report Template",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = DeepRoyalNavy
                    )

                    ReportType.values().forEach { rType ->
                        val isSelected = selectedReportType == rType
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) DeepRoyalNavy.copy(alpha = 0.08f) else MutedSurface)
                                .clickable {
                                    selectedReportType = rType
                                    exportedText = ""
                                    isCopied = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rType.title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) DeepRoyalNavy else PrimaryText
                            )

                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = DeepRoyalNavy, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            exportedText = generateReport()
                        },
                        modifier = Modifier
                            .testTag("generate_report_button")
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepRoyalNavy)
                    ) {
                        Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Report", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Generated Report Preview & Export
            AnimatedVisibility(visible = exportedText.isNotBlank()) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Report Output Preview",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = DeepRoyalNavy
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(exportedText))
                                        isCopied = true
                                        Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("copy_report_btn").height(36.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isCopied) "Copied!" else "Copy", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        if (!isExportingPdf) {
                                            isExportingPdf = true
                                            scope.launch {
                                                try {
                                                    pdfOutput = ClinicalPdfExporter.generateClinicalReportPdf(
                                                        context = context,
                                                        reportTitle = selectedReportType.title,
                                                        bodyText = exportedText,
                                                        clinicianName = currentUser.name,
                                                        department = currentUser.department
                                                    )
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "PDF failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    isExportingPdf = false
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricLime,
                                        contentColor = com.example.ui.theme.OnLimeText
                                    ),
                                    modifier = Modifier.testTag("export_report_pdf_btn").height(36.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = com.example.ui.theme.OnLimeText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isExportingPdf) "..." else "PDF",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = com.example.ui.theme.OnLimeText
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = exportedText,
                                color = Color(0xFFF8FAFC),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
