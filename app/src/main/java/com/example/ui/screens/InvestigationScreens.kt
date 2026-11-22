package com.example.ui.screens

import android.net.Uri
import com.example.domain.ocr.OcrDocumentType
import com.example.domain.ocr.OcrExtractionResult
import com.example.ui.components.OcrScanActionChip
import com.example.ui.ocr.OcrImageCaptureDialog
import com.example.ui.ocr.OcrReviewDraftScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.model.InvestigationEventEntity
import com.example.data.model.InvestigationOrderEntity
import com.example.data.model.InvestigationPriority
import com.example.data.model.InvestigationResultEntity
import com.example.data.model.InvestigationStatus
import com.example.data.model.PatientEntity
import com.example.data.model.ResultStatus
import com.example.data.model.SyncStatus
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

// Common predefined investigation catalog
val PREDEFINED_INVESTIGATIONS = mapOf(
    "Hematology" to listOf("Complete Blood Count (CBC)", "Platelet Count", "Peripheral Smear", "Coagulation Profile (PT/INR, aPTT)", "ESR"),
    "Biochemistry" to listOf("Kidney Function Test (KFT)", "Serum Creatinine", "Liver Function Test (LFT)", "C-Reactive Protein (CRP)", "Procalcitonin", "Serum Electrolytes", "Blood Glucose (Fasting/PP)", "HbA1c", "Lipid Profile"),
    "Microbiology" to listOf("Blood Culture & Sensitivity", "Sputum Gram Stain & Culture", "Urine Routine & Culture", "Throat Swab PCR", "Stool Routine & Culture"),
    "Radiology" to listOf("Chest X-Ray PA View", "Chest X-Ray Bedside", "Ultrasound Abdomen & Pelvis", "CT Thorax HRCT", "CT Brain Plain"),
    "Cardiology" to listOf("12-Lead Electrocardiogram (ECG)", "Echocardiogram (2D Echo)", "High-Sensitivity Troponin I", "NT-proBNP"),
    "Pulmonary" to listOf("Arterial Blood Gas (ABG)", "Spirometry", "Peak Expiratory Flow")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestigationMainScreen(
    viewModel: WardRoundViewModel,
    onNavigateToPatient: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Pending Tests", "Results Awaiting Review", "All Orders")

    val allOrders by viewModel.allOrders.collectAsStateWithLifecycle()
    val allResults by viewModel.allResults.collectAsStateWithLifecycle()
    val patients by viewModel.allPatients.collectAsStateWithLifecycle()

    val pendingOrders = remember(allOrders) {
        allOrders.filter { it.currentStatus !in listOf(InvestigationStatus.RESULT_REVIEWED, InvestigationStatus.COMPLETED, InvestigationStatus.CANCELLED) }
    }

    val unreviewedResults = remember(allResults) {
        allResults.filter { !it.isReviewed }
    }

    var selectedOrderForDetail by remember { mutableStateOf<InvestigationOrderEntity?>(null) }
    var showAddResultDialogForOrder by remember { mutableStateOf<InvestigationOrderEntity?>(null) }

    Scaffold(
        topBar = {
            WardRoundTopBar(
                title = "Investigation Hub",
                subtitle = "Orders, Results & Turnaround",
                showBackButton = false
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            val badge = when (index) {
                                0 -> if (pendingOrders.isNotEmpty()) " (${pendingOrders.size})" else ""
                                1 -> if (unreviewedResults.isNotEmpty()) " (${unreviewedResults.size})" else ""
                                else -> " (${allOrders.size})"
                            }
                            Text(
                                text = "$title$badge",
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    // Pending Orders list
                    if (pendingOrders.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.CheckCircle,
                            title = "No Pending Tests",
                            subtitle = "All ordered laboratory and imaging investigations have been completed."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(pendingOrders, key = { it.id }) { order ->
                                val patient = patients.find { it.id == order.patientId }
                                InvestigationOrderCard(
                                    order = order,
                                    patient = patient,
                                    onClick = { selectedOrderForDetail = order },
                                    onAdvanceStatus = { nextStatus ->
                                        viewModel.advanceOrderWorkflow(order, nextStatus)
                                    },
                                    onEnterResult = {
                                        showAddResultDialogForOrder = order
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Results Awaiting Clinician Review
                    if (unreviewedResults.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Verified,
                            title = "All Results Reviewed",
                            subtitle = "Every reported diagnostic parameter has received clinician verification."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(unreviewedResults, key = { it.id }) { result ->
                                val patient = patients.find { it.id == result.patientId }
                                ResultAwaitingReviewCard(
                                    result = result,
                                    patient = patient,
                                    onReview = {
                                        viewModel.reviewResult(result.id)
                                    },
                                    onViewPatient = {
                                        onNavigateToPatient(result.patientId)
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // All Orders
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(allOrders, key = { it.id }) { order ->
                            val patient = patients.find { it.id == order.patientId }
                            InvestigationOrderCard(
                                order = order,
                                patient = patient,
                                onClick = { selectedOrderForDetail = order },
                                onAdvanceStatus = { nextStatus ->
                                    viewModel.advanceOrderWorkflow(order, nextStatus)
                                },
                                onEnterResult = {
                                    showAddResultDialogForOrder = order
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for Workflow Event Details
    selectedOrderForDetail?.let { order ->
        val events by viewModel.getEventsForOrder(order.id).collectAsStateWithLifecycle(initialValue = emptyList())
        OrderWorkflowDetailModal(
            order = order,
            events = events,
            onDismiss = { selectedOrderForDetail = null },
            onAdvanceStatus = { nextStatus, comments ->
                viewModel.advanceOrderWorkflow(order, nextStatus, comments)
            }
        )
    }

    // Modal Sheet for Adding Result to Order
    showAddResultDialogForOrder?.let { order ->
        AddInvestigationResultDialog(
            patientId = order.patientId,
            defaultOrder = order,
            viewModel = viewModel,
            onDismiss = { showAddResultDialogForOrder = null },
            onSaved = { showAddResultDialogForOrder = null }
        )
    }
}

@Composable
fun InvestigationOrderCard(
    order: InvestigationOrderEntity,
    patient: PatientEntity?,
    onClick: () -> Unit,
    onAdvanceStatus: (InvestigationStatus) -> Unit,
    onEnterResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("order_card_${order.id}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority Badge
                val (priorityBg, priorityText) = when (order.priority) {
                    InvestigationPriority.STAT -> Pair(StatusAlert.copy(alpha = 0.2f), StatusAlert)
                    InvestigationPriority.URGENT -> Pair(StatusPending.copy(alpha = 0.25f), Color(0xFF9A6700))
                    InvestigationPriority.ROUTINE -> Pair(DeepRoyalNavy.copy(alpha = 0.1f), DeepRoyalNavy)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(priorityBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = order.priority.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = priorityText
                    )
                }

                // Workflow status badge
                val (statusBg, statusFg, statusLabel) = when (order.currentStatus) {
                    InvestigationStatus.ORDERED -> Triple(Color(0xFFE2E8F0), PrimaryText, "1. Ordered")
                    InvestigationStatus.COLLECTION_PENDING -> Triple(StatusPending.copy(alpha = 0.2f), Color(0xFF9A6700), "2. Sample Due")
                    InvestigationStatus.SAMPLE_COLLECTED -> Triple(PeriwinkleBlue.copy(alpha = 0.2f), DeepRoyalNavy, "3. Collected")
                    InvestigationStatus.SENT_TO_LAB -> Triple(PeriwinkleBlue.copy(alpha = 0.2f), DeepRoyalNavy, "4. In Transit")
                    InvestigationStatus.RECEIPT_CONFIRMED -> Triple(PeriwinkleBlue.copy(alpha = 0.2f), DeepRoyalNavy, "5. Lab Received")
                    InvestigationStatus.IN_PROGRESS -> Triple(StatusPending.copy(alpha = 0.2f), Color(0xFF9A6700), "6. Processing")
                    InvestigationStatus.RESULT_AVAILABLE -> Triple(ElectricLime.copy(alpha = 0.4f), DeepRoyalNavy, "7. Result In")
                    InvestigationStatus.RESULT_REVIEWED -> Triple(ElectricLime.copy(alpha = 0.4f), DeepRoyalNavy, "8. Reviewed")
                    InvestigationStatus.COMPLETED -> Triple(Color(0xFFDCFCE7), Color(0xFF166534), "Completed")
                    InvestigationStatus.CANCELLED -> Triple(Color(0xFFFEE2E2), StatusAlert, "Cancelled")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusFg
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = order.investigationName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DeepRoyalNavy
            )

            if (patient != null) {
                Text(
                    text = "${patient.name} (${patient.uhid}) • ${patient.ward} (Bed ${patient.bedNumber})",
                    fontSize = 12.sp,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Specimen: ${order.specimenInfo} • Ordered: ${order.orderDate} ${order.orderTime}",
                fontSize = 11.sp,
                color = SecondaryText
            )

            // Quick Next Workflow Action
            val nextStatus = when (order.currentStatus) {
                InvestigationStatus.ORDERED -> InvestigationStatus.SAMPLE_COLLECTED
                InvestigationStatus.COLLECTION_PENDING -> InvestigationStatus.SAMPLE_COLLECTED
                InvestigationStatus.SAMPLE_COLLECTED -> InvestigationStatus.SENT_TO_LAB
                InvestigationStatus.SENT_TO_LAB -> InvestigationStatus.RECEIPT_CONFIRMED
                InvestigationStatus.RECEIPT_CONFIRMED -> InvestigationStatus.IN_PROGRESS
                InvestigationStatus.IN_PROGRESS -> InvestigationStatus.RESULT_AVAILABLE
                InvestigationStatus.RESULT_AVAILABLE -> InvestigationStatus.RESULT_REVIEWED
                InvestigationStatus.RESULT_REVIEWED -> InvestigationStatus.COMPLETED
                else -> null
            }

            if (nextStatus != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (order.currentStatus in listOf(InvestigationStatus.RECEIPT_CONFIRMED, InvestigationStatus.IN_PROGRESS, InvestigationStatus.RESULT_AVAILABLE)) {
                        OutlinedButton(
                            onClick = onEnterResult,
                            modifier = Modifier
                                .testTag("enter_result_btn_${order.id}")
                                .height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DeepRoyalNavy)
                        ) {
                            Text("Enter Result", fontSize = 11.sp, color = DeepRoyalNavy, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = { onAdvanceStatus(nextStatus) },
                        modifier = Modifier
                            .testTag("advance_workflow_btn_${order.id}")
                            .height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepRoyalNavy)
                    ) {
                        Text("Mark: ${nextStatus.name.replace("_", " ")}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ResultAwaitingReviewCard(
    result: InvestigationResultEntity,
    patient: PatientEntity?,
    onReview: () -> Unit,
    onViewPatient: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("unreviewed_result_card_${result.id}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(StatusAlert.copy(alpha = 0.15f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Awaiting Clinician Review",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusAlert
                    )
                }

                Text(
                    text = "${result.measurementDate} ${result.measurementTime}",
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (patient != null) {
                Text(
                    text = "${patient.name} (UHID: ${patient.uhid})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${result.investigationName} • ${result.parameterName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepRoyalNavy
                    )
                    if (result.refRangeText.isNotBlank()) {
                        Text(
                            text = "Reference Range: ${result.refRangeText}",
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }
                }

                Text(
                    text = "${result.originalTextValue} ${result.unit}".trim(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (result.isAbnormal) StatusAlert else DeepRoyalNavy
                )
            }

            if (result.clinicalComments.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Comments: ${result.clinicalComments}",
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onViewPatient,
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open Patient", fontSize = 11.sp, color = DeepRoyalNavy)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onReview,
                    modifier = Modifier
                        .testTag("verify_result_button_${result.id}")
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRoyalNavy)
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve & Sign-Off", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderWorkflowDetailModal(
    order: InvestigationOrderEntity,
    events: List<InvestigationEventEntity>,
    onDismiss: () -> Unit,
    onAdvanceStatus: (InvestigationStatus, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PureWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = order.investigationName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepRoyalNavy
                    )
                    Text(
                        text = "Specimen: ${order.specimenInfo} • Priority: ${order.priority}",
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Divider(color = CardBorder)

            Text(
                text = "Chain of Custody & Workflow Events",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )

            // Timeline nodes
            val allStatuses = listOf(
                InvestigationStatus.ORDERED,
                InvestigationStatus.SAMPLE_COLLECTED,
                InvestigationStatus.SENT_TO_LAB,
                InvestigationStatus.RECEIPT_CONFIRMED,
                InvestigationStatus.IN_PROGRESS,
                InvestigationStatus.RESULT_AVAILABLE,
                InvestigationStatus.RESULT_REVIEWED,
                InvestigationStatus.COMPLETED
            )

            allStatuses.forEach { status ->
                val matchingEvent = events.find { it.eventStatus == status }
                val isReached = matchingEvent != null

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isReached) ElectricLime else Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isReached) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = DeepRoyalNavy)
                        } else {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SecondaryText))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = status.name.replace("_", " "),
                            fontSize = 13.sp,
                            fontWeight = if (isReached) FontWeight.Bold else FontWeight.Normal,
                            color = if (isReached) PrimaryText else SecondaryText
                        )
                        if (matchingEvent != null) {
                            Text(
                                text = "${matchingEvent.eventDateTimeFormatted} by ${matchingEvent.responsiblePerson}",
                                fontSize = 11.sp,
                                color = DeepRoyalNavy
                            )
                            if (matchingEvent.comments.isNotBlank()) {
                                Text(
                                    text = matchingEvent.comments,
                                    fontSize = 11.sp,
                                    color = SecondaryText
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AddInvestigationResultDialog(
    patientId: String,
    defaultOrder: InvestigationOrderEntity? = null,
    viewModel: WardRoundViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var category by remember { mutableStateOf(defaultOrder?.category ?: "Biochemistry") }
    var investigationName by remember { mutableStateOf(defaultOrder?.investigationName ?: "Kidney Function Test (KFT)") }
    var parameterName by remember { mutableStateOf(if (defaultOrder != null) "" else "Serum Creatinine") }
    var numericValueText by remember { mutableStateOf("") }
    var originalTextValue by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(if (category == "Hematology") "x10^3/uL" else "mg/dL") }
    var refRangeLowText by remember { mutableStateOf("") }
    var refRangeHighText by remember { mutableStateOf("") }
    var refRangeText by remember { mutableStateOf("") }
    var clinicalComments by remember { mutableStateOf("") }
    var isAbnormal by remember { mutableStateOf(false) }

    // OCR States
    var showOcrCaptureDialog by remember { mutableStateOf(false) }
    var ocrReviewData by remember { mutableStateOf<Triple<Uri, Int, OcrExtractionResult>?>(null) }

    val currentDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val currentTime = remember { SimpleDateFormat("HH:mm", Locale.US).format(Date()) }

    if (ocrReviewData != null) {
        val (uri, rot, result) = ocrReviewData!!
        OcrReviewDraftScreen(
            imageUri = uri,
            rotationDegrees = rot,
            extractionResult = result,
            onApplyFields = { applied ->
                val labs = applied.filterKeys { it.startsWith("lab_") }
                if (labs.isNotEmpty()) {
                    val firstLab = labs.entries.first()
                    parameterName = firstLab.key.removePrefix("lab_").replace('_', ' ').uppercase()
                    numericValueText = firstLab.value
                    originalTextValue = firstLab.value
                    if (labs.size > 1) {
                        clinicalComments = "Extracted parameters: " + labs.entries.joinToString("; ") { "${it.key.removePrefix("lab_").replace('_', ' ').uppercase()}: ${it.value}" }
                    }
                }
                ocrReviewData = null
            },
            onDiscard = { ocrReviewData = null }
        )
        return
    }

    if (showOcrCaptureDialog) {
        OcrImageCaptureDialog(
            targetDocType = OcrDocumentType.LAB_INVESTIGATION,
            existingFormValues = mapOf(
                "parameter" to parameterName,
                "value" to numericValueText,
                "unit" to unit
            ),
            onDismiss = { showOcrCaptureDialog = false },
            onOcrResultReady = { uri, rot, res ->
                showOcrCaptureDialog = false
                ocrReviewData = Triple(uri, rot, res)
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Enter Investigation Result",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DeepRoyalNavy
                    )
                    Text(
                        text = "Or scan printed laboratory report",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OcrScanActionChip(
                        onClick = { showOcrCaptureDialog = true },
                        label = "Scan Lab Slip",
                        testTag = "btn_ocr_scan_lab_slip"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Divider(color = CardBorder)

            OutlinedTextField(
                value = investigationName,
                onValueChange = { investigationName = it },
                label = { Text("Investigation Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = parameterName,
                onValueChange = { parameterName = it },
                label = { Text("Parameter Name (e.g. Creatinine, WBC, CRP)") },
                modifier = Modifier
                    .testTag("result_param_input")
                    .fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = numericValueText,
                    onValueChange = {
                        numericValueText = it
                        if (originalTextValue.isBlank() || originalTextValue.toDoubleOrNull() != null) {
                            originalTextValue = it
                        }
                    },
                    label = { Text("Numeric Value") },
                    placeholder = { Text("1.2") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .testTag("result_numeric_input")
                        .weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit") },
                    placeholder = { Text("mg/dL") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = originalTextValue,
                onValueChange = { originalTextValue = it },
                label = { Text("Original Recorded Text Value") },
                placeholder = { Text("e.g. 1.2 or 'Culture Positive'") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = refRangeLowText,
                    onValueChange = { refRangeLowText = it },
                    label = { Text("Ref Min") },
                    placeholder = { Text("0.7") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = refRangeHighText,
                    onValueChange = { refRangeHighText = it },
                    label = { Text("Ref Max") },
                    placeholder = { Text("1.3") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = clinicalComments,
                onValueChange = { clinicalComments = it },
                label = { Text("Clinical Comments") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val numericVal = numericValueText.toDoubleOrNull()
                        val low = refRangeLowText.toDoubleOrNull()
                        val high = refRangeHighText.toDoubleOrNull()

                        val result = InvestigationResultEntity(
                            id = UUID.randomUUID().toString(),
                            patientId = patientId,
                            admissionId = defaultOrder?.admissionId ?: "ADM-001",
                            orderId = defaultOrder?.id,
                            investigationName = investigationName.trim(),
                            category = category,
                            parameterName = parameterName.trim().ifBlank { investigationName.trim() },
                            numericValue = numericVal,
                            originalTextValue = originalTextValue.ifBlank { numericValueText },
                            unit = unit.trim(),
                            refRangeLow = low,
                            refRangeHigh = high,
                            refRangeText = if (low != null && high != null) "$low - $high $unit" else "",
                            resultStatus = ResultStatus.FINAL,
                            measurementDate = currentDate,
                            measurementTime = currentTime,
                            isReviewed = false,
                            isAbnormal = (low != null && numericVal != null && numericVal < low) || (high != null && numericVal != null && numericVal > high),
                            dataSource = "Hospital Laboratory (Entered)",
                            clinicalComments = clinicalComments,
                            syncStatus = SyncStatus.SAVED_LOCALLY
                        )
                        viewModel.saveInvestigationResult(result) {
                            onSaved()
                        }
                    },
                    modifier = Modifier.testTag("save_result_confirm_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRoyalNavy)
                ) {
                    Text("Save Result")
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PeriwinkleBlue,
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = DeepRoyalNavy
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SecondaryText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
