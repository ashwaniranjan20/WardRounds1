package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Today
import com.example.ui.components.CollapsibleRoundSection
import com.example.ui.components.RoundExpandCollapseControls
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.data.model.ClinicalNoteEntity
import com.example.data.model.FollowUpTaskEntity
import com.example.data.model.InvestigationOrderEntity
import com.example.data.model.InvestigationResultEntity
import com.example.data.model.InvestigationStatus
import com.example.data.model.PatientEntity
import com.example.data.model.TaskStatus
import com.example.data.model.RoundEntryEntity
import com.example.data.model.SyncStatus
import com.example.data.model.VitalObservationEntity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.example.data.model.DischargeStatus
import com.example.data.model.DischargeSummaryEntity
import com.example.domain.pdf.ClinicalPdfExporter
import com.example.domain.pdf.PdfExportOutput
import com.example.ui.components.PdfPreviewExportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientProfileScreen(
    patientId: String,
    initialTab: Int = 0,
    viewModel: WardRoundViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDailyRound: (String) -> Unit,
    onNavigateToDischarge: (String) -> Unit,
    onNavigateToHandover: (String) -> Unit = {},
    onNavigateToEditPatient: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val patientState by viewModel.getPatient(patientId).collectAsStateWithLifecycle(initialValue = null)
    val patient = patientState ?: return

    val rounds by viewModel.getRoundsForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())
    val vitals by viewModel.getVitalsForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())
    val notes by viewModel.getNotesForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())
    val orders by viewModel.getOrdersForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())
    val results by viewModel.getResultsForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())
    val dischargeSummary by viewModel.getDischargeSummaryForPatient(patientId).collectAsStateWithLifecycle(initialValue = null)
    val followUpTasks by viewModel.getFollowUpTasksForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedTabIndex by remember { mutableStateOf(initialTab) }
    val tabTitles = listOf(
        "Overview",
        "History",
        "Notes",
        "Daily Rounds",
        "Investigations",
        "Trends",
        "Treatment",
        "Discharge",
        "Follow-ups"
    )

    // Dialog state for "+ Add Investigation Result directly in Note" (Section 11)
    var showAddResultDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showOrderTestDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            WardRoundTopBar(
                title = patient.name,
                subtitle = "UHID: ${patient.uhid} • ${patient.ward} (Bed ${patient.bedNumber})",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        var headerOffsetPx by remember { mutableFloatStateOf(0f) }
        val headerHeightRef = remember { intArrayOf(0) }

        LaunchedEffect(selectedTabIndex, patient.id) {
            headerOffsetPx = 0f
        }

        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    val maxCollapse = headerHeightRef[0].toFloat()
                    if (maxCollapse <= 0f) return Offset.Zero
                    if (delta < 0f && headerOffsetPx > -maxCollapse) {
                        val oldOffset = headerOffsetPx
                        val newOffset = (oldOffset + delta).coerceIn(-maxCollapse, 0f)
                        headerOffsetPx = newOffset
                        return Offset(0f, newOffset - oldOffset)
                    }
                    if (delta > 0f && headerOffsetPx < 0f) {
                        val oldOffset = headerOffsetPx
                        val newOffset = (oldOffset + delta).coerceIn(-maxCollapse, 0f)
                        headerOffsetPx = newOffset
                        return Offset(0f, newOffset - oldOffset)
                    }
                    return Offset.Zero
                }
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(nestedScrollConnection)
        ) {
            // Smoothly Scroll-Collapsing PATIENT HEADER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0))
                        val fullHeight = placeable.height
                        headerHeightRef[0] = fullHeight
                        val clampedOffset = headerOffsetPx.roundToInt().coerceIn(-fullHeight, 0)
                        val visibleHeight = (fullHeight + clampedOffset).coerceIn(0, fullHeight)
                        layout(placeable.width, visibleHeight) {
                            placeable.placeRelative(0, clampedOffset)
                        }
                    }
            ) {
                PatientProfileHeader(
                    patient = patient,
                    roundsCount = rounds.size,
                    latestRound = rounds.firstOrNull(),
                    onAddRound = { onNavigateToDailyRound(patient.id) },
                    onAddNote = { showAddNoteDialog = true },
                    onOrderTest = { showOrderTestDialog = true },
                    onAddResult = { showAddResultDialog = true },
                    onViewTrends = { selectedTabIndex = 5 },
                    onPrepareDischarge = { onNavigateToDischarge(patient.id) },
                    onHandover = { onNavigateToHandover(patient.id) },
                    onEditPatient = { onNavigateToEditPatient(patient.id) }
                )
            }

            // 9 PATIENT TABS (Pinned directly below collapsing header)
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            // Tab Content (Fills all remaining space for generous scrolling)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTabIndex) {
                    0 -> PatientOverviewTab(patient, rounds.firstOrNull(), vitals.firstOrNull(), results, orders, followUpTasks)
                    1 -> PatientHistoryTab(patient)
                    2 -> PatientNotesTab(
                        notes = notes,
                        onAddNote = { showAddNoteDialog = true },
                        onAddResult = { showAddResultDialog = true }
                    )
                    3 -> PatientDailyRoundsTab(
                        patient = patient,
                        rounds = rounds,
                        latestVital = vitals.firstOrNull(),
                        orders = orders,
                        results = results,
                        onAddRound = { onNavigateToDailyRound(patient.id) }
                    )
                    4 -> PatientInvestigationsTab(
                        orders = orders,
                        results = results,
                        onOrderTest = { showOrderTestDialog = true },
                        onAddResult = { showAddResultDialog = true }
                    )
                    5 -> TrendDashboardTab(
                        patientId = patient.id,
                        viewModel = viewModel
                    )
                    6 -> PatientTreatmentTab(rounds.firstOrNull(), patient)
                    7 -> PatientDischargeTab(
                        summary = dischargeSummary,
                        patient = patient,
                        viewModel = viewModel,
                        onOpenDischargeEditor = { onNavigateToDischarge(patient.id) }
                    )
                    8 -> PatientFollowUpsTab(
                        tasks = followUpTasks,
                        onTaskToggle = { taskId -> viewModel.completeFollowUpTask(taskId) }
                    )
                }
            }
        }
    }

    // Modal Sheet: Add Investigation Result Directly (Section 11)
    if (showAddResultDialog) {
        AddInvestigationResultDialog(
            patientId = patient.id,
            viewModel = viewModel,
            onDismiss = { showAddResultDialog = false },
            onSaved = { showAddResultDialog = false }
        )
    }

    // Modal Sheet: Add Quick Clinical Note
    if (showAddNoteDialog) {
        AddClinicalNoteModal(
            patient = patient,
            viewModel = viewModel,
            onDismiss = { showAddNoteDialog = false }
        )
    }

    // Modal Sheet: Quick Order Test
    if (showOrderTestDialog) {
        QuickOrderTestModal(
            patient = patient,
            viewModel = viewModel,
            onDismiss = { showOrderTestDialog = false }
        )
    }
}

