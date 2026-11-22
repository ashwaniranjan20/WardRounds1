package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FollowUpTaskEntity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class FollowUpFilter {
    TODAY,
    UPCOMING,
    OVERDUE,
    COMPLETED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpScreen(
    viewModel: WardRoundViewModel,
    onNavigateToPatient: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.allFollowUpTasks.collectAsStateWithLifecycle()
    val patients by viewModel.allPatients.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(FollowUpFilter.TODAY) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    // Partition tasks by status / date
    val todayTasks = remember(tasks, todayStr) {
        tasks.filter { it.status == TaskStatus.DUE_TODAY || (it.dueDate == todayStr && it.status != TaskStatus.COMPLETED) }
    }
    val upcomingTasks = remember(tasks, todayStr) {
        tasks.filter { it.dueDate > todayStr && it.status != TaskStatus.COMPLETED }
    }
    val overdueTasks = remember(tasks, todayStr) {
        tasks.filter { (it.status == TaskStatus.OVERDUE || it.dueDate < todayStr) && it.status != TaskStatus.COMPLETED }
    }
    val completedTasks = remember(tasks) {
        tasks.filter { it.status == TaskStatus.COMPLETED }
    }

    val displayedTasks = when (selectedFilter) {
        FollowUpFilter.TODAY -> todayTasks
        FollowUpFilter.UPCOMING -> upcomingTasks
        FollowUpFilter.OVERDUE -> overdueTasks
        FollowUpFilter.COMPLETED -> completedTasks
    }

    Scaffold(
        topBar = {
            WardRoundTopBar(
                title = "Follow-ups & Tasks",
                subtitle = "Inpatient Reviews & Appointments",
                showBackButton = false
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Task", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_task_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedFilter.ordinal,
                containerColor = PureWhite,
                contentColor = DeepRoyalNavy,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedFilter.ordinal]),
                        color = DeepRoyalNavy,
                        height = 3.dp
                    )
                }
            ) {
                FollowUpFilter.values().forEach { filter ->
                    val count = when (filter) {
                        FollowUpFilter.TODAY -> todayTasks.size
                        FollowUpFilter.UPCOMING -> upcomingTasks.size
                        FollowUpFilter.OVERDUE -> overdueTasks.size
                        FollowUpFilter.COMPLETED -> completedTasks.size
                    }
                    val label = when (filter) {
                        FollowUpFilter.TODAY -> "Today ($count)"
                        FollowUpFilter.UPCOMING -> "Upcoming ($count)"
                        FollowUpFilter.OVERDUE -> "Overdue ($count)"
                        FollowUpFilter.COMPLETED -> "Done ($count)"
                    }
                    Tab(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        text = {
                            Text(
                                text = label,
                                fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            if (displayedTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PeriwinkleBlue,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No ${selectedFilter.name.lowercase().replaceFirstChar { it.uppercase() }} Tasks",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepRoyalNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All scheduled clinical reviews and repeat tests are accounted for.",
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedTasks, key = { it.id }) { task ->
                        val patient = patients.find { it.id == task.patientId }
                        FollowUpTaskItemCard(
                            task = task,
                            patient = patient,
                            onToggleComplete = {
                                viewModel.completeFollowUpTask(task.id)
                            },
                            onOpenPatient = {
                                onNavigateToPatient(task.patientId)
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTaskModalBottomSheet(
            patients = patients,
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
fun FollowUpTaskItemCard(
    task: FollowUpTaskEntity,
    patient: PatientEntity?,
    onToggleComplete: () -> Unit,
    onOpenPatient: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDone = task.status == TaskStatus.COMPLETED

    Card(
        modifier = modifier
            .testTag("task_card_${task.id}")
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
                // Priority Tag
                val (pBg, pFg) = when (task.priority) {
                    TaskPriority.URGENT -> Pair(StatusAlert.copy(alpha = 0.2f), StatusAlert)
                    TaskPriority.HIGH -> Pair(StatusPending.copy(alpha = 0.25f), Color(0xFF9A6700))
                    else -> Pair(MutedSurface, DeepRoyalNavy)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(pBg)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${task.priority} Priority",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = pFg
                    )
                }

                Text(
                    text = "Due: ${task.dueDate} ${task.dueTime}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.status == TaskStatus.OVERDUE) StatusAlert else SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier
                        .testTag("task_toggle_${task.id}")
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Complete",
                        tint = if (isDone) DeepRoyalNavy else SecondaryText
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.description,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDone) SecondaryText else PrimaryText
                    )
                    Text(
                        text = "${task.taskType} • ${task.responsibleDepartmentOrDoctor}",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
            }

            if (patient != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MutedSurface)
                        .clickable { onOpenPatient() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Patient: ${patient.name} (${patient.uhid})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepRoyalNavy
                    )
                    Text(
                        text = "${patient.ward} (Bed ${patient.bedNumber})",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskModalBottomSheet(
    patients: List<PatientEntity>,
    viewModel: WardRoundViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPatientId by remember { mutableStateOf(patients.firstOrNull()?.id ?: "") }
    var taskType by remember { mutableStateOf("Outpatient Follow-up") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var dueTime by remember { mutableStateOf("10:00") }
    var dept by remember { mutableStateOf("Internal Medicine OPD") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Follow-up / Clinical Task",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepRoyalNavy
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Divider(color = CardBorder)

            // Select Patient
            Text("Select Patient", fontSize = 12.sp, color = SecondaryText, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                patients.forEach { pt ->
                    FilterChip(
                        selected = selectedPatientId == pt.id,
                        onClick = { selectedPatientId = pt.id },
                        label = { Text("${pt.name} (${pt.uhid})", fontSize = 11.sp) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeepRoyalNavy,
                            selectedLabelColor = PureWhite
                        )
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Task Description *") },
                placeholder = { Text("e.g. Check repeat blood culture clearance") },
                modifier = Modifier.testTag("task_description_input").fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = dueTime,
                    onValueChange = { dueTime = it },
                    label = { Text("Due Time") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = dept,
                onValueChange = { dept = it },
                label = { Text("Responsible Dept / Doctor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Priority
            Text("Priority Level", fontSize = 12.sp, color = SecondaryText, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TaskPriority.ROUTINE, TaskPriority.MEDIUM, TaskPriority.HIGH, TaskPriority.URGENT).forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p.name, fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeepRoyalNavy,
                            selectedLabelColor = PureWhite
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (description.isBlank()) return@Button
                    val targetPatient = patients.find { it.id == selectedPatientId }
                    val task = FollowUpTaskEntity(
                        id = UUID.randomUUID().toString(),
                        patientId = selectedPatientId,
                        uhid = targetPatient?.uhid ?: "DEMO",
                        taskType = taskType,
                        description = description.trim(),
                        dueDate = dueDate,
                        dueTime = dueTime,
                        responsibleDepartmentOrDoctor = dept,
                        priority = priority,
                        status = TaskStatus.SCHEDULED,
                        syncStatus = SyncStatus.SAVED_LOCALLY
                    )
                    viewModel.saveFollowUpTask(task)
                    onDismiss()
                },
                modifier = Modifier
                    .testTag("submit_task_button")
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepRoyalNavy)
            ) {
                Text("Create Task", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
