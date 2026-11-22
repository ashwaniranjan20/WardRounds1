package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    val role: UserRole = UserRole.DOCTOR,
    val department: String = "General Medicine",
    val registrationNumber: String = "MCI-74892"
)

@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val uhid: String, // Visible lookup ID (e.g. DEMO-00125)
    val name: String,
    val age: Int? = null,
    val sex: String? = null,
    val admissionNumber: String = "",
    val ward: String = "",
    val bedNumber: String = "",
    val department: String = "Internal Medicine",
    val consultant: String = "Dr. S. Mukherjee, MD",
    val admissionDate: String = "", // YYYY-MM-DD
    val admissionTime: String = "09:00",
    val primaryDiagnosis: String = "",
    val provisionalDiagnosis: String = "",
    val comorbidities: String = "",
    val allergies: String = "NKDA",
    val pastMedicalHistory: String = "",
    val pastSurgicalHistory: String = "",
    val contactInfo: String = "",
    val isDischarged: Boolean = false,
    val dischargeDate: String? = null,
    val syncStatus: SyncStatus = SyncStatus.SAVED_LOCALLY,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "admissions")
data class AdmissionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val uhid: String,
    val admissionNumber: String,
    val admissionDate: String,
    val dischargeDate: String? = null,
    val ward: String,
    val bedNumber: String,
    val admissionDiagnosis: String,
    val finalDiagnosis: String = "",
    val status: AdmissionStatus = AdmissionStatus.ACTIVE,
    val syncStatus: SyncStatus = SyncStatus.SAVED_LOCALLY,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "round_entries")
data class RoundEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val admissionId: String,
    val roundDate: String, // YYYY-MM-DD
    val roundTime: String, // HH:mm
    val doctorName: String,
    val ward: String,
    val bedNumber: String,
    val hospitalDay: Int,
    val roundType: RoundType = RoundType.MORNING,
    // Section B - Clinical Update
    val overnightEvents: String = "",
    val currentComplaints: String = "",
    val symptomProgression: String = "",
    val feverEvents: String = "",
    val currentCondition: String = "",
    val responseToTreatment: String = "",
    val changesSincePrevious: String = "",
    // Section D - Examination
    val generalExam: String = "",
    val respiratoryExam: String = "",
    val cvsExam: String = "",
    val abdomenExam: String = "",
    val cnsExam: String = "",
    val otherExam: String = "",
    // Section E - Assessment
    val currentDiagnosis: String = "",
    val differentialDiagnosis: String = "",
    val clinicalAssessment: String = "",
    val problemsIdentified: String = "",
    // Section F - Plan
    val treatmentChanges: String = "",
    val medicationPlan: String = "",
    val procedures: String = "",
    val monitoringPlan: String = "",
    val dietFluidAdvice: String = "",
    val respiratorySupportPlan: String = "",
    val referrals: String = "",
    val investigationsOrdered: String = "",
    val expectedDischargeDate: String = "",
    // Section G - Daily Summary
    val dailySummary: String = "",
    val isDraft: Boolean = false,
    val copiedFromPreviousRound: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SAVED_LOCALLY,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vital_observations")
data class VitalObservationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val admissionId: String,
    val roundId: String? = null,
    val measurementDate: String, // YYYY-MM-DD
    val measurementTime: String, // HH:mm
    val bpSystolic: Int? = null,
    val bpDiastolic: Int? = null,
    val pulseRate: Int? = null, // bpm
    val respiratoryRate: Int? = null, // breaths/min
    val temperatureF: Double? = null, // F
    val spO2: Int? = null, // %
    val oxygenDeliveryMethod: String = "Room Air", // Nasal Cannula, Mask, etc.
    val oxygenFlowRateLpm: Double? = null,
    val bloodGlucoseMgDl: Int? = null,
    val painScore: Int? = null, // 0-10
    val otherObservations: String = "",
    val syncStatus: SyncStatus = SyncStatus.SAVED_LOCALLY,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "clinical_notes")
data class ClinicalNoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val admissionId: String,
    val date: String, // YYYY-MM-DD
    val time: String, // HH:mm
    val author: String,
    val noteType: String = "Daily Progress Note", // Progress Note, Consult, History, Discharge Advice
    val hospitalDay: Int = 1,
    val title: String = "",
    val content: String,
    val isDraft: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SAVED_LOCALLY,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "investigation_orders")
data class InvestigationOrderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val admissionId: String,
    val roundId: String? = null,
    val orderingClinician: String,
    val orderDate: String, // YYYY-MM-DD
    val orderTime: String, // HH:mm
    val investigationName: String, // e.g. "Complete Blood Count (CBC)"
    val category: String, // Hematology, Biochemistry, etc.
    val priority: InvestigationPriority = InvestigationPriority.ROUTINE,
    val currentStatus: InvestigationStatus = InvestigationStatus.ORDERED,
    val specimenInfo: String = "Venous Blood (EDTA)",
    val turnaroundHours: Int = 4,
    val clinicalIndication: String = "",
    val cancellationReason: String? = null,
    val syncStatus: SyncStatus = SyncStatus.SAVED_LOCALLY,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "investigation_events")
