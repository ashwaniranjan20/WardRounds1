package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InvestigationStatus
import com.example.data.model.SyncStatus
import com.example.ui.components.PatientCard
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
import com.example.ui.viewmodel.PatientFilter
import com.example.ui.viewmodel.WardRoundViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WardRoundViewModel,
    onNavigateToPatient: (String) -> Unit,
    onNavigateToAddPatient: () -> Unit,
    onNavigateToHandover: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToEditPatient: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val patients by viewModel.dashboardPatients.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val todayRounds by viewModel.todayRounds.collectAsStateWithLifecycle()
    val allOrders by viewModel.allOrders.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val pendingHandoversCount by viewModel.pendingHandoversCount.collectAsStateWithLifecycle()

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    val reviewedPatientIds = remember(todayRounds) {
        todayRounds.map { it.patientId }.toSet()
    }

    val todayFormatted = remember {
        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.US).format(Date())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            WardRoundTopBar(
                title = "WardRound",
                subtitle = "Your Daily Ward Companion",
                syncStatus = syncStatus,
                isOffline = isOffline,
                onSyncClick = { viewModel.triggerSync() }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddPatient,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Patient") },
                text = { Text("Add Patient", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("home_add_patient_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with date & greeting
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = todayFormatted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Today's Ward Rounds",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier
                            .testTag("toggle_search_button")
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Expandable Search Bar
            if (isSearchExpanded) {
                item {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            viewModel.setSearchQuery(it)
                        },
                        placeholder = { Text("Search by name, UHID, ward, bed, or diagnosis...") },
                        modifier = Modifier
                            .testTag("home_search_input")
                            .fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = PeriwinkleBlue)
                        },
                        trailingIcon = {
                            if (searchText.isNotBlank()) {
                                IconButton(onClick = {
                                    searchText = ""
                                    viewModel.setSearchQuery("")
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = PureWhite,
                            unfocusedContainerColor = PureWhite,
                            focusedIndicatorColor = PeriwinkleBlue,
                            unfocusedIndicatorColor = CardBorder
                        )
                    )
                }
            }

            // Sync Notification banner (if offline or syncing)
            if (isOffline || syncStatus == SyncStatus.SYNC_PENDING || syncStatus == SyncStatus.SAVED_LOCALLY) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isOffline) Color(0xFFF1F5F9) else StatusPending.copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOffline) Icons.Default.Sync else Icons.Default.NotificationImportant,
                                contentDescription = null,
                                tint = if (isOffline) SecondaryText else Color(0xFF9A6700),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = syncMessage,
                                fontSize = 12.sp,
                                color = if (isOffline) PrimaryText else Color(0xFF9A6700),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 4 SUMMARY CARDS (Mandated by PDF page 7)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 1: Total Active Patients
                        DashboardMetricCard(
                            title = "Active Inpatients",
                            value = "${stats.totalActivePatients}",
                            subtitle = "${stats.notReviewedToday} pending review",
                            icon = Icons.Default.People,
                            accentColor = DeepRoyalNavy,
                            onClick = { viewModel.setFilter(PatientFilter.ALL) },
                            modifier = Modifier.weight(1f)
                        )

                        // Card 2: Reviewed Today
                        DashboardMetricCard(
                            title = "Reviewed Today",
                            value = "${stats.reviewedToday}",
                            subtitle = if (stats.totalActivePatients > 0) "${(stats.reviewedToday * 100) / stats.totalActivePatients}% completed" else "0%",
                            icon = Icons.Default.CheckCircle,
                            accentColor = ElectricLime,
                            onClick = { viewModel.setFilter(PatientFilter.NOT_REVIEWED) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 3: Investigations Pending
                        DashboardMetricCard(
                            title = "Labs Pending",
                            value = "${stats.pendingInvestigations}",
                            subtitle = "Sample/analysis in progress",
                            icon = Icons.Default.HourglassTop,
                            accentColor = StatusPending,
                            onClick = { viewModel.setFilter(PatientFilter.PENDING_INVESTIGATIONS) },
                            modifier = Modifier.weight(1f)
                        )

                        // Card 4: Results Awaiting Review
                        DashboardMetricCard(
                            title = "Awaiting Review",
                            value = "${stats.resultsAwaitingReview}",
                            subtitle = "Doctor sign-off needed",
                            icon = Icons.Default.NotificationImportant,
                            accentColor = StatusAlert,
                            onClick = { viewModel.setFilter(PatientFilter.RESULTS_AWAITING_REVIEW) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Shift Handover & PDF Reports Quick-Access Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_handover_card")
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigateToHandover() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Shift Handover",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Shift Handover",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (pendingHandoversCount > 0) "$pendingHandoversCount pending sign-off" else "SBAR • I-PASS Ready",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (pendingHandoversCount > 0) StatusAlert else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_reports_card")
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigateToReports() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ElectricLime),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "Reports and PDF",
                                    tint = com.example.ui.theme.OnLimeText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reports & PDF",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Ward & Lab Exports",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Patient Filter Chips (Mandated by PDF page 8)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PatientFilterChip(
                        label = "All (${stats.totalActivePatients})",
                        selected = currentFilter == PatientFilter.ALL,
                        onClick = { viewModel.setFilter(PatientFilter.ALL) }
                    )
                    PatientFilterChip(
                        label = "Not Reviewed (${stats.notReviewedToday})",
                        selected = currentFilter == PatientFilter.NOT_REVIEWED,
                        onClick = { viewModel.setFilter(PatientFilter.NOT_REVIEWED) }
                    )
                    PatientFilterChip(
                        label = "Pending Labs (${stats.pendingInvestigations})",
                        selected = currentFilter == PatientFilter.PENDING_INVESTIGATIONS,
                        onClick = { viewModel.setFilter(PatientFilter.PENDING_INVESTIGATIONS) }
                    )
                    PatientFilterChip(
                        label = "Awaiting Review (${stats.resultsAwaitingReview})",
                        selected = currentFilter == PatientFilter.RESULTS_AWAITING_REVIEW,
                        onClick = { viewModel.setFilter(PatientFilter.RESULTS_AWAITING_REVIEW) }
                    )
                    PatientFilterChip(
                        label = "Discharge Planned",
                        selected = currentFilter == PatientFilter.DISCHARGE_PLANNED,
                        onClick = { viewModel.setFilter(PatientFilter.DISCHARGE_PLANNED) }
                    )
                }
            }

            // Patient Cards Section Heading
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Admitted Patients (${patients.size})",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepRoyalNavy
                    )
                    Text(
                        text = "Sorted by Ward & Bed",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
            }

            // Patient Cards List
            if (patients.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(PureWhite)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.LocalHospital,
                                contentDescription = null,
                                tint = SecondaryText,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No patients match the current filter",
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Switch filters or tap '+ Add Patient' to register a new admission.",
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(patients, key = { it.id }) { patient ->
                    val isReviewed = patient.id in reviewedPatientIds
                    val patientPendingLabs = remember(allOrders, patient.id) {
                        allOrders.count {
                            it.patientId == patient.id &&
                                    it.currentStatus !in listOf(InvestigationStatus.RESULT_REVIEWED, InvestigationStatus.COMPLETED, InvestigationStatus.CANCELLED)
                        }
                    }

                    val lastRound = remember(todayRounds, patient.id) {
                        todayRounds.find { it.patientId == patient.id }
                    }

                    PatientCard(
                        patient = patient,
                        lastRoundDate = lastRound?.let { "${it.roundDate} (${it.roundTime})" },
                        isReviewedToday = isReviewed,
                        pendingInvestigationCount = patientPendingLabs,
                        latestUpdateText = lastRound?.symptomProgression?.ifBlank { lastRound.currentCondition },
                        onEditClick = { onNavigateToEditPatient(patient.id) },
                        onClick = { onNavigateToPatient(patient.id) }
                    )
                }
            }

            // Bottom space for FAB padding
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("metric_card_${title.lowercase().replace(" ", "_")}")
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (accentColor == ElectricLime) ElectricLime else accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (accentColor == ElectricLime) com.example.ui.theme.OnLimeText else accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PatientFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    )
}
