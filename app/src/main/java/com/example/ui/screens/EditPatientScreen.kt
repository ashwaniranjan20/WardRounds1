package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PatientEntity
import com.example.ui.components.WardRoundTopBar
import com.example.ui.theme.StatusAlert
import com.example.ui.theme.StatusPending
import com.example.ui.viewmodel.WardRoundViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPatientScreen(
    patientId: String,
    viewModel: WardRoundViewModel,
    onNavigateBack: () -> Unit,
    onPatientUpdated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val patientState by viewModel.getPatient(patientId).collectAsStateWithLifecycle(initialValue = null)
    val patient = patientState

    if (patient == null) {
        Scaffold(
            topBar = {
                WardRoundTopBar(
                    title = "Edit Patient Details",
                    subtitle = "Loading patient chart...",
                    showBackButton = true,
                    onBackClick = onNavigateBack
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    EditPatientForm(
        patient = patient,
        checkDuplicateUhid = { candidate ->
            viewModel.findDuplicateUhidOwner(candidate, excludePatientId = patient.id)
        },
        onSavePatient = { updated, onSuccess, onError ->
            viewModel.saveEditedPatient(updated, onSuccess = onSuccess, onError = onError)
        },
        onNavigateBack = onNavigateBack,
        onPatientUpdated = onPatientUpdated,
        modifier = modifier
    )
}

@Composable
internal fun EditPatientForm(
    patient: PatientEntity,
    checkDuplicateUhid: suspend (String) -> String?,
    onSavePatient: (PatientEntity, () -> Unit, (String) -> Unit) -> Unit,
    onNavigateBack: () -> Unit,
    onPatientUpdated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var name by remember(patient.id) { mutableStateOf(patient.name) }
    var uhid by remember(patient.id) { mutableStateOf(patient.uhid) }
    var ageText by remember(patient.id) { mutableStateOf(patient.age?.toString().orEmpty()) }
    var sex by remember(patient.id) { mutableStateOf(patient.sex.orEmpty()) }
    var contactInfo by remember(patient.id) { mutableStateOf(patient.contactInfo) }

    var ward by remember(patient.id) { mutableStateOf(patient.ward) }
    var bedNumber by remember(patient.id) { mutableStateOf(patient.bedNumber) }
    var admissionNumber by remember(patient.id) { mutableStateOf(patient.admissionNumber) }
    var department by remember(patient.id) { mutableStateOf(patient.department) }
    var consultant by remember(patient.id) { mutableStateOf(patient.consultant) }

    var primaryDiagnosis by remember(patient.id) { mutableStateOf(patient.primaryDiagnosis) }
    var provisionalDiagnosis by remember(patient.id) { mutableStateOf(patient.provisionalDiagnosis) }

    var drugAllergies by remember(patient.id) { mutableStateOf(patient.allergies) }
    var comorbidities by remember(patient.id) { mutableStateOf(patient.comorbidities) }
    var pastMedicalHistory by remember(patient.id) { mutableStateOf(patient.pastMedicalHistory) }
    var pastSurgicalHistory by remember(patient.id) { mutableStateOf(patient.pastSurgicalHistory) }

    var duplicateUhidOwner by remember(patient.id) { mutableStateOf<String?>(null) }
    var validationError by remember(patient.id) { mutableStateOf<String?>(null) }
    var isSaving by remember(patient.id) { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = remember(
        patient,
        name,
        uhid,
        ageText,
        sex,
        contactInfo,
        ward,
        bedNumber,
        admissionNumber,
        department,
        consultant,
        primaryDiagnosis,
        provisionalDiagnosis,
        drugAllergies,
        comorbidities,
        pastMedicalHistory,
        pastSurgicalHistory
    ) {
        name != patient.name ||
            uhid != patient.uhid ||
            ageText != (patient.age?.toString().orEmpty()) ||
            sex != patient.sex.orEmpty() ||
            contactInfo != patient.contactInfo ||
            ward != patient.ward ||
            bedNumber != patient.bedNumber ||
            admissionNumber != patient.admissionNumber ||
            department != patient.department ||
            consultant != patient.consultant ||
            primaryDiagnosis != patient.primaryDiagnosis ||
            provisionalDiagnosis != patient.provisionalDiagnosis ||
            drugAllergies != patient.allergies ||
            comorbidities != patient.comorbidities ||
            pastMedicalHistory != patient.pastMedicalHistory ||
            pastSurgicalHistory != patient.pastSurgicalHistory
    }

    LaunchedEffect(patient.id, uhid) {
        val trimmed = uhid.trim()
        duplicateUhidOwner = if (trimmed.isNotBlank()) {
            checkDuplicateUhid(trimmed)
        } else {
            null
        }
    }

    fun requestBack() {
        if (isSaving) return
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = hasUnsavedChanges && !isSaving) {
        showDiscardDialog = true
    }

    Scaffold(
        topBar = {
            WardRoundTopBar(
                title = "Edit Patient Details",
                subtitle = "UHID: ${patient.uhid} • Adm: ${patient.admissionDate.ifBlank { "Active" }}",
                showBackButton = true,
                onBackClick = { requestBack() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = duplicateUhidOwner != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_duplicate_uhid_warning")
                            .clip(RoundedCornerShape(12.dp))
                            .background(StatusPending.copy(alpha = 0.18f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Duplicate UHID warning",
                                tint = StatusPending,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Warning: Another patient (${duplicateUhidOwner.orEmpty()}) already uses UHID '${uhid.trim()}'. You may still save if intentional.",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = validationError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_validation_error")
                            .clip(RoundedCornerShape(12.dp))
                            .background(StatusAlert.copy(alpha = 0.14f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Validation error",
                                tint = StatusAlert,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = validationError.orEmpty(),
                                color = StatusAlert,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 1. Demographics & Identification
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "1. Identification & Demographics",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                if (validationError != null) validationError = null
                            },
                            label = { Text("Full Patient Name *") },
                            modifier = Modifier
                                .testTag("edit_patient_name_input")
                                .fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = uhid,
                            onValueChange = {
                                uhid = it
                                if (validationError != null) validationError = null
                            },
                            label = { Text("UHID (Unique Hospital ID) *") },
                            modifier = Modifier
                                .testTag("edit_patient_uhid_input")
                                .fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (uhid.isNotBlank()) {
                                    if (duplicateUhidOwner != null) {
                                        Icon(
                                            imageVector = Icons.Default.WarningAmber,
                                            contentDescription = "Duplicate UHID",
                                            tint = StatusPending
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Unique UHID",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = ageText,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.all { it.isDigit() }) {
                                        ageText = input
                                    }
                                },
                                label = { Text("Age (years)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .testTag("edit_patient_age_input")
                                    .weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = sex,
                                onValueChange = { sex = it },
                                label = { Text("Sex") },
                                placeholder = { Text("Male / Female") },
                                modifier = Modifier
                                    .testTag("edit_patient_sex_input")
                                    .weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = contactInfo,
                            onValueChange = { contactInfo = it },
                            label = { Text("Contact / Emergency Phone") },
                            placeholder = { Text("e.g. +91-9876543210 (Spouse)") },
                            modifier = Modifier
                                .testTag("edit_patient_contact_input")
                                .fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // 2. Ward Location & Admission
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "2. Ward Transfer & Admission",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = ward,
                                onValueChange = { ward = it },
                                label = { Text("Ward Name") },
                                modifier = Modifier
                                    .testTag("edit_patient_ward_input")
                                    .weight(1.5f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = bedNumber,
                                onValueChange = { bedNumber = it },
                                label = { Text("Bed No.") },
                                modifier = Modifier
                                    .testTag("edit_patient_bed_input")
                                    .weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = admissionNumber,
                            onValueChange = { admissionNumber = it },
                            label = { Text("Admission Number") },
                            modifier = Modifier
                                .testTag("edit_patient_admission_no_input")
                                .fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = department,
                            onValueChange = { department = it },
                            label = { Text("Clinical Department") },
                            modifier = Modifier
                                .testTag("edit_patient_department_input")
                                .fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = consultant,
                            onValueChange = { consultant = it },
                            label = { Text("Attending Consultant") },
                            modifier = Modifier
                                .testTag("edit_patient_consultant_input")
                                .fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // 3. Clinical Diagnosis (bounded height)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "3. Clinical Diagnosis",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = primaryDiagnosis,
                            onValueChange = { primaryDiagnosis = it },
                            label = { Text("Primary Working Diagnosis") },
                            modifier = Modifier
                                .testTag("edit_patient_diagnosis_input")
                                .fillMaxWidth()
                                .heightIn(max = 112.dp),
                            singleLine = false,
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = provisionalDiagnosis,
                            onValueChange = { provisionalDiagnosis = it },
                            label = { Text("Provisional / Differential Diagnosis") },
                            modifier = Modifier
                                .testTag("edit_patient_provisional_diagnosis_input")
                                .fillMaxWidth()
                                .heightIn(max = 112.dp),
                            singleLine = false,
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // 4. Clinical Background & Safety
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "4. Clinical Background & Safety",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = drugAllergies,
                            onValueChange = { drugAllergies = it },
                            label = { Text("Drug Allergies") },
                            placeholder = { Text("NKDA or specific medications") },
                            modifier = Modifier
                                .testTag("edit_patient_allergies_input")
                                .fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = comorbidities,
                            onValueChange = { comorbidities = it },
                            label = { Text("Comorbidities") },
                            placeholder = { Text("e.g. T2DM, HTN, CAD") },
                            modifier = Modifier
                                .testTag("edit_patient_comorbidities_input")
                                .fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = pastMedicalHistory,
                            onValueChange = { pastMedicalHistory = it },
                            label = { Text("Past Medical History") },
                            modifier = Modifier
                                .testTag("edit_patient_pmh_input")
                                .fillMaxWidth()
                                .heightIn(max = 120.dp),
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = pastSurgicalHistory,
                            onValueChange = { pastSurgicalHistory = it },
                            label = { Text("Past Surgical History") },
                            modifier = Modifier
                                .testTag("edit_patient_psh_input")
                                .fillMaxWidth()
                                .heightIn(max = 120.dp),
                            singleLine = false,
                            minLines = 1,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Bottom Cancel / Save Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { requestBack() },
                        enabled = !isSaving,
                        modifier = Modifier
                            .testTag("edit_patient_cancel_button")
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            if (name.isBlank() || uhid.isBlank()) {
                                validationError = "Patient Name and UHID are mandatory."
                                return@Button
                            }
                            validationError = null
                            isSaving = true

                            val updatedPatient = patient.copy(
                                name = name.trim(),
                                uhid = uhid.trim(),
                                age = ageText.trim().toIntOrNull(),
                                sex = sex.trim().ifBlank { null },
                                contactInfo = contactInfo.trim(),
                                ward = ward.trim().ifBlank { "General Ward" },
                                bedNumber = bedNumber.trim().ifBlank { "1" },
                                admissionNumber = admissionNumber.trim(),
                                department = department.trim().ifBlank { "Internal Medicine" },
                                consultant = consultant.trim(),
                                primaryDiagnosis = primaryDiagnosis.trim().ifBlank { "Inpatient Assessment" },
                                provisionalDiagnosis = provisionalDiagnosis.trim(),
                                allergies = drugAllergies.trim().ifBlank { "NKDA" },
                                comorbidities = comorbidities.trim(),
                                pastMedicalHistory = pastMedicalHistory.trim(),
                                pastSurgicalHistory = pastSurgicalHistory.trim()
                            )

                            onSavePatient(
                                updatedPatient,
                                {
                                    isSaving = false
                                    Toast.makeText(context, "Patient details updated", Toast.LENGTH_SHORT).show()
                                    onPatientUpdated()
                                },
                                { err ->
                                    isSaving = false
                                    validationError = err
                                }
                            )
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .testTag("edit_patient_save_button")
                            .weight(1.5f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("You have modified this patient's clinical details. Leaving now will discard your unsaved edits.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}
