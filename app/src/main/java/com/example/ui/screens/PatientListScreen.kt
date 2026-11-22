package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InvestigationStatus
import com.example.data.model.PatientEntity
import com.example.ui.components.PatientCard
import com.example.ui.components.WardRoundTopBar
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DeepRoyalNavy
import com.example.ui.theme.PeriwinkleBlue
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SoftCloudWhite
import com.example.ui.viewmodel.WardRoundViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PatientListScreen(
    viewModel: WardRoundViewModel,
    onNavigateToPatient: (String) -> Unit,
    onNavigateToAddPatient: () -> Unit,
    onNavigateToEditPatient: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allPatients by viewModel.allPatients.collectAsStateWithLifecycle()
    val todayRounds by viewModel.todayRounds.collectAsStateWithLifecycle()
    val allOrders by viewModel.allOrders.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val reviewedPatientIds = remember(todayRounds) {
        todayRounds.map { it.patientId }.toSet()
    }

    val activePatients = remember(allPatients) { allPatients.filter { !it.isDischarged } }
    val dischargedPatients = remember(allPatients) { allPatients.filter { it.isDischarged } }
    val admittedTodayPatients = remember(allPatients, todayStr) { allPatients.filter { it.admissionDate == todayStr } }

    val currentList = when (selectedTabIndex) {
        0 -> activePatients
        1 -> dischargedPatients
        2 -> admittedTodayPatients
        else -> allPatients
    }

    val filteredList = remember(currentList, searchQuery) {
        if (searchQuery.isBlank()) {
            currentList
        } else {
            val q = searchQuery.lowercase(Locale.US)
            currentList.filter {
                it.name.lowercase(Locale.US).contains(q) ||
                        it.uhid.lowercase(Locale.US).contains(q) ||
                        it.ward.lowercase(Locale.US).contains(q) ||
                        it.bedNumber.lowercase(Locale.US).contains(q) ||
                        it.primaryDiagnosis.lowercase(Locale.US).contains(q) ||
                        it.department.lowercase(Locale.US).contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            WardRoundTopBar(
                title = "Patient Register",
                subtitle = "Inpatient Database & Archives",
                showBackButton = false
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddPatient,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Patient", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("patient_list_add_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, UHID, ward, bed, diagnosis...") },
                    modifier = Modifier
                        .testTag("patient_search_input")
                        .fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // Tabs
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
                listOf(
                    "Active Inpatients (${activePatients.size})",
                    "Discharged (${dischargedPatients.size})",
                    "Admitted Today (${admittedTodayPatients.size})"
                ).forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTabIndex == idx,
                        onClick = { selectedTabIndex = idx },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == idx) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            // Patient List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = PeriwinkleBlue,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No matching patient records",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = DeepRoyalNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try adjusting your search query or registering a new patient.",
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
                    items(filteredList, key = { it.id }) { patient ->
                        val isReviewed = patient.id in reviewedPatientIds
                        val pendingCount = remember(allOrders, patient.id) {
                            allOrders.count {
                                it.patientId == patient.id &&
                                        it.currentStatus !in listOf(InvestigationStatus.RESULT_REVIEWED, InvestigationStatus.COMPLETED, InvestigationStatus.CANCELLED)
                            }
                        }

                        PatientCard(
                            patient = patient,
                            isReviewedToday = isReviewed,
                            pendingInvestigationCount = pendingCount,
                            onEditClick = { onNavigateToEditPatient(patient.id) },
                            onClick = { onNavigateToPatient(patient.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }
}