@Composable
private fun PatientProfileHeader(
    patient: PatientEntity,
    roundsCount: Int,
    latestRound: RoundEntryEntity?,
    onAddRound: () -> Unit,
    onAddNote: () -> Unit,
    onOrderTest: () -> Unit,
    onAddResult: () -> Unit,
    onViewTrends: () -> Unit,
    onPrepareDischarge: () -> Unit,
    onHandover: () -> Unit,
    onEditPatient: () -> Unit = {}
) {
    val diagnosisBody = patient.primaryDiagnosis.ifBlank { "Inpatient Assessment" }
    var isDiagnosisExpanded by remember(patient.id) { mutableStateOf(false) }
    var isDiagnosisTruncated by remember(diagnosisBody) {
        mutableStateOf(diagnosisBody.length > 75)
    }
    val canToggleDiagnosis = isDiagnosisTruncated || isDiagnosisExpanded

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = patient.name.take(2).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = patient.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "UHID: ${patient.uhid} • ${patient.age}y/${patient.sex ?: "M"} • Adm: ${patient.admissionDate}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (patient.isDischarged) MaterialTheme.colorScheme.surfaceVariant else ElectricLime.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (patient.isDischarged) "Discharged" else "Active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (patient.isDischarged) MaterialTheme.colorScheme.onSurfaceVariant else com.example.ui.theme.OnLimeText
                        )
                    }

                    IconButton(
                        onClick = onEditPatient,
                        modifier = Modifier
                            .testTag("patient_header_edit_button")
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit patient details",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Compact 1-2 Line Diagnosis Strip with Tap-to-Expand & Bounded Scroll Height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("patient_header_diagnosis_box")
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    .then(
                        if (canToggleDiagnosis) {
                            Modifier.clickable { isDiagnosisExpanded = !isDiagnosisExpanded }
                        } else {
                            Modifier
                        }
                    )
                    .semantics {
                        contentDescription = if (canToggleDiagnosis) {
                            if (isDiagnosisExpanded) "Collapse full diagnosis" else "Expand full diagnosis"
                        } else {
                            "Primary diagnosis"
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (isDiagnosisExpanded) {
                                    Modifier
                                        .heightIn(min = 56.dp, max = 120.dp)
                                        .verticalScroll(rememberScrollState())
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Text(
                            text = "Diagnosis: $diagnosisBody",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (isDiagnosisExpanded) Int.MAX_VALUE else 2,
                            overflow = if (isDiagnosisExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                            onTextLayout = { result ->
                                if (!isDiagnosisExpanded && result.hasVisualOverflow) {
                                    isDiagnosisTruncated = true
                                }
                            }
                        )
                    }
                    if (canToggleDiagnosis) {
                        Icon(
                            imageVector = if (isDiagnosisExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Actions Horizontal Scroll (Section 8)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton("Edit Details", Icons.Default.Edit, onEditPatient, Modifier.testTag("quick_action_edit_patient"))
                QuickActionButton("+ Today's Round", Icons.Default.Today, onAddRound)
                QuickActionButton("+ Clinical Note", Icons.Default.NoteAdd, onAddNote)
                QuickActionButton("+ Order Test", Icons.Default.Biotech, onOrderTest)
                QuickActionButton("+ Add Result", Icons.Default.Science, onAddResult)
                QuickActionButton("View Trends", Icons.Default.Timeline, onViewTrends)
                QuickActionButton("Handover", Icons.Default.SwapHoriz, onHandover)
                QuickActionButton("Discharge Plan", Icons.Default.ExitToApp, onPrepareDischarge)
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun PatientOverviewTab(
    patient: PatientEntity,
    latestRound: RoundEntryEntity?,
    latestVital: VitalObservationEntity?,
    results: List<InvestigationResultEntity>,
    orders: List<InvestigationOrderEntity>,
    tasks: List<FollowUpTaskEntity>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Latest Vitals Overview
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Text("Current Vital Signs", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeepRoyalNavy)
                        Text(
                            text = latestVital?.let { "${it.measurementDate} ${it.measurementTime}" } ?: "Not recorded",
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (latestVital != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            VitalStatBox("BP", "${latestVital.bpSystolic ?: "--"}/${latestVital.bpDiastolic ?: "--"}", "mmHg")
                            VitalStatBox("Pulse", "${latestVital.pulseRate ?: "--"}", "bpm")
                            VitalStatBox("Temp", "${latestVital.temperatureF ?: "--"}", "°F")
                            VitalStatBox("SpO2", "${latestVital.spO2 ?: "--"}%", latestVital.oxygenDeliveryMethod)
                        }
                    } else {
                        Text("No vitals recorded yet. Tap '+ Today's Round' to enter vitals.", fontSize = 12.sp, color = SecondaryText)
                    }
                }
            }
        }

        // Latest Round Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Latest Clinical Round (Day ${latestRound?.hospitalDay ?: 1})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeepRoyalNavy)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (latestRound != null) {
                        Text(
                            text = "Condition: ${latestRound.currentCondition.ifBlank { "Recorded by ${latestRound.doctorName}" }}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryText
                        )
                        if (latestRound.clinicalAssessment.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Assessment: ${latestRound.clinicalAssessment}", fontSize = 12.sp, color = SecondaryText)
                        }
                        if (latestRound.treatmentChanges.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Treatment Plan: ${latestRound.treatmentChanges}", fontSize = 12.sp, color = SecondaryText)
                        }
                    } else {
                        Text("No rounds documented yet. Tap '+ Today's Round' to start.", fontSize = 12.sp, color = SecondaryText)
                    }
                }
            }
        }

        // Pending items summary
        item {
            val pendingOrders = orders.filter { it.currentStatus !in listOf(InvestigationStatus.RESULT_REVIEWED, InvestigationStatus.COMPLETED, InvestigationStatus.CANCELLED) }
            val openTasks = tasks.filter { it.status != com.example.data.model.TaskStatus.COMPLETED }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pending Ward Tasks & Tests", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeepRoyalNavy)
                    Text("• ${pendingOrders.size} lab/imaging order(s) pending completion", fontSize = 12.sp, color = PrimaryText)
                    Text("• ${openTasks.size} follow-up task(s) active", fontSize = 12.sp, color = PrimaryText)
                }
            }
        }
    }
}