data class InvestigationEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val orderId: String,
    val eventStatus: InvestigationStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val eventDateTimeFormatted: String, // e.g. "2026-10-24 10:15"
    val responsiblePerson: String,
    val comments: String = ""
)

@Entity(tableName = "investigation_results")
data class InvestigationResultEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val admissionId: String,
    val orderId: String? = null,
    val investigationName: String, // e.g. "CBC", "KFT", "CRP"
    val category: String, // Hematology, Biochemistry, etc.
    val parameterName: String, // e.g. "Hemoglobin", "WBC", "Platelets", "Creatinine", "CRP"
    val numericValue: Double? = null,
    val originalTextValue: String, // e.g. "10.2", "Culture Positive: Streptococcus pneumoniae"
    val unit: String = "", // e.g. "g/dL", "x10^3/uL", "mg/dL", "mg/L"
    val refRangeLow: Double? = null,
    val refRangeHigh: Double? = null,
    val refRangeText: String = "", // e.g. "13.0 - 17.0 g/dL"
    val resultStatus: ResultStatus = ResultStatus.FINAL,
    val measurementDate: String, // YYYY-MM-DD
    val measurementTime: String = "08:00",
    val reportAvailabilityTime: String = "",
    val reviewDate: String? = null,
    val reviewTime: String? = null,
    val reviewingClinician: String? = null,
    val isReviewed: Boolean = false,
    val isAbnormal: Boolean = false,
    val isCritical: Boolean = false,
    val dataSource: String = "Central Laboratory (Automated)",
    val clinicalComments: String = "",
    val correctionHistory: String = "",
    val linkedNoteId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.SAVED_LOCALLY,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "discharge_summaries")
data class DischargeSummaryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val admissionId: String,
    val admissionDiagnosis: String = "",
    val finalDiagnosis: String = "",
    val relevantClinicalCourse: String = "",
    val importantExamFindings: String = "",
    val importantInvestigationResults: String = "",
    val proceduresPerformed: String = "",
    val conditionAtDischarge: String = "Stable, Afebrile, Hemodynamically Normal",
    val treatmentReceived: String = "",
    val dischargeMedications: String = "", // Medication, dose, route, freq, duration
    val dietAdvice: String = "Normal balanced diet, adequate hydration",
    val activityAdvice: String = "Gradual mobilization as tolerated, avoid strenuous exertion for 2 weeks",
    val woundOrDeviceCare: String = "",
    val warningSigns: String = "Recurrent high grade fever, breathlessness, chest pain, persistent vomiting",
    val urgentCareInstructions: String = "Report immediately to Hospital Emergency Dept (Triage Zone 1)",
    val followUpDepartment: String = "Internal Medicine OPD",
    val followUpDate: String = "",
    val followUpInvestigations: String = "Repeat CBC and CRP at 1-week follow-up",
    val pendingInvestigationReports: String = "Blood culture 48h incubation report pending",
    val responsibleClinician: String = "Dr. S. Mukherjee, MD (Consultant)",
    val additionalInstructions: String = "Maintain medication compliance. Complete antibiotic course.",
    val dischargeStatus: DischargeStatus = DischargeStatus.UNDER_CONSIDERATION,
    val isFinalized: Boolean = false,
    val finalizedAt: String? = null,
    val syncStatus: SyncStatus = SyncStatus.SAVED_LOCALLY,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "follow_up_tasks")
data class FollowUpTaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val uhid: String,
    val taskType: String, // Outpatient follow-up, Repeat Lab, Specialist Consultation, etc.
    val description: String,
    val dueDate: String, // YYYY-MM-DD
    val dueTime: String = "10:00",
    val responsibleDepartmentOrDoctor: String = "Internal Medicine OPD",
    val linkedInvestigation: String? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val status: TaskStatus = TaskStatus.SCHEDULED,
    val completionDate: String? = null,
    val notes: String = "",
    val syncStatus: SyncStatus = SyncStatus.SAVED_LOCALLY,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val formattedDateTime: String,
    val entityType: String,
    val entityId: String,
    val action: String, // CREATED, UPDATED, FINALIZED, REVIEWED, SYNCED
    val performedBy: String,
    val details: String
)

@Entity(tableName = "handover_entries")
data class HandoverEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val uhid: String,
    val patientName: String,
    val ward: String,
    val bedNumber: String,
    val shiftDate: String, // YYYY-MM-DD
    val shiftType: ShiftType = ShiftType.EVENING_TO_NIGHT,
    val illnessSeverity: IllnessSeverity = IllnessSeverity.STABLE,
    // SBAR + I-PASS structured sections
    val situation: String = "",
    val background: String = "",
    val assessment: String = "",
    val recommendationAndActions: String = "",
    val contingencyPlan: String = "",
    val pendingCriticalItems: String = "",
    // Clinician Sign-Off & Provenance
    val outgoingClinician: String = "",
    val outgoingTime: String = "",
    val incomingClinician: String? = null,
    val acknowledgedAt: String? = null,
    val synthesisConfirmed: Boolean = false,
    val status: HandoverStatus = HandoverStatus.HANDED_OVER,
    val syncStatus: SyncStatus = SyncStatus.SAVED_LOCALLY,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)
