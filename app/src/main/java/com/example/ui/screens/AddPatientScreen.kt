package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.example.domain.ocr.OcrDocumentType
import com.example.domain.ocr.OcrExtractionResult
import com.example.ui.components.OcrScanActionChip
import com.example.ui.ocr.OcrImageCaptureDialog
import com.example.ui.ocr.OcrReviewDraftScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PatientEntity
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPatientScreen(
    viewModel: WardRoundViewModel,
    onNavigateBack: () -> Unit,
    onPatientRegistered: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Required fields
    var name by remember { mutableStateOf("") }
    var uhid by remember { mutableStateOf("") }

    // Quick add toggle
    var isQuickAddMode by remember { mutableStateOf(false) }

    // Optional fields
    var admissionNumber by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("Male") }
    var ward by remember { mutableStateOf("Medicine Ward") }
    var bedNumber by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("Internal Medicine") }
    var consultant by remember { mutableStateOf("Dr. S. Mukherjee, MD") }
    var primaryDiagnosis by remember { mutableStateOf("") }
    var provisionalDiagnosis by remember { mutableStateOf("") }
    var comorbidities by remember { mutableStateOf("") }
    var drugAllergies by remember { mutableStateOf("NKDA") }
    var pastMedicalHistory by remember { mutableStateOf("") }
    var pastSurgicalHistory by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }

    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    var duplicateWarning by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingDuplicate by remember { mutableStateOf(false) }

    // OCR Scan states
    var showOcrCaptureDialog by remember { mutableStateOf(false) }
    var ocrReviewData by remember { mutableStateOf<Triple<Uri, Int, OcrExtractionResult>?>(null) }

    // Check duplicate UHID on change
    LaunchedEffect(uhid) {
        if (uhid.isNotBlank()) {
            isCheckingDuplicate = true
            val exists = viewModel.isUhidRegistered(uhid.trim())
            duplicateWarning = if (exists) "Warning: A patient with UHID '${uhid.trim()}' is already registered in the system!" else null
            isCheckingDuplicate = false
        } else {
            duplicateWarning = null
        }
    }

    if (ocrReviewData != null) {
        val (uri, rot, result) = ocrReviewData!!
        OcrReviewDraftScreen(
            imageUri = uri,
            rotationDegrees = rot,
            extractionResult = result,
            onApplyFields = { applied ->
                applied["name"]?.let { name = it }
                applied["uhid"]?.let { uhid = it }
                applied["age"]?.let { ageText = it }
                applied["sex"]?.let { sex = it }
                applied["ward"]?.let { ward = it }
                applied["bed"]?.let { bedNumber = it }
                applied["diagnosis"]?.let { primaryDiagnosis = it }
                ocrReviewData = null
            },
            onDiscard = { ocrReviewData = null }
        )
        return
    }

    if (showOcrCaptureDialog) {
        OcrImageCaptureDialog(
            targetDocType = OcrDocumentType.PATIENT_REGISTRATION,
            existingFormValues = mapOf(
                "name" to name,
                "uhid" to uhid,
                "age" to ageText,
                "sex" to sex,
                "ward" to ward,
                "bed" to bedNumber,
                "diagnosis" to primaryDiagnosis
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
                title = "New Patient Registration",
                subtitle = "Inpatient Admission Record",
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
            // OCR Scan Action Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scan Admission Form or ID",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                OcrScanActionChip(
                    onClick = { showOcrCaptureDialog = true },
                    label = "Scan Document",
                    testTag = "btn_ocr_scan_patient_admission"
                )
            }

            // Quick-Add Mode Switch Card
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Quick Registration Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = DeepRoyalNavy
                        )
                        Text(
                            text = "Rapid ward-round intake: only Name and UHID required initially",
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }

                    Switch(
                        checked = isQuickAddMode,
                        onCheckedChange = { isQuickAddMode = it },
                        modifier = Modifier.testTag("quick_add_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = DeepRoyalNavy
                        )
                    )
                }
            }

            // Error & Duplicate Alert Banners
            AnimatedVisibility(visible = duplicateWarning != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StatusAlert.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = StatusAlert,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = duplicateWarning ?: "",
                            color = StatusAlert,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(visible = errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StatusAlert.copy(alpha = 0.15f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = StatusAlert,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = StatusAlert,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Core Patient Identification Card (Mandatory Fields)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Patient Identification (Mandatory)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepRoyalNavy
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Patient Name *") },
                        placeholder = { Text("e.g. Rahul Sharma") },
                        modifier = Modifier
                            .testTag("patient_name_input")
                            .fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = PureWhite,
                            unfocusedContainerColor = PureWhite,
                            focusedIndicatorColor = DeepRoyalNavy,
                            unfocusedIndicatorColor = CardBorder
                        )
                    )

                    OutlinedTextField(
                        value = uhid,
                        onValueChange = { uhid = it },
                        label = { Text("UHID (Unique Hospital ID) *") },
                        placeholder = { Text("e.g. DEMO-00125") },
                        modifier = Modifier
                            .testTag("patient_uhid_input")
                            .fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (uhid.isNotBlank()) {
                                if (duplicateWarning != null) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = "Duplicate", tint = StatusAlert)
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = "Valid", tint = DeepRoyalNavy)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = PureWhite,
                            unfocusedContainerColor = PureWhite,
                            focusedIndicatorColor = if (duplicateWarning != null) StatusAlert else DeepRoyalNavy,
                            unfocusedIndicatorColor = if (duplicateWarning != null) StatusAlert else CardBorder
                        )
                    )
                }
            }

            // Ward & Admission Details Card
            if (!isQuickAddMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "2. Ward Location & Admission",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepRoyalNavy
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
                                    .testTag("patient_ward_input")
                                    .weight(1.5f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = bedNumber,
                                onValueChange = { bedNumber = it },
                                label = { Text("Bed No.") },
                                placeholder = { Text("12") },
                                modifier = Modifier
                                    .testTag("patient_bed_input")
                                    .weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = ageText,
                                onValueChange = { ageText = it },
                                label = { Text("Age (years)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .testTag("patient_age_input")
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
                                    .testTag("patient_sex_input")
                                    .weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = primaryDiagnosis,
                            onValueChange = { primaryDiagnosis = it },
                            label = { Text("Primary Working Diagnosis") },
                            placeholder = { Text("e.g. Community Acquired Pneumonia") },
                            modifier = Modifier
                                .testTag("patient_diagnosis_input")
                                .fillMaxWidth()
                                .heightIn(max = 112.dp),
                            singleLine = false,
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = consultant,
                            onValueChange = { consultant = it },
                            label = { Text("Attending Consultant") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Medical History & Allergies Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "3. Clinical Background & Safety",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepRoyalNavy
                        )

                        OutlinedTextField(
                            value = drugAllergies,
                            onValueChange = { drugAllergies = it },
                            label = { Text("Drug Allergies (Important)") },
                            placeholder = { Text("NKDA or specific drugs") },
                            modifier = Modifier
                                .testTag("patient_allergies_input")
                                .fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = comorbidities,
                            onValueChange = { comorbidities = it },
                            label = { Text("Comorbidities") },
                            placeholder = { Text("e.g. T2DM, HTN, CAD") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = pastMedicalHistory,
                            onValueChange = { pastMedicalHistory = it },
                            label = { Text("Relevant Past Medical History") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Save Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .testTag("cancel_patient_button")
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Text("Cancel", color = SecondaryText, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (name.isBlank() || uhid.isBlank()) {
                            errorMessage = "Patient Name and UHID are mandatory."
                            return@Button
                        }
                        if (duplicateWarning != null) {
                            errorMessage = "Please enter a unique UHID. Duplicate registration prevented."
                            return@Button
                        }

                        val parsedAge = ageText.toIntOrNull()
                        val newPatient = PatientEntity(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            uhid = uhid.trim(),
                            age = parsedAge,
                            sex = sex.trim(),
                            admissionNumber = admissionNumber.ifBlank { "ADM-${System.currentTimeMillis() % 100000}" },
                            ward = ward.ifBlank { "General Ward" },
                            bedNumber = bedNumber.ifBlank { "1" },
                            department = department,
                            consultant = consultant,
                            admissionDate = todayDate,
                            primaryDiagnosis = primaryDiagnosis.ifBlank { "Inpatient Assessment" },
                            provisionalDiagnosis = provisionalDiagnosis,
                            comorbidities = comorbidities,
                            allergies = drugAllergies,
                            pastMedicalHistory = pastMedicalHistory,
                            pastSurgicalHistory = pastSurgicalHistory,
                            contactInfo = contactInfo,
                            syncStatus = SyncStatus.SAVED_LOCALLY
                        )

                        viewModel.registerPatient(
                            patient = newPatient,
                            onSuccess = { newId ->
                                onPatientRegistered(newId)
                            },
                            onError = { err ->
                                errorMessage = err
                            }
                        )
                    },
                    modifier = Modifier
                        .testTag("submit_patient_button")
                        .weight(1.5f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepRoyalNavy,
                        contentColor = PureWhite
                    )
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Patient", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