@Composable
private fun VitalStatBox(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = SecondaryText)
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DeepRoyalNavy)
        Text(text = unit, fontSize = 9.sp, color = SecondaryText)
    }
}

@Composable
private fun PatientHistoryTab(patient: PatientEntity) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { HistorySectionCard("Primary & Provisional Diagnoses", "${patient.primaryDiagnosis}\nProvisional: ${patient.provisionalDiagnosis.ifBlank { "None" }}") }
        item { HistorySectionCard("Drug Allergies (Safety Invariant)", patient.allergies.ifBlank { "NKDA" }, isAlert = patient.allergies.isNotBlank() && !patient.allergies.contains("NKDA", ignoreCase = true)) }
        item { HistorySectionCard("Comorbidities", patient.comorbidities.ifBlank { "None reported" }) }
        item { HistorySectionCard("Past Medical History", patient.pastMedicalHistory.ifBlank { "None reported" }) }
        item { HistorySectionCard("Past Surgical History", patient.pastSurgicalHistory.ifBlank { "None" }) }
        item { HistorySectionCard("Emergency Contact", patient.contactInfo.ifBlank { "Hospital ward contact on file" }) }
    }
}

@Composable
private fun HistorySectionCard(title: String, content: String, isAlert: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) StatusAlert.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isAlert) StatusAlert else MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isAlert) StatusAlert else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

// SECTION 10 & 11: Clinical Notes Timeline + "+ Add Investigation Result Directly in Note"
@Composable
private fun PatientNotesTab(
    notes: List<ClinicalNoteEntity>,
    onAddNote: () -> Unit,
    onAddResult: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Clinical Notes Timeline (${notes.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepRoyalNavy
                )
                Button(
                    onClick = onAddNote,
                    modifier = Modifier.testTag("add_clinical_note_btn").height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRoyalNavy)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Note", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (notes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No clinical notes yet. Tap 'Add Note' to document.", color = SecondaryText, fontSize = 13.sp)
                }
            }
        } else {
            items(notes, key = { it.id }) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            Column {
                                Text(
                                    text = "${note.date} at ${note.time} • Day ${note.hospitalDay}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DeepRoyalNavy
                                )
                                Text(
                                    text = "By ${note.author} (${note.noteType})",
                                    fontSize = 11.sp,
                                    color = SecondaryText
                                )
                            }

                            if (note.isDraft) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(StatusPending.copy(alpha = 0.25f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Draft", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A6700))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = note.content,
                            fontSize = 13.sp,
                            color = PrimaryText,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // SECTION 11 MANDATE: Inside every daily note, provide "+ Add Investigation Result"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onAddResult,
                                modifier = Modifier
                                    .testTag("add_investigation_result_in_note_btn")
                                    .height(34.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PeriwinkleBlue)
                            ) {
                                Icon(Icons.Default.Science, contentDescription = null, tint = DeepRoyalNavy, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Add Investigation Result", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DeepRoyalNavy)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientDailyRoundsTab(
    patient: PatientEntity,
    rounds: List<RoundEntryEntity>,
    latestVital: VitalObservationEntity?,
    orders: List<InvestigationOrderEntity>,
    results: List<InvestigationResultEntity>,
    onAddRound: () -> Unit
) {
    val latestRound = rounds.firstOrNull()

    // Default expand/collapse state: Logical workflow per specification
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Ward Rounds",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${rounds.size} documented round session(s)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onAddRound,
                    modifier = Modifier.testTag("add_new_round_button").height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Round", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ==========================================
        // EXPAND ALL / COLLAPSE ALL GLOBAL CONTROLS
        // ==========================================
        item {
            RoundExpandCollapseControls(
                expandedCount = expandedSections.size,
                totalCount = 10,
                onExpandAll = { expandedSections = (1..10).toSet() },
                onCollapseAll = { expandedSections = emptySet() }
            )
        }

        // ==========================================
        // 1. PATIENT INFORMATION
        // ==========================================
        item {
            val summary = "${patient.name} · UHID: ${patient.uhid} · ${patient.age}y/${patient.sex ?: "M"} · ${patient.ward} Bed ${patient.bedNumber} · ${patient.primaryDiagnosis}"
            CollapsibleRoundSection(
                sectionNumber = 1,
                title = "Patient Information",
                icon = Icons.Default.Person,
                isExpanded = expandedSections.contains(1),
                onToggle = { toggleSection(1) },
                collapsedSummary = summary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Patient Name", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(patient.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("UHID", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(patient.uhid, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Age / Sex", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${patient.age}y / ${patient.sex ?: "M"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ward / Bed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${patient.ward} • Bed ${patient.bedNumber}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                }
            }
        }

        // ==========================================
        // 2. TODAY'S CLINICAL STATUS
        // ==========================================
        item {
            val condition = latestRound?.currentCondition?.ifBlank { null } ?: "Stable, alert, oriented"
            val complaints = latestRound?.currentComplaints?.ifBlank { null }
            val progression = latestRound?.symptomProgression?.ifBlank { null }
            val summary = listOfNotNull(
                "Condition: $condition",
                complaints?.let { "Complaints: $it" },
                progression?.let { "Progression: $it" }
            ).joinToString(" · ")

            CollapsibleRoundSection(
                sectionNumber = 2,
                title = "Today's Clinical Status",
                icon = Icons.Default.EventNote,
                isExpanded = expandedSections.contains(2),
                onToggle = { toggleSection(2) },
                collapsedSummary = summary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "General Condition: $condition", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    if (!complaints.isNullOrBlank()) {
                        Text(text = "Symptoms & Complaints: $complaints", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (!progression.isNullOrBlank()) {
                        Text(text = "Symptom Progression: $progression", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!latestRound?.feverEvents.isNullOrBlank()) {
                        Text(text = "Fever Events: ${latestRound.feverEvents}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ==========================================
        // 3. VITAL SIGNS
        // ==========================================
        item {
            val vitalsSummary = if (latestVital != null) {
                listOfNotNull(
                    if (latestVital.bpSystolic != null && latestVital.bpDiastolic != null) "BP ${latestVital.bpSystolic}/${latestVital.bpDiastolic} mmHg" else null,
                    latestVital.pulseRate?.let { "PR $it/min" },
                    latestVital.respiratoryRate?.let { "RR $it/min" },
                    latestVital.temperatureF?.let { "Temp $it°F" },
                    latestVital.spO2?.let { "SpO₂ $it% (${latestVital.oxygenDeliveryMethod})" }
                ).joinToString(" · ")
            } else "No recorded vital parameters"

            CollapsibleRoundSection(
                sectionNumber = 3,
                title = "Vital Signs",
                icon = Icons.Default.Favorite,
                isExpanded = expandedSections.contains(3),
                onToggle = { toggleSection(3) },
                collapsedSummary = vitalsSummary
            ) {
                if (latestVital != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            VitalStatBox("BP", "${latestVital.bpSystolic ?: "--"}/${latestVital.bpDiastolic ?: "--"}", "mmHg")
                            VitalStatBox("Pulse", "${latestVital.pulseRate ?: "--"}", "bpm")
                            VitalStatBox("Temp", "${latestVital.temperatureF ?: "--"}", "°F")
                            VitalStatBox("SpO2", "${latestVital.spO2 ?: "--"}%", latestVital.oxygenDeliveryMethod)
                        }
                        if (latestVital.bloodGlucoseMgDl != null) {
                            Text(
                                text = "Blood Glucose: ${latestVital.bloodGlucoseMgDl} mg/dL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No vitals recorded for today yet. Tap 'New Round' to document.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ==========================================
        // 4. CLINICAL EXAMINATION
        // ==========================================
        item {
            val examSummary = listOfNotNull(
                latestRound?.respiratoryExam?.takeIf { it.isNotBlank() }?.let { "Resp: $it" },
                latestRound?.cvsExam?.takeIf { it.isNotBlank() }?.let { "CVS: $it" },
                latestRound?.abdomenExam?.takeIf { it.isNotBlank() }?.let { "Abd: $it" }
            ).joinToString(" · ").ifBlank { "Examination pending documentation" }

            CollapsibleRoundSection(
                sectionNumber = 4,
                title = "Clinical Examination",
                icon = Icons.Default.MedicalServices,
                isExpanded = expandedSections.contains(4),
                onToggle = { toggleSection(4) },
                collapsedSummary = examSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "General: ${latestRound?.generalExam?.ifBlank { "No distress, oriented" } ?: "Not documented"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Respiratory: ${latestRound?.respiratoryExam?.ifBlank { "Bilateral air entry present" } ?: "Not documented"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Cardiovascular: ${latestRound?.cvsExam?.ifBlank { "S1 S2 normal" } ?: "Not documented"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Abdomen: ${latestRound?.abdomenExam?.ifBlank { "Soft, non-tender" } ?: "Not documented"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "CNS: ${latestRound?.cnsExam?.ifBlank { "Conscious, alert" } ?: "Not documented"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // ==========================================
        // 5. INVESTIGATIONS
        // ==========================================
        item {
            val invSummary = "${orders.size} test(s) ordered · ${results.size} result(s) received"
            CollapsibleRoundSection(
                sectionNumber = 5,
                title = "Investigations",
                icon = Icons.Default.Biotech,
                isExpanded = expandedSections.contains(5),
                onToggle = { toggleSection(5) },
                collapsedSummary = invSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Investigations Ordered (${orders.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (orders.isEmpty()) {
                        Text("No active pending orders", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        orders.take(3).forEach { o ->
                            Text("• ${o.investigationName} (${o.currentStatus.name})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Recent Results Received (${results.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    results.take(3).forEach { r ->
                        Text("• ${r.parameterName}: ${r.originalTextValue} ${r.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ==========================================
        // 6. INVESTIGATION TRENDS
        // ==========================================
        item {
            val trendsSummary = "Serial trends active · View dynamic curves in Trends tab"
            CollapsibleRoundSection(
                sectionNumber = 6,
                title = "Investigation Trends",
                icon = Icons.Default.Timeline,
                isExpanded = expandedSections.contains(6),
                onToggle = { toggleSection(6) },
                collapsedSummary = trendsSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Laboratory Parameters & Trend Progression",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Inflammatory trajectory: Serial CBC, CRP, and electrolytes are tracked automatically.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Tap the 'Trends' tab above to inspect full interactive multi-day charts with reference ranges.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ==========================================
        // 7. CLINICAL ASSESSMENT
        // ==========================================
        item {
            val assess = latestRound?.clinicalAssessment?.takeIf { it.isNotBlank() } ?: "Under evaluation, responding to therapy"
            val diag = latestRound?.currentDiagnosis?.takeIf { it.isNotBlank() } ?: patient.primaryDiagnosis
            val assessSummary = "Dx: $diag · $assess"

            CollapsibleRoundSection(
                sectionNumber = 7,
                title = "Clinical Assessment",
                icon = Icons.Default.LocalHospital,
                isExpanded = expandedSections.contains(7),
                onToggle = { toggleSection(7) },
                collapsedSummary = assessSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Working Diagnosis: $diag",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = "Clinical Interpretation: $assess", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 17.sp)
                }
            }
        }

        // ==========================================
        // 8. TREATMENT AND MANAGEMENT PLAN
        // ==========================================
        item {
            val plan = latestRound?.treatmentChanges?.takeIf { it.isNotBlank() } ?: latestRound?.medicationPlan?.takeIf { it.isNotBlank() } ?: "Continue current ward management protocol"
            val planSummary = "Plan: $plan"

            CollapsibleRoundSection(
                sectionNumber = 8,
                title = "Treatment and Management Plan",
                icon = Icons.Default.CheckCircle,
                isExpanded = expandedSections.contains(8),
                onToggle = { toggleSection(8) },
                collapsedSummary = planSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Treatment Plan: $plan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    if (!latestRound?.medicationPlan.isNullOrBlank()) {
                        Text(text = "Medications: ${latestRound?.medicationPlan}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (!latestRound?.monitoringPlan.isNullOrBlank()) {
                        Text(text = "Monitoring: ${latestRound?.monitoringPlan}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ==========================================
        // 9. PENDING TASKS
        // ==========================================
        item {
            val pendingSummary = "Outstanding reviews: ${orders.count { it.currentStatus.name.contains("PENDING") || it.currentStatus.name.contains("ORDERED") }} pending test(s)"
            CollapsibleRoundSection(
                sectionNumber = 9,
                title = "Pending Tasks",
                icon = Icons.Default.PendingActions,
                isExpanded = expandedSections.contains(9),
                onToggle = { toggleSection(9) },
                collapsedSummary = pendingSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "• Review evening laboratory reports and notify doctor on call", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "• Check nursing vitals sheet at 18:00", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "• Confirm medication administration compliance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // ==========================================
        // 10. FOLLOW-UP AND DISCHARGE PLANNING
        // ==========================================
        item {
            val dischargeSummary = if (patient.isDischarged) "Patient Discharged" else "Discharge target: Day 4 · Subject to clinical stability"
            CollapsibleRoundSection(
                sectionNumber = 10,
                title = "Follow-up and Discharge Planning",
                icon = Icons.Default.PlaylistAddCheck,
                isExpanded = expandedSections.contains(10),
                onToggle = { toggleSection(10) },
                collapsedSummary = dischargeSummary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Discharge Readiness: ${if (patient.isDischarged) "Discharged" else "Clinical trajectory improving, planned in 24-48 hours"}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Discharge Advice: Complete course of oral antibiotics, maintain adequate hydration", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Follow-up Plan: Outpatient Medicine review within 7 days", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Chronological Round History section below
        if (rounds.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Previous Rounds History (${rounds.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            items(rounds, key = { it.id }) { round ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${round.roundDate} (${round.roundTime}) • Day ${round.hospitalDay}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Dr. ${round.doctorName} • ${round.roundType.name} Round",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (round.copiedFromPreviousRound) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Copied Forward", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (round.currentCondition.isNotBlank()) {
                            Text(text = "Condition: ${round.currentCondition}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (round.clinicalAssessment.isNotBlank()) {
                            Text(text = "Assessment: ${round.clinicalAssessment}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (round.treatmentChanges.isNotBlank()) {
                            Text(text = "Plan: ${round.treatmentChanges}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// SECTION 13: Day-Wise Investigation Table
@Composable
private fun PatientInvestigationsTab(
    orders: List<InvestigationOrderEntity>,
    results: List<InvestigationResultEntity>,
    onOrderTest: () -> Unit,
    onAddResult: () -> Unit
) {
    // Group results and orders by date (Section 13 layout)
    val dates = (orders.map { it.orderDate } + results.map { it.measurementDate }).distinct().sortedDescending()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Day-Wise Investigation Tracker",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepRoyalNavy
                )
                Row {
                    OutlinedButton(
                        onClick = onAddResult,
                        modifier = Modifier.height(34.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+ Result", fontSize = 11.sp, color = DeepRoyalNavy)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onOrderTest,
                        modifier = Modifier.testTag("order_investigation_btn").height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepRoyalNavy)
                    ) {
                        Text("+ Order", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (dates.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No investigations ordered or recorded yet.", color = SecondaryText, fontSize = 13.sp)
                }
            }
        } else {
            dates.forEach { date ->
                val dayOrders = orders.filter { it.orderDate == date }
                val dayResults = results.filter { it.measurementDate == date }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Date header
                            Text(
                                text = "Date: $date",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepRoyalNavy
                            )

                            // Table of investigations on this date
                            if (dayResults.isNotEmpty()) {
                                Text("Recorded Results:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                dayResults.forEach { res ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MutedSurface)
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = "${res.parameterName} (${res.investigationName})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                                            if (res.refRangeText.isNotBlank()) {
                                                Text(text = "Ref: ${res.refRangeText}", fontSize = 10.sp, color = SecondaryText)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${res.originalTextValue} ${res.unit}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (res.isAbnormal) StatusAlert else DeepRoyalNavy
                                            )
                                            if (res.isReviewed) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Reviewed", tint = DeepRoyalNavy, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            if (dayOrders.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Orders & Workflow Status:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                dayOrders.forEach { ord ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = ord.investigationName, fontSize = 12.sp, color = PrimaryText)
                                        Text(
                                            text = ord.currentStatus.name.replace("_", " "),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PeriwinkleBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientTreatmentTab(latestRound: RoundEntryEntity?, patient: PatientEntity) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Active Medication Plan", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRoyalNavy)
                Text(
                    text = latestRound?.medicationPlan?.ifBlank { "No active medication protocol recorded." }
                        ?: "No active medication protocol recorded.",
                    fontSize = 13.sp,
                    color = PrimaryText,
                    lineHeight = 18.sp
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diet, Fluid & Respiratory Orders", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRoyalNavy)
                Text(text = "Diet: ${latestRound?.dietFluidAdvice ?: "Normal balanced diet"}", fontSize = 13.sp, color = PrimaryText)
                Text(text = "Respiratory: ${latestRound?.respiratorySupportPlan ?: "Room air"}", fontSize = 13.sp, color = PrimaryText)
                Text(text = "Monitoring: ${latestRound?.monitoringPlan ?: "Routine 4-hourly ward monitoring"}", fontSize = 13.sp, color = PrimaryText)
            }
        }
    }
}

@Composable
private fun PatientDischargeTab(
    summary: DischargeSummaryEntity?,
    patient: PatientEntity,
    viewModel: WardRoundViewModel,
    onOpenDischargeEditor: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var pdfOutput by remember { mutableStateOf<PdfExportOutput?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }

    if (pdfOutput != null) {
        PdfPreviewExportDialog(
            pdfOutput = pdfOutput!!,
            onDismiss = { pdfOutput = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Discharge Summary Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (summary?.isFinalized == true) ElectricLime else StatusPending.copy(alpha = 0.22f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = summary?.dischargeStatus?.name?.replace("_", " ") ?: "Not Planned",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (summary?.isFinalized == true) com.example.ui.theme.OnLimeText else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (summary != null) {
                    Text(
                        text = "Final Diagnosis: ${summary.finalDiagnosis.ifBlank { "Pending finalization" }}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Condition: ${summary.conditionAtDischarge}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Follow-up: ${summary.followUpDepartment} on ${summary.followUpDate.ifBlank { "TBD" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "No saved discharge summary yet. You can prepare a formal plan or export a working draft PDF from chart details.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onOpenDischargeEditor,
                    modifier = Modifier
                        .testTag("open_discharge_editor_btn")
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (summary == null) "Prepare Discharge Plan" else "Edit Discharge Summary", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        if (isExportingPdf) return@OutlinedButton
                        isExportingPdf = true
                        scope.launch {
                            try {
                                val effectiveSummary = summary ?: DischargeSummaryEntity(
                                    id = UUID.randomUUID().toString(),
                                    patientId = patient.id,
                                    admissionId = patient.admissionNumber,
                                    admissionDiagnosis = patient.primaryDiagnosis,
                                    finalDiagnosis = patient.primaryDiagnosis,
                                    conditionAtDischarge = "Active Inpatient — Draft Discharge Summary",
                                    responsibleClinician = currentUser.name,
                                    dischargeStatus = DischargeStatus.UNDER_CONSIDERATION,
                                    isFinalized = false
                                )
                                pdfOutput = ClinicalPdfExporter.generateDischargeSummaryPdf(
                                    context = context,
                                    patient = patient,
                                    summary = effectiveSummary,
                                    clinicianName = currentUser.name
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "PDF error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExportingPdf = false
                            }
                        }
                    },
                    modifier = Modifier
                        .testTag("profile_export_discharge_pdf_btn")
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (summary?.isFinalized == true) "Export Discharge Summary PDF" else "Preview Draft Discharge PDF",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PatientFollowUpsTab(
    tasks: List<com.example.data.model.FollowUpTaskEntity>,
    onTaskToggle: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Scheduled Follow-ups (${tasks.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DeepRoyalNavy)
        }
        if (tasks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No scheduled follow-up tasks for this patient.", color = SecondaryText, fontSize = 13.sp)
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                FollowUpTaskItemCard(
                    task = task,
                    patient = null,
                    onToggleComplete = { onTaskToggle(task.id) },
                    onOpenPatient = {}
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddClinicalNoteModal(
    patient: PatientEntity,
    viewModel: WardRoundViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var noteContent by remember { mutableStateOf("") }
    var noteType by remember { mutableStateOf("Daily Progress Note") }
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val time = remember { SimpleDateFormat("HH:mm", Locale.US).format(Date()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = PureWhite) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Clinical Note", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DeepRoyalNavy)
            Text("Patient: ${patient.name} (${patient.uhid})", fontSize = 12.sp, color = SecondaryText)
            Divider(color = CardBorder)

            OutlinedTextField(
                value = noteContent,
                onValueChange = { noteContent = it },
                label = { Text("Clinical Note Content *") },
                placeholder = { Text("Document progress, symptoms, review of systems, or management plan") },
                modifier = Modifier.testTag("clinical_note_content_input").fillMaxWidth(),
                minLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    if (noteContent.isBlank()) return@Button
                    val note = ClinicalNoteEntity(
                        id = UUID.randomUUID().toString(),
                        patientId = patient.id,
                        admissionId = patient.admissionNumber,
                        date = today,
                        time = time,
                        author = currentUser.name,
                        noteType = noteType,
                        hospitalDay = viewModel.calculateHospitalDay(patient.admissionDate, today),
                        content = noteContent.trim(),
                        isDraft = false,
                        syncStatus = SyncStatus.SAVED_LOCALLY
                    )
                    viewModel.addClinicalNote(note) { onDismiss() }
                },
                modifier = Modifier.testTag("save_clinical_note_button").fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepRoyalNavy)
            ) {
                Text("Save Note", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickOrderTestModal(
    patient: PatientEntity,
    viewModel: WardRoundViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableStateOf("Biochemistry") }
    var investigationName by remember { mutableStateOf("Serum Electrolytes") }
    var priority by remember { mutableStateOf(com.example.data.model.InvestigationPriority.ROUTINE) }
    var indication by remember { mutableStateOf("") }
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val time = remember { SimpleDateFormat("HH:mm", Locale.US).format(Date()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = PureWhite) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Order Investigation", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DeepRoyalNavy)
            Text("Patient: ${patient.name} (${patient.uhid})", fontSize = 12.sp, color = SecondaryText)
            Divider(color = CardBorder)

            OutlinedTextField(
                value = investigationName,
                onValueChange = { investigationName = it },
                label = { Text("Investigation Name *") },
                modifier = Modifier.testTag("order_name_input").fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = indication,
                onValueChange = { indication = it },
                label = { Text("Clinical Indication") },
                placeholder = { Text("e.g. Check hypokalemia resolution") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    if (investigationName.isBlank()) return@Button
                    val order = InvestigationOrderEntity(
                        id = UUID.randomUUID().toString(),
                        patientId = patient.id,
                        admissionId = patient.admissionNumber,
                        orderingClinician = currentUser.name,
                        orderDate = today,
                        orderTime = time,
                        investigationName = investigationName.trim(),
                        category = selectedCategory,
                        priority = priority,
                        currentStatus = InvestigationStatus.ORDERED,
                        clinicalIndication = indication,
                        syncStatus = SyncStatus.SAVED_LOCALLY
                    )
                    viewModel.placeInvestigationOrder(order) { onDismiss() }
                },
                modifier = Modifier.testTag("confirm_order_test_btn").fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepRoyalNavy)
            ) {
                Text("Submit Order", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
