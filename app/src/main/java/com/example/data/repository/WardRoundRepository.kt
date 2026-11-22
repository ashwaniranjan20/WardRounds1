package com.example.data.repository

import com.example.data.database.WardRoundDao
import com.example.data.model.AdmissionEntity
import com.example.data.model.AdmissionStatus
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClinicalNoteEntity
import com.example.data.model.DischargeStatus
import com.example.data.model.DischargeSummaryEntity
import com.example.data.model.FollowUpTaskEntity
import com.example.data.model.HandoverEntryEntity
import com.example.data.model.HandoverStatus
import com.example.data.model.IllnessSeverity
import com.example.data.model.InvestigationEventEntity
import com.example.data.model.InvestigationOrderEntity
import com.example.data.model.InvestigationPriority
import com.example.data.model.InvestigationResultEntity
import com.example.data.model.InvestigationStatus
import com.example.data.model.ParameterTrendSummary
import com.example.data.model.PatientEntity
import com.example.data.model.ResultStatus
import com.example.data.model.RoundEntryEntity
import com.example.data.model.RoundType
import com.example.data.model.ShiftType
import com.example.data.model.SyncStatus
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.TrendEngine
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.data.model.VitalObservationEntity
import com.example.data.remote.FirestoreClinicalDoc
import com.example.data.remote.FirestoreClinicalSync
import com.example.data.remote.FirestorePatientRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class WardRoundRepository(
    private val dao: WardRoundDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val firestoreSync: FirestoreClinicalSync? = null
) {
    private val syncMutex = Mutex()
    private var activeSyncJob: Job? = null

    // Current Active User Session
    private val _currentUser = MutableStateFlow(
        UserEntity(
            id = "doc-01",
            name = "Dr. Ananya Ray",
            email = "dr.ray@hospital.org",
            role = UserRole.DOCTOR,
            department = "Internal Medicine"
        )
    )
    val currentUser: StateFlow<UserEntity> = _currentUser.asStateFlow()

    // Multi-device synchronization state & offline mode controls
    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _syncMessage = MutableStateFlow("Room & Cloud Firestore ready")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    init {
        scope.launch {
            // Check if database is empty; if so, populate synthetic demonstration dataset
            val existing = dao.getAllPatients().firstOrNull()
            if (existing.isNullOrEmpty()) {
                seedSyntheticData()
            }
        }
    }

    val isCloudSyncEnabled: Boolean
        get() = firestoreSync != null

    fun observeCloudPatients(): Flow<List<FirestorePatientRecord>> {
        val sync = firestoreSync ?: return flowOf(emptyList())
        val uid = sync.currentUserId ?: return flowOf(emptyList())
        return sync.observePatients(uid)
    }

    fun observeCloudDocs(): Flow<List<FirestoreClinicalDoc>> {
        val sync = firestoreSync ?: return flowOf(emptyList())
        val uid = sync.currentUserId ?: return flowOf(emptyList())
        return sync.observeClinicalDocs(uid)
    }

    fun activateLocalSession(
        sessionId: String,
        displayName: String,
        identifier: String,
        role: UserRole,
        department: String,
        isGuest: Boolean
    ) {
        val updated = UserEntity(
            id = sessionId,
            name = displayName,
            email = identifier,
            role = role,
            department = department
        )
        _currentUser.value = updated
        _isOfflineMode.value = true
        _syncStatus.value = SyncStatus.SAVED_LOCALLY
        _syncMessage.value = if (isGuest) {
            "Guest Mode: saved locally in Room (cloud sync disabled)"
        } else {
            "Local Account: saved locally in Room (sign in with Google to sync cloud)"
        }
        scope.launch {
            dao.insertUser(updated)
        }
    }

    fun syncFirebaseClinician(uid: String, displayName: String?, email: String?) {
        val existing = _currentUser.value
        val resolvedName = displayName?.takeIf { it.isNotBlank() } ?: existing.name
        val resolvedEmail = email?.takeIf { it.isNotBlank() } ?: existing.email
        val updated = existing.copy(
            id = uid,
            name = resolvedName,
            email = resolvedEmail
        )
        _currentUser.value = updated
        scope.launch {
            dao.insertUser(updated)
            if (!_isOfflineMode.value) {
                firestoreSync?.upsertClinicianProfile(
                    displayName = updated.name,
                    role = updated.role,
                    department = updated.department
                )
            }
        }
    }

    fun clearCloudSession() {
        activeSyncJob?.cancel()
        activeSyncJob = null
    }

    fun setDoctorUser(name: String, role: UserRole, department: String) {
        val updated = UserEntity(
            id = firestoreSync?.currentUserId ?: _currentUser.value.id,
            name = name,
            email = _currentUser.value.email.ifBlank {
                "${name.lowercase(Locale.ROOT).replace(" ", ".")}@hospital.org"
            },
            role = role,
            department = department
        )
        _currentUser.value = updated
        scope.launch {
            dao.insertUser(updated)
            if (!_isOfflineMode.value) {
                firestoreSync?.upsertClinicianProfile(
                    displayName = updated.name,
                    role = updated.role,
                    department = updated.department
                )
            }
        }
    }

    fun toggleOfflineMode(enabled: Boolean) {
        _isOfflineMode.value = enabled
        if (enabled) {
            _syncStatus.value = SyncStatus.SAVED_LOCALLY
            _syncMessage.value = "Offline mode active: changes will be stored locally in Room"
        } else {
            triggerSync()
        }
    }

    fun triggerSync() {
        activeSyncJob = scope.launch {
            syncMutex.withLock {
                if (_isOfflineMode.value) {
                    _syncStatus.value = SyncStatus.SAVED_LOCALLY
                    _syncMessage.value = "Cannot sync while Offline Mode is enabled"
                    return@withLock
                }

                val sync = firestoreSync
                if (sync == null || sync.currentUserId == null) {
                    _syncStatus.value = SyncStatus.SAVED_LOCALLY
                    _lastSyncTimestamp.value = System.currentTimeMillis()
                    _syncMessage.value = "Saved in local Room database"
                    return@withLock
                }

                _syncStatus.value = SyncStatus.SYNC_PENDING
                _syncMessage.value = "Synchronizing clinical records with Cloud Firestore..."

                try {
                    val patients = dao.getAllPatients().firstOrNull().orEmpty()
                    val rounds = dao.getAllRoundsOnce()
                    val notes = dao.getAllNotesOnce()
                    val discharges = dao.getAllDischargeSummariesOnce()
                    val handovers = dao.getAllHandovers().firstOrNull().orEmpty()
                    val user = _currentUser.value

                    val result = sync.syncAllFromRoom(
                        clinicianName = user.name,
                        clinicianRole = user.role,
                        clinicianDepartment = user.department,
                        patients = patients,
                        rounds = rounds,
                        notes = notes,
                        discharges = discharges,
                        handovers = handovers
                    )

                    result.onSuccess { count ->
                        _syncStatus.value = SyncStatus.SYNCED
                        _lastSyncTimestamp.value = System.currentTimeMillis()
                        _syncMessage.value = "Synced $count clinical documents across Room & Cloud Firestore"
                        logAudit("SYNC", "firestore", "SYNC_ALL", "Synchronized $count clinical records with Cloud Firestore")
                    }.onFailure { error ->
                        _syncStatus.value = SyncStatus.SAVED_LOCALLY
                        _syncMessage.value = "Saved locally in Room (${error.localizedMessage ?: "cloud retry pending"})"
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _syncStatus.value = SyncStatus.SAVED_LOCALLY
                    _syncMessage.value = "Saved locally in Room (${e.localizedMessage ?: "offline"})"
                }
            }
        }
    }

    // --- Patients ---
    fun getAllPatients(): Flow<List<PatientEntity>> = dao.getAllPatients()
    fun getActivePatients(): Flow<List<PatientEntity>> = dao.getActivePatients()
    fun getDischargedPatients(): Flow<List<PatientEntity>> = dao.getDischargedPatients()
    fun getPatientById(id: String): Flow<PatientEntity?> = dao.getPatientById(id)

    suspend fun getPatientByUhid(uhid: String): PatientEntity? = dao.getPatientByUhid(uhid)

    suspend fun getPatientByUhidExcluding(uhid: String, excludePatientId: String): PatientEntity? =
        dao.getPatientByUhidExcluding(uhid, excludePatientId)

    suspend fun registerPatient(patient: PatientEntity): String {
        val currentStatus = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        val patientToInsert = patient.copy(syncStatus = currentStatus)
        dao.insertPatient(patientToInsert)

        // Automatically create initial Admission record
        val admission = AdmissionEntity(
            patientId = patientToInsert.id,
            uhid = patientToInsert.uhid,
            admissionNumber = patientToInsert.admissionNumber.ifBlank { "ADM-${System.currentTimeMillis() % 100000}" },
            admissionDate = patientToInsert.admissionDate.ifBlank { todayString() },
            ward = patientToInsert.ward,
            bedNumber = patientToInsert.bedNumber,
            admissionDiagnosis = patientToInsert.primaryDiagnosis,
            status = AdmissionStatus.ACTIVE
        )
        dao.insertAdmission(admission)

        logAudit("PATIENT", patientToInsert.id, "REGISTERED", "Registered patient ${patient.name} (${patient.uhid})")
        if (!_isOfflineMode.value) triggerSync()
        return patientToInsert.id
    }

    suspend fun updatePatient(patient: PatientEntity) {
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        val updatedPatient = patient.copy(syncStatus = status, lastUpdated = System.currentTimeMillis())
        dao.updatePatient(updatedPatient)

        val activeAdmission = dao.getActiveAdmissionForPatient(updatedPatient.id)
        if (activeAdmission != null) {
            dao.updateAdmission(
                activeAdmission.copy(
                    uhid = updatedPatient.uhid,
                    admissionNumber = updatedPatient.admissionNumber.ifBlank { activeAdmission.admissionNumber },
                    ward = updatedPatient.ward,
                    bedNumber = updatedPatient.bedNumber,
                    admissionDiagnosis = updatedPatient.primaryDiagnosis,
                    syncStatus = status
                )
            )
        }

        logAudit("PATIENT", updatedPatient.id, "UPDATED", "Updated clinical profile for ${updatedPatient.name} (${updatedPatient.uhid})")
        if (!_isOfflineMode.value) triggerSync()
    }

    suspend fun dischargePatient(patientId: String, dischargeDate: String = todayString()) {
        val patient = dao.getPatientByIdOnce(patientId) ?: return
        val updated = patient.copy(
            isDischarged = true,
            dischargeDate = dischargeDate,
            syncStatus = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        )
        dao.updatePatient(updated)

        // Update active admission
        val activeAdmission = dao.getActiveAdmissionForPatient(patientId)
        if (activeAdmission != null) {
            dao.updateAdmission(activeAdmission.copy(dischargeDate = dischargeDate, status = AdmissionStatus.DISCHARGED))
        }

        logAudit("PATIENT", patientId, "DISCHARGED", "Discharged patient ${patient.name} (${patient.uhid}) on $dischargeDate")
        if (!_isOfflineMode.value) triggerSync()
    }

    // --- Daily Rounds ---
    fun getRoundsForPatient(patientId: String): Flow<List<RoundEntryEntity>> = dao.getRoundsForPatient(patientId)
    fun getRoundById(id: String): Flow<RoundEntryEntity?> = dao.getRoundById(id)
    suspend fun getLatestRoundForPatient(patientId: String): RoundEntryEntity? = dao.getLatestRoundForPatient(patientId)
    fun getRoundsForDate(date: String): Flow<List<RoundEntryEntity>> = dao.getRoundsForDate(date)

    suspend fun saveRoundEntry(
        round: RoundEntryEntity,
        vitals: VitalObservationEntity? = null
    ): String {
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        val roundToSave = round.copy(syncStatus = status)
        dao.insertRound(roundToSave)

        if (vitals != null) {
            val vitalsToSave = vitals.copy(
                roundId = roundToSave.id,
                patientId = roundToSave.patientId,
                admissionId = roundToSave.admissionId,
                syncStatus = status
            )
            dao.insertVital(vitalsToSave)
        }

        logAudit(
            "ROUND",
            roundToSave.id,
            if (roundToSave.isDraft) "SAVED_DRAFT" else "FINALIZED",
            "Round recorded by ${roundToSave.doctorName} for patient ID ${roundToSave.patientId}"
        )
        if (!_isOfflineMode.value) triggerSync()
        return roundToSave.id
    }

    // --- Vitals ---
    fun getVitalsForPatient(patientId: String): Flow<List<VitalObservationEntity>> = dao.getVitalsForPatient(patientId)
    fun getLatestVitalForPatient(patientId: String): Flow<VitalObservationEntity?> = dao.getLatestVitalForPatient(patientId)

    suspend fun recordVital(vital: VitalObservationEntity) {
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        dao.insertVital(vital.copy(syncStatus = status))
        logAudit("VITAL", vital.id, "RECORDED", "Vitals recorded for patient ${vital.patientId}")
        if (!_isOfflineMode.value) triggerSync()
    }

    // --- Clinical Notes ---
    fun getNotesForPatient(patientId: String): Flow<List<ClinicalNoteEntity>> = dao.getNotesForPatient(patientId)

    suspend fun saveClinicalNote(note: ClinicalNoteEntity): String {
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        val noteToSave = note.copy(syncStatus = status)
        dao.insertNote(noteToSave)
        logAudit("NOTE", noteToSave.id, if (noteToSave.isDraft) "DRAFT_SAVED" else "FINALIZED", "Clinical note by ${noteToSave.author}")
        if (!_isOfflineMode.value) triggerSync()
        return noteToSave.id
    }

    // --- Investigation Orders ---
    fun getAllOrders(): Flow<List<InvestigationOrderEntity>> = dao.getAllOrders()
    fun getOrdersForPatient(patientId: String): Flow<List<InvestigationOrderEntity>> = dao.getOrdersForPatient(patientId)
    fun getPendingOrdersCount(): Flow<Int> = dao.getPendingOrdersCount()
    fun getOrderById(id: String): Flow<InvestigationOrderEntity?> = dao.getOrderById(id)
    fun getEventsForOrder(orderId: String): Flow<List<InvestigationEventEntity>> = dao.getEventsForOrder(orderId)

    suspend fun placeInvestigationOrder(order: InvestigationOrderEntity): String {
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        val orderToSave = order.copy(syncStatus = status)
        dao.insertOrder(orderToSave)

        // Record initial ORDERED event
        dao.insertEvent(
            InvestigationEventEntity(
                orderId = orderToSave.id,
                eventStatus = InvestigationStatus.ORDERED,
                eventDateTimeFormatted = "${orderToSave.orderDate} ${orderToSave.orderTime}",
                responsiblePerson = orderToSave.orderingClinician,
                comments = "Order submitted with priority ${orderToSave.priority}"
            )
        )

        logAudit("INVESTIGATION_ORDER", orderToSave.id, "ORDERED", "Ordered ${orderToSave.investigationName}")
        if (!_isOfflineMode.value) triggerSync()
        return orderToSave.id
    }

    suspend fun advanceInvestigationWorkflow(
        order: InvestigationOrderEntity,
        newStatus: InvestigationStatus,
        responsiblePerson: String,
        comments: String = ""
    ) {
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        dao.updateOrder(order.copy(currentStatus = newStatus, syncStatus = status))
        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        dao.insertEvent(
            InvestigationEventEntity(
                orderId = order.id,
                eventStatus = newStatus,
                eventDateTimeFormatted = currentDateTime,
                responsiblePerson = responsiblePerson,
                comments = comments
            )
        )
        logAudit("INVESTIGATION_WORKFLOW", order.id, newStatus.name, "Status advanced to $newStatus by $responsiblePerson")
        if (!_isOfflineMode.value) triggerSync()
    }

    // --- Investigation Results ---
    fun getAllResults(): Flow<List<InvestigationResultEntity>> = dao.getAllResults()
    fun getResultsForPatient(patientId: String): Flow<List<InvestigationResultEntity>> = dao.getResultsForPatient(patientId)
    fun getResultsAwaitingReview(): Flow<List<InvestigationResultEntity>> = dao.getResultsAwaitingReview()
    fun getAwaitingReviewCount(): Flow<Int> = dao.getAwaitingReviewCount()

    suspend fun saveInvestigationResult(result: InvestigationResultEntity): String {
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        val resultToSave = result.copy(syncStatus = status)
        dao.insertResult(resultToSave)

        // If linked to an order, update order status to RESULT_AVAILABLE
        if (!resultToSave.orderId.isNullOrBlank()) {
            val order = dao.getOrderById(resultToSave.orderId).firstOrNull()
            if (order != null && order.currentStatus != InvestigationStatus.COMPLETED) {
                dao.updateOrder(order.copy(currentStatus = InvestigationStatus.RESULT_AVAILABLE, syncStatus = status))
                dao.insertEvent(
                    InvestigationEventEntity(
                        orderId = order.id,
                        eventStatus = InvestigationStatus.RESULT_AVAILABLE,
                        eventDateTimeFormatted = "${resultToSave.measurementDate} ${resultToSave.measurementTime}",
                        responsiblePerson = resultToSave.dataSource,
                        comments = "Result uploaded: ${resultToSave.parameterName} = ${resultToSave.originalTextValue} ${resultToSave.unit}"
                    )
                )
            }
        }

        logAudit("INVESTIGATION_RESULT", resultToSave.id, "RECORDED", "Result recorded: ${resultToSave.parameterName}")
        if (!_isOfflineMode.value) triggerSync()
        return resultToSave.id
    }

    suspend fun reviewInvestigationResult(resultId: String, clinicianName: String) {
        val results = dao.getAllResults().firstOrNull() ?: return
        val target = results.find { it.id == resultId } ?: return
        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val parts = currentDateTime.split(" ")
        val updated = target.copy(
            isReviewed = true,
            reviewDate = parts[0],
            reviewTime = parts.getOrNull(1) ?: "10:00",
            reviewingClinician = clinicianName,
            syncStatus = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        )
        dao.updateResult(updated)

        if (!updated.orderId.isNullOrBlank()) {
            val order = dao.getOrderById(updated.orderId).firstOrNull()
            if (order != null) {
                dao.updateOrder(order.copy(currentStatus = InvestigationStatus.RESULT_REVIEWED))
                dao.insertEvent(
                    InvestigationEventEntity(
                        orderId = order.id,
                        eventStatus = InvestigationStatus.RESULT_REVIEWED,
                        eventDateTimeFormatted = currentDateTime,
                        responsiblePerson = clinicianName,
                        comments = "Clinician review completed by $clinicianName"
                    )
                )
            }
        }

        logAudit("INVESTIGATION_RESULT", resultId, "REVIEWED", "Result reviewed by $clinicianName")
        if (!_isOfflineMode.value) triggerSync()
    }

    // --- Trends ---
    fun getTrendsForPatient(patientId: String): Flow<List<ParameterTrendSummary>> {
        return dao.getResultsForPatient(patientId).map { results ->
            // Group by parameter name
            val grouped = results.groupBy { it.parameterName }
            grouped.map { (paramName, paramResults) ->
                val sample = paramResults.first()
                TrendEngine.calculateTrend(
                    parameterName = paramName,
                    category = sample.category,
                    unit = sample.unit,
                    results = paramResults
                )
            }.sortedBy { it.parameterName }
        }
    }

    // --- Discharge Summaries ---
    fun getDischargeSummaryForPatient(patientId: String): Flow<DischargeSummaryEntity?> = dao.getDischargeSummaryForPatient(patientId)

    suspend fun saveDischargeSummary(summary: DischargeSummaryEntity) {
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        dao.insertDischargeSummary(summary.copy(syncStatus = status))
        logAudit("DISCHARGE_SUMMARY", summary.id, if (summary.isFinalized) "FINALIZED" else "SAVED", "Discharge summary for patient ${summary.patientId}")
        if (!_isOfflineMode.value) triggerSync()
    }

    // --- Follow-Up Tasks ---
    fun getAllFollowUpTasks(): Flow<List<FollowUpTaskEntity>> = dao.getAllFollowUpTasks()
    fun getFollowUpTasksForPatient(patientId: String): Flow<List<FollowUpTaskEntity>> = dao.getFollowUpTasksForPatient(patientId)

    suspend fun saveFollowUpTask(task: FollowUpTaskEntity) {
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        dao.insertFollowUpTask(task.copy(syncStatus = status))
        logAudit("FOLLOW_UP_TASK", task.id, "SAVED", "Task ${task.taskType}: ${task.description}")
        if (!_isOfflineMode.value) triggerSync()
    }

    suspend fun completeFollowUpTask(taskId: String) {
        val tasks = dao.getAllFollowUpTasks().firstOrNull() ?: return
        val task = tasks.find { it.id == taskId } ?: return
        val updated = task.copy(
            status = TaskStatus.COMPLETED,
            completionDate = todayString(),
            syncStatus = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        )
        dao.updateFollowUpTask(updated)
        logAudit("FOLLOW_UP_TASK", taskId, "COMPLETED", "Task completed on ${todayString()}")
        if (!_isOfflineMode.value) triggerSync()
    }

    // --- Shift Handovers (SBAR + I-PASS) ---
    fun getAllHandovers(): Flow<List<HandoverEntryEntity>> = dao.getAllHandovers()
    fun getHandoversForPatient(patientId: String): Flow<List<HandoverEntryEntity>> = dao.getHandoversForPatient(patientId)

    suspend fun saveHandover(handover: HandoverEntryEntity) {
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        val toSave = handover.copy(
            syncStatus = status,
            lastUpdated = System.currentTimeMillis()
        )
        dao.insertHandover(toSave)
        logAudit(
            "HANDOVER",
            toSave.id,
            toSave.status.name,
            "Shift handover (${toSave.shiftType.label}) for ${toSave.patientName} [${toSave.illnessSeverity.label}]"
        )
        if (!_isOfflineMode.value) triggerSync()
    }

    suspend fun acknowledgeHandover(
        handoverId: String,
        incomingClinician: String,
        synthesisConfirmed: Boolean
    ) {
        val existing = dao.getHandoverByIdOnce(handoverId) ?: return
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val status = if (_isOfflineMode.value) SyncStatus.SAVED_LOCALLY else SyncStatus.SYNC_PENDING
        val updated = existing.copy(
            incomingClinician = incomingClinician,
            acknowledgedAt = now,
            synthesisConfirmed = synthesisConfirmed,
            status = HandoverStatus.ACKNOWLEDGED,
            syncStatus = status,
            lastUpdated = System.currentTimeMillis()
        )
        dao.updateHandover(updated)
        logAudit(
            "HANDOVER",
            handoverId,
            "ACKNOWLEDGED",
            "Shift handover for ${existing.patientName} signed off by $incomingClinician (Read-back: $synthesisConfirmed)"
        )
        if (!_isOfflineMode.value) triggerSync()
    }

    suspend fun deleteHandover(handoverId: String) {
        dao.deleteHandover(handoverId)
        logAudit("HANDOVER", handoverId, "DELETED", "Removed shift handover draft $handoverId")
    }

    suspend fun getLatestVitalOnce(patientId: String): VitalObservationEntity? {
        return dao.getLatestVitalForPatient(patientId).firstOrNull()
    }

    // --- Audit Logs ---
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>> = dao.getAllAuditLogs()

    private suspend fun logAudit(entityType: String, entityId: String, action: String, details: String) {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        dao.insertAuditLog(
            AuditLogEntity(
                formattedDateTime = now,
                entityType = entityType,
                entityId = entityId,
                action = action,
                performedBy = _currentUser.value.name,
                details = details
            )
        )
    }

    private fun todayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    // --- Reset & Seed Synthetic Data as required by PDF page 31 ---
    suspend fun resetDatabase() {
        dao.clearPatients()
        dao.clearAdmissions()
        dao.clearRounds()
        dao.clearVitals()
        dao.clearNotes()
        dao.clearOrders()
        dao.clearEvents()
        dao.clearResults()
        dao.clearDischargeSummaries()
        dao.clearFollowUpTasks()
        dao.clearHandovers()
        seedSyntheticData()
    }

    suspend fun seedSyntheticData() {
        // Patient 1: Rahul Sharma (From Section 31 Demonstration Data)
        val p1 = PatientEntity(
            id = "demo-patient-001",
            uhid = "DEMO-00125",
            name = "Rahul Sharma",
            age = 46,
            sex = "Male",
            admissionNumber = "ADM-2026-9042",
            ward = "Medicine Ward",
            bedNumber = "12",
            department = "Internal Medicine",
            consultant = "Dr. S. Mukherjee, MD",
            admissionDate = "2026-10-24",
            admissionTime = "09:30",
            primaryDiagnosis = "Community Acquired Pneumonia (Right Lower Lobe)",
            provisionalDiagnosis = "Severe Bacterial Pneumonia with Sepsis",
            comorbidities = "Type 2 Diabetes Mellitus (HbA1c 7.2%), Essential Hypertension",
            allergies = "NKDA (No Known Drug Allergies)",
            pastMedicalHistory = "T2DM for 6 years on Metformin 500mg BD. Hypertension on Telmisartan 40mg OD.",
            pastSurgicalHistory = "None",
            contactInfo = "+91 98765 43210 (Spouse: Sunita Sharma)",
            isDischarged = false,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertPatient(p1)

        val adm1 = AdmissionEntity(
            id = "adm-001",
            patientId = p1.id,
            uhid = p1.uhid,
            admissionNumber = p1.admissionNumber,
            admissionDate = p1.admissionDate,
            ward = p1.ward,
            bedNumber = p1.bedNumber,
            admissionDiagnosis = p1.primaryDiagnosis,
            status = AdmissionStatus.ACTIVE,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertAdmission(adm1)

        // Day 1: 2026-10-24
        val r1 = RoundEntryEntity(
            id = "round-p1-d1",
            patientId = p1.id,
            admissionId = adm1.id,
            roundDate = "2026-10-24",
            roundTime = "10:30",
            doctorName = "Dr. Ananya Ray",
            ward = "Medicine Ward",
            bedNumber = "12",
            hospitalDay = 1,
            roundType = RoundType.MORNING,
            overnightEvents = "Admitted from Emergency with severe fever and right-sided pleuritic chest pain.",
            currentComplaints = "High grade fever with chills, productive cough with rust-colored sputum, right pleuritic pain.",
            symptomProgression = "Symptoms progressed over last 4 days. Dyspnea on minimal exertion.",
            feverEvents = "Recorded 102.4 F at 06:00 AM. Paracetamol 1g IV administered.",
            currentCondition = "Toxic appearance, tachypneic, alert and oriented.",
            responseToTreatment = "Empirical IV Ceftriaxone 2g OD + Azithromycin 500mg OD started.",
            changesSincePrevious = "Initial baseline ward assessment.",
            generalExam = "Pallor mild, no icterus, no cyanosis, dry oral mucosa, hydration moderate.",
            respiratoryExam = "Tachypnea (RR 24/min), dull percussion right base, bronchial breathing and coarse inspiratory crackles in right infrascapular area.",
            cvsExam = "S1 S2 heard normal, no murmurs. Tachycardia (102 bpm).",
            abdomenExam = "Soft, non-tender, bowel sounds present, no organomegaly.",
            cnsExam = "Conscious, oriented to time/place/person, GCS 15/15.",
            otherExam = "Bilateral lower limb peripheral pulses palpable. Calves soft.",
            currentDiagnosis = "Community Acquired Pneumonia (Right Lower Lobe)",
            differentialDiagnosis = "Pulmonary Tuberculosis, Acute Bronchitis, Pulmonary Thromboembolism",
            clinicalAssessment = "Moderate-to-severe CAP requiring inpatient IV antibiotic therapy, close vitals and oxygen monitoring.",
            problemsIdentified = "Hypoxemia (SpO2 93% on room air), elevated inflammatory response.",
            treatmentChanges = "Commenced IV Ceftriaxone 2g OD + Azithromycin 500mg IV OD.",
            medicationPlan = "IV Ceftriaxone 2g OD\nIV Azithromycin 500mg OD\nTab Paracetamol 650mg SOS\nIV Pantoprazole 40mg OD\nSubcutaneous Enoxaparin 40mg OD (DVT prophylaxis)",
            procedures = "Peripheral IV cannula 20G inserted left forearm. Sputum and blood cultures collected.",
            monitoringPlan = "Hourly SpO2, 4-hourly temperature and BP chart, strict intake/output chart.",
            dietFluidAdvice = "Diabetic soft diet, IV fluids 0.9% Normal Saline at 75 mL/hr.",
            respiratorySupportPlan = "Nasal oxygen support 2 L/min via nasal prongs to maintain SpO2 >= 95%.",
            referrals = "Pulmonology review requested.",
            investigationsOrdered = "CBC, Blood Culture, Sputum Gram stain & Culture, Procalcitonin, CRP, Chest X-ray, KFT",
            expectedDischargeDate = "2026-10-28",
            dailySummary = "Day 1 admission for severe RLL CAP with high fever and hypoxia. Antibiotics initiated with oxygen support.",
            isDraft = false,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertRound(r1)

        val v1 = VitalObservationEntity(
            id = "v-p1-d1",
            patientId = p1.id,
            admissionId = adm1.id,
            roundId = r1.id,
            measurementDate = "2026-10-24",
            measurementTime = "10:00",
            bpSystolic = 118,
            bpDiastolic = 76,
            pulseRate = 102,
            respiratoryRate = 24,
            temperatureF = 102.4,
            spO2 = 93,
            oxygenDeliveryMethod = "Room Air",
            oxygenFlowRateLpm = 0.0,
            bloodGlucoseMgDl = 168,
            painScore = 6,
            otherObservations = "Right sided pleuritic chest pain on deep inspiration.",
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertVital(v1)

        val n1 = ClinicalNoteEntity(
            id = "note-p1-d1",
            patientId = p1.id,
            admissionId = adm1.id,
            date = "2026-10-24",
            time = "11:00",
            author = "Dr. Ananya Ray",
            noteType = "Admission Clinical Note",
            hospitalDay = 1,
            title = "Admission Assessment & Workup",
            content = "Patient Rahul Sharma (UHID: DEMO-00125) admitted with acute febrile illness and productive cough. RLL crackles confirmed. Empirical coverage started.",
            isDraft = false,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertNote(n1)

        // Day 2: 2026-10-25
        val r2 = RoundEntryEntity(
            id = "round-p1-d2",
            patientId = p1.id,
            admissionId = adm1.id,
            roundDate = "2026-10-25",
            roundTime = "09:45",
            doctorName = "Dr. Ananya Ray",
            ward = "Medicine Ward",
            bedNumber = "12",
            hospitalDay = 2,
            roundType = RoundType.MORNING,
            overnightEvents = "One spike of fever (100.8F) at 02:00 AM, settled with Paracetamol.",
            currentComplaints = "Cough persists, less chest pain, breathing feels easier on oxygen.",
            symptomProgression = "Fever frequency declining, sputum clearing slightly.",
            feverEvents = "100.2F recorded at 08:00 AM.",
            currentCondition = "Better general condition, less distressed.",
            responseToTreatment = "Favorable early response to IV Ceftriaxone.",
            changesSincePrevious = "Heart rate normalized to 90 bpm, respiratory rate dropped from 24 to 20.",
            generalExam = "Hydration improved, alert, comfortably lying in bed with nasal prongs.",
            respiratoryExam = "Crackles still present in right lower zone, bronchial breathing diminished.",
            cvsExam = "Normal heart sounds, PR 90/min, no murmurs.",
            abdomenExam = "Soft, non-tender, normal sounds.",
            cnsExam = "Conscious, cooperative.",
            otherExam = "No pedal edema.",
            currentDiagnosis = "Resolving Community Acquired Pneumonia (RLL)",
            differentialDiagnosis = "Unchanged",
            clinicalAssessment = "Good response to antibacterial regimen. Oxygen requirement stable on 2 L/min.",
            problemsIdentified = "Mild hyperglycemia on sliding scale.",
            treatmentChanges = "Continue IV Ceftriaxone and Azithromycin. Check post-prandial sugars.",
            medicationPlan = "Continue IV Ceftriaxone 2g OD\nContinue Azithromycin 500mg OD\nParacetamol 650mg TDS PRN",
            procedures = "Sputum sample transported to lab.",
            monitoringPlan = "Maintain SpO2 > 95%, review morning lab results.",
            dietFluidAdvice = "Normal diabetic diet, liberal oral fluids encouraged.",
            respiratorySupportPlan = "Nasal cannula 2 L/min.",
            referrals = "None needed today.",
            investigationsOrdered = "KFT, LFT, ABG, Sputum Culture repeat",
            expectedDischargeDate = "2026-10-28",
            dailySummary = "Day 2 review shows clinical stabilization. Fever spikes spaced out, respiratory effort reduced.",
            isDraft = false,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertRound(r2)

        val v2 = VitalObservationEntity(
            id = "v-p1-d2",
            patientId = p1.id,
            admissionId = adm1.id,
            roundId = r2.id,
            measurementDate = "2026-10-25",
            measurementTime = "09:00",
            bpSystolic = 122,
            bpDiastolic = 78,
            pulseRate = 90,
            respiratoryRate = 20,
            temperatureF = 100.2,
            spO2 = 96,
            oxygenDeliveryMethod = "Nasal Cannula",
            oxygenFlowRateLpm = 2.0,
            bloodGlucoseMgDl = 152,
            painScore = 3,
            otherObservations = "Pleuritic pain significantly lessened.",
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertVital(v2)

        // Day 3: 2026-10-26 (Today)
        val r3 = RoundEntryEntity(
            id = "round-p1-d3",
            patientId = p1.id,
            admissionId = adm1.id,
            roundDate = "2026-10-26",
            roundTime = "10:00",
            doctorName = "Dr. Ananya Ray",
            ward = "Medicine Ward",
            bedNumber = "12",
            hospitalDay = 3,
            roundType = RoundType.MORNING,
            overnightEvents = "Afebrile throughout the night. Slept comfortably without coughing spells.",
            currentComplaints = "Minimal dry cough, no chest pain, no breathlessness.",
            symptomProgression = "Significant subjective improvement. Patient asked if he could walk in corridor.",
            feverEvents = "Afebrile for >24 hours (98.6 F).",
            currentCondition = "Euthermic, comfortable, cheerful.",
            responseToTreatment = "Excellent clinical and biomarker resolution.",
            changesSincePrevious = "Weaned off supplemental oxygen to room air with SpO2 98%. Crackles resolved.",
            generalExam = "Good hydration, pink mucosa, no distress.",
            respiratoryExam = "Vesicular breath sounds bilateral, clear lung fields, no added sounds.",
            cvsExam = "Normal sounds, PR 78/min regular, BP 120/80.",
            abdomenExam = "Soft, normal.",
            cnsExam = "Intact, fully ambulatory.",
            otherExam = "No peripheral edema.",
            currentDiagnosis = "Pneumonia - Clinical Resolution Phase",
            differentialDiagnosis = "Resolved",
            clinicalAssessment = "Clinically resolved pneumonia. Ready for oral step-down and discharge planning.",
            problemsIdentified = "None acute. Ensure outpatient diabetes continuity.",
            treatmentChanges = "Switch IV Ceftriaxone to Tab Cefpodoxime 200mg BD x 5 days.",
            medicationPlan = "Tab Cefpodoxime Proxetil 200mg BD x 5 days\nTab Azithromycin 500mg OD x 2 days\nResume home Metformin 500mg BD and Telmisartan 40mg OD",
            procedures = "Peripheral cannula removed.",
            monitoringPlan = "Observe for 24h on oral medications.",
            dietFluidAdvice = "Normal diet.",
            respiratorySupportPlan = "Room air (SpO2 98%).",
            referrals = "Outpatient Chest Clinic booking.",
            investigationsOrdered = "Repeat CBC, CRP, Chest X-ray",
            expectedDischargeDate = "2026-10-27",
            dailySummary = "Day 3 review shows complete fever defervescence, normalized vitals on room air, and clearing radiological infiltrate.",
            isDraft = false,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertRound(r3)

        val v3 = VitalObservationEntity(
            id = "v-p1-d3",
            patientId = p1.id,
            admissionId = adm1.id,
            roundId = r3.id,
            measurementDate = "2026-10-26",
            measurementTime = "09:30",
            bpSystolic = 120,
            bpDiastolic = 80,
            pulseRate = 78,
            respiratoryRate = 18,
            temperatureF = 98.6,
            spO2 = 98,
            oxygenDeliveryMethod = "Room Air",
            oxygenFlowRateLpm = 0.0,
            bloodGlucoseMgDl = 138,
            painScore = 1,
            otherObservations = "Ambulatory without fatigue or desaturation.",
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertVital(v3)

        // Seed Investigation Orders & Events
        val order1 = InvestigationOrderEntity(
            id = "ord-001",
            patientId = p1.id,
            admissionId = adm1.id,
            orderingClinician = "Dr. Ananya Ray",
            orderDate = "2026-10-24",
            orderTime = "09:45",
            investigationName = "Complete Blood Count (CBC)",
            category = "Hematology",
            priority = InvestigationPriority.ROUTINE,
            currentStatus = InvestigationStatus.COMPLETED,
            specimenInfo = "Whole Blood (EDTA)",
            clinicalIndication = "Initial sepsis and infection workup",
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertOrder(order1)

        val order2 = InvestigationOrderEntity(
            id = "ord-002",
            patientId = p1.id,
            admissionId = adm1.id,
            orderingClinician = "Dr. Ananya Ray",
            orderDate = "2026-10-24",
            orderTime = "09:50",
            investigationName = "C-Reactive Protein (CRP)",
            category = "Biochemistry",
            priority = InvestigationPriority.URGENT,
            currentStatus = InvestigationStatus.COMPLETED,
            specimenInfo = "Serum (Gel Separator)",
            clinicalIndication = "Inflammatory marker assessment",
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertOrder(order2)

        val order3 = InvestigationOrderEntity(
            id = "ord-003",
            patientId = p1.id,
            admissionId = adm1.id,
            orderingClinician = "Dr. Ananya Ray",
            orderDate = "2026-10-24",
            orderTime = "10:00",
            investigationName = "Blood Culture & Sensitivity",
            category = "Microbiology",
            priority = InvestigationPriority.STAT,
            currentStatus = InvestigationStatus.RESULT_AVAILABLE,
            specimenInfo = "Blood in BACTEC aerobic/anaerobic bottles",
            clinicalIndication = "Bacteremia surveillance in severe pneumonia",
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertOrder(order3)

        val order4 = InvestigationOrderEntity(
            id = "ord-004",
            patientId = p1.id,
            admissionId = adm1.id,
            orderingClinician = "Dr. Ananya Ray",
            orderDate = "2026-10-25",
            orderTime = "08:30",
            investigationName = "Kidney Function Test (KFT)",
            category = "Biochemistry",
            priority = InvestigationPriority.ROUTINE,
            currentStatus = InvestigationStatus.COMPLETED,
            specimenInfo = "Serum",
            clinicalIndication = "Renal monitoring on IV antibiotics",
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertOrder(order4)

        val order5 = InvestigationOrderEntity(
            id = "ord-005",
            patientId = p1.id,
            admissionId = adm1.id,
            orderingClinician = "Dr. Ananya Ray",
            orderDate = "2026-10-26",
            orderTime = "08:15",
            investigationName = "Chest X-Ray PA View",
            category = "Radiology",
            priority = InvestigationPriority.ROUTINE,
            currentStatus = InvestigationStatus.RESULT_AVAILABLE,
            specimenInfo = "Radiographic imaging",
            clinicalIndication = "Assess resolution of right lower lobe consolidation",
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertOrder(order5)

        // Seed Investigation Events for Order 1
        dao.insertEvent(InvestigationEventEntity(orderId = order1.id, eventStatus = InvestigationStatus.ORDERED, eventDateTimeFormatted = "2026-10-24 09:45", responsiblePerson = "Dr. Ananya Ray", comments = "Order placed in digital register"))
        dao.insertEvent(InvestigationEventEntity(orderId = order1.id, eventStatus = InvestigationStatus.SAMPLE_COLLECTED, eventDateTimeFormatted = "2026-10-24 10:15", responsiblePerson = "Staff Nurse Mercy", comments = "Sample collected in EDTA vacutainer"))
        dao.insertEvent(InvestigationEventEntity(orderId = order1.id, eventStatus = InvestigationStatus.SENT_TO_LAB, eventDateTimeFormatted = "2026-10-24 10:30", responsiblePerson = "Orderly Ramesh", comments = "Dispatched via pneumatic tube system"))
        dao.insertEvent(InvestigationEventEntity(orderId = order1.id, eventStatus = InvestigationStatus.RECEIPT_CONFIRMED, eventDateTimeFormatted = "2026-10-24 10:42", responsiblePerson = "Central Lab Reception", comments = "Sample barcoded and accessioned"))
        dao.insertEvent(InvestigationEventEntity(orderId = order1.id, eventStatus = InvestigationStatus.RESULT_AVAILABLE, eventDateTimeFormatted = "2026-10-24 11:30", responsiblePerson = "Sysmex XN-1000 Analyzer", comments = "Automated analyzer results released"))
        dao.insertEvent(InvestigationEventEntity(orderId = order1.id, eventStatus = InvestigationStatus.RESULT_REVIEWED, eventDateTimeFormatted = "2026-10-24 12:15", responsiblePerson = "Dr. Ananya Ray", comments = "Marked leukocytosis noted; verified"))
        dao.insertEvent(InvestigationEventEntity(orderId = order1.id, eventStatus = InvestigationStatus.COMPLETED, eventDateTimeFormatted = "2026-10-24 12:20", responsiblePerson = "Dr. Ananya Ray", comments = "Workflow cycle completed"))

        // Seed Serial Investigation Results across Day 1, Day 2, Day 3 (Testing Trend Engine)
        // Hemoglobin (g/dL) - Ref: 13.0 - 17.0
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order1.id, investigationName = "CBC", category = "Hematology", parameterName = "Hemoglobin", numericValue = 12.8, originalTextValue = "12.8", unit = "g/dL", refRangeLow = 13.0, refRangeHigh = 17.0, refRangeText = "13.0 - 17.0 g/dL", measurementDate = "2026-10-24", measurementTime = "10:30", isReviewed = true, isAbnormal = true, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order1.id, investigationName = "CBC", category = "Hematology", parameterName = "Hemoglobin", numericValue = 12.7, originalTextValue = "12.7", unit = "g/dL", refRangeLow = 13.0, refRangeHigh = 17.0, refRangeText = "13.0 - 17.0 g/dL", measurementDate = "2026-10-25", measurementTime = "09:15", isReviewed = true, isAbnormal = true, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order1.id, investigationName = "CBC", category = "Hematology", parameterName = "Hemoglobin", numericValue = 13.1, originalTextValue = "13.1", unit = "g/dL", refRangeLow = 13.0, refRangeHigh = 17.0, refRangeText = "13.0 - 17.0 g/dL", measurementDate = "2026-10-26", measurementTime = "08:45", isReviewed = true, isAbnormal = false, syncStatus = SyncStatus.SYNCED))

        // Total WBC Count (x10^3/uL) - Ref: 4.0 - 11.0
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order1.id, investigationName = "CBC", category = "Hematology", parameterName = "Total WBC", numericValue = 16.4, originalTextValue = "16.4", unit = "x10^3/uL", refRangeLow = 4.0, refRangeHigh = 11.0, refRangeText = "4.0 - 11.0 x10^3/uL", measurementDate = "2026-10-24", measurementTime = "10:30", isReviewed = true, isAbnormal = true, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order1.id, investigationName = "CBC", category = "Hematology", parameterName = "Total WBC", numericValue = 12.8, originalTextValue = "12.8", unit = "x10^3/uL", refRangeLow = 4.0, refRangeHigh = 11.0, refRangeText = "4.0 - 11.0 x10^3/uL", measurementDate = "2026-10-25", measurementTime = "09:15", isReviewed = true, isAbnormal = true, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order1.id, investigationName = "CBC", category = "Hematology", parameterName = "Total WBC", numericValue = 9.2, originalTextValue = "9.2", unit = "x10^3/uL", refRangeLow = 4.0, refRangeHigh = 11.0, refRangeText = "4.0 - 11.0 x10^3/uL", measurementDate = "2026-10-26", measurementTime = "08:45", isReviewed = true, isAbnormal = false, syncStatus = SyncStatus.SYNCED))

        // Platelets (x10^3/uL) - Ref: 150 - 450
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order1.id, investigationName = "CBC", category = "Hematology", parameterName = "Platelets", numericValue = 220.0, originalTextValue = "220", unit = "x10^3/uL", refRangeLow = 150.0, refRangeHigh = 450.0, refRangeText = "150 - 450 x10^3/uL", measurementDate = "2026-10-24", measurementTime = "10:30", isReviewed = true, isAbnormal = false, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order1.id, investigationName = "CBC", category = "Hematology", parameterName = "Platelets", numericValue = 240.0, originalTextValue = "240", unit = "x10^3/uL", refRangeLow = 150.0, refRangeHigh = 450.0, refRangeText = "150 - 450 x10^3/uL", measurementDate = "2026-10-25", measurementTime = "09:15", isReviewed = true, isAbnormal = false, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order1.id, investigationName = "CBC", category = "Hematology", parameterName = "Platelets", numericValue = 255.0, originalTextValue = "255", unit = "x10^3/uL", refRangeLow = 150.0, refRangeHigh = 450.0, refRangeText = "150 - 450 x10^3/uL", measurementDate = "2026-10-26", measurementTime = "08:45", isReviewed = true, isAbnormal = false, syncStatus = SyncStatus.SYNCED))

        // C-Reactive Protein (CRP) (mg/L) - Ref: 0.0 - 5.0
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order2.id, investigationName = "CRP", category = "Biochemistry", parameterName = "CRP", numericValue = 85.0, originalTextValue = "85.0", unit = "mg/L", refRangeLow = 0.0, refRangeHigh = 5.0, refRangeText = "0.0 - 5.0 mg/L", measurementDate = "2026-10-24", measurementTime = "11:00", isReviewed = true, isAbnormal = true, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order2.id, investigationName = "CRP", category = "Biochemistry", parameterName = "CRP", numericValue = 42.0, originalTextValue = "42.0", unit = "mg/L", refRangeLow = 0.0, refRangeHigh = 5.0, refRangeText = "0.0 - 5.0 mg/L", measurementDate = "2026-10-25", measurementTime = "09:30", isReviewed = true, isAbnormal = true, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order2.id, investigationName = "CRP", category = "Biochemistry", parameterName = "CRP", numericValue = 18.0, originalTextValue = "18.0", unit = "mg/L", refRangeLow = 0.0, refRangeHigh = 5.0, refRangeText = "0.0 - 5.0 mg/L", measurementDate = "2026-10-26", measurementTime = "08:50", isReviewed = false, isAbnormal = true, syncStatus = SyncStatus.SYNCED)) // Awaiting review!

        // Procalcitonin (ng/mL) - Ref: < 0.5
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = null, investigationName = "Procalcitonin", category = "Biochemistry", parameterName = "Procalcitonin", numericValue = 2.4, originalTextValue = "2.4", unit = "ng/mL", refRangeLow = 0.0, refRangeHigh = 0.5, refRangeText = "< 0.5 ng/mL", measurementDate = "2026-10-24", measurementTime = "11:15", isReviewed = true, isAbnormal = true, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = null, investigationName = "Procalcitonin", category = "Biochemistry", parameterName = "Procalcitonin", numericValue = 0.8, originalTextValue = "0.8", unit = "ng/mL", refRangeLow = 0.0, refRangeHigh = 0.5, refRangeText = "< 0.5 ng/mL", measurementDate = "2026-10-26", measurementTime = "09:00", isReviewed = false, isAbnormal = true, syncStatus = SyncStatus.SYNCED))

        // Serum Creatinine (mg/dL) - Ref: 0.7 - 1.3
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order4.id, investigationName = "KFT", category = "Biochemistry", parameterName = "Serum Creatinine", numericValue = 1.3, originalTextValue = "1.3", unit = "mg/dL", refRangeLow = 0.7, refRangeHigh = 1.3, refRangeText = "0.7 - 1.3 mg/dL", measurementDate = "2026-10-24", measurementTime = "10:45", isReviewed = true, isAbnormal = false, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order4.id, investigationName = "KFT", category = "Biochemistry", parameterName = "Serum Creatinine", numericValue = 1.1, originalTextValue = "1.1", unit = "mg/dL", refRangeLow = 0.7, refRangeHigh = 1.3, refRangeText = "0.7 - 1.3 mg/dL", measurementDate = "2026-10-25", measurementTime = "09:15", isReviewed = true, isAbnormal = false, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order4.id, investigationName = "KFT", category = "Biochemistry", parameterName = "Serum Creatinine", numericValue = 1.0, originalTextValue = "1.0", unit = "mg/dL", refRangeLow = 0.7, refRangeHigh = 1.3, refRangeText = "0.7 - 1.3 mg/dL", measurementDate = "2026-10-26", measurementTime = "09:10", isReviewed = false, isAbnormal = false, syncStatus = SyncStatus.SYNCED))

        // Blood Urea (mg/dL) - Ref: 15.0 - 45.0
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order4.id, investigationName = "KFT", category = "Biochemistry", parameterName = "Blood Urea", numericValue = 38.0, originalTextValue = "38.0", unit = "mg/dL", refRangeLow = 15.0, refRangeHigh = 45.0, refRangeText = "15.0 - 45.0 mg/dL", measurementDate = "2026-10-24", measurementTime = "10:45", isReviewed = true, isAbnormal = false, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order4.id, investigationName = "KFT", category = "Biochemistry", parameterName = "Blood Urea", numericValue = 32.0, originalTextValue = "32.0", unit = "mg/dL", refRangeLow = 15.0, refRangeHigh = 45.0, refRangeText = "15.0 - 45.0 mg/dL", measurementDate = "2026-10-25", measurementTime = "09:15", isReviewed = true, isAbnormal = false, syncStatus = SyncStatus.SYNCED))

        // Non-numeric / Qualitative Results (Mandated by Section 14 & 26: Do NOT force qualitative into charts)
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order3.id, investigationName = "Blood Culture", category = "Microbiology", parameterName = "Culture Isolation", numericValue = null, originalTextValue = "Positive for Streptococcus pneumoniae (Sensitive to Ceftriaxone, Penicillin G, Levofloxacin)", unit = "", refRangeText = "No growth in 48 hours", measurementDate = "2026-10-25", measurementTime = "16:00", isReviewed = false, isAbnormal = true, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p1.id, admissionId = adm1.id, orderId = order5.id, investigationName = "Chest X-Ray", category = "Radiology", parameterName = "Radiological Impression", numericValue = null, originalTextValue = "Right lower zone alveolar opacity showing marked clearance compared to admission film. No pleural effusion. Normal cardiothoracic ratio.", unit = "", refRangeText = "Normal lung parenchyma", measurementDate = "2026-10-26", measurementTime = "10:15", isReviewed = false, isAbnormal = false, syncStatus = SyncStatus.SYNCED))

        // Discharge Summary Draft
        val ds = DischargeSummaryEntity(
            id = "ds-p1",
            patientId = p1.id,
            admissionId = adm1.id,
            admissionDiagnosis = "Community Acquired Pneumonia (RLL)",
            finalDiagnosis = "Community Acquired Lobar Pneumonia (Streptococcus pneumoniae) - Resolved",
            relevantClinicalCourse = "46M admitted with fever, pleuritic pain, hypoxia. Responded swiftly to IV Ceftriaxone + Azithromycin. Weaned off O2 Day 3. Switched to oral Cefpodoxime.",
            importantExamFindings = "Afebrile, chest clear bilateral, SpO2 98% room air.",
            importantInvestigationResults = "WBC normalized (16.4 -> 9.2). CRP declined (85 -> 18). CXR shows marked resolution.",
            proceduresPerformed = "Peripheral venous access, sputum & blood culture collection.",
            conditionAtDischarge = "Clinically stable, afebrile for 36 hours, tolerating oral diet and medications.",
            treatmentReceived = "IV Ceftriaxone 2g OD x 3 days, IV Azithromycin 500mg OD x 3 days, O2 therapy.",
            dischargeMedications = "1. Tab Cefpodoxime 200mg BD x 5 days (Oral antibiotic)\n2. Tab Paracetamol 650mg SOS for fever\n3. Resume Tab Metformin 500mg BD (with meals)\n4. Resume Tab Telmisartan 40mg OD (morning)",
            dietAdvice = "Low glycemic index diabetic diet, adequate fluids (2-2.5 L/day).",
            activityAdvice = "Normal mobilization, avoid cold exposure and vigorous exertion for 10 days.",
            warningSigns = "High fever recurrence > 101F, difficulty breathing, blood in sputum, chest pain.",
            urgentCareInstructions = "Report directly to Emergency Department (Room 04) if any warning sign occurs.",
            followUpDepartment = "Internal Medicine OPD",
            followUpDate = "2026-11-02",
            followUpInvestigations = "Repeat CBC and CRP at 1-week follow-up visit.",
            pendingInvestigationReports = "Final 5-day blood culture report pending in lab.",
            responsibleClinician = "Dr. S. Mukherjee, MD / Dr. Ananya Ray",
            dischargeStatus = DischargeStatus.READY_FOR_CLINICIAN_REVIEW,
            isFinalized = false,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertDischargeSummary(ds)

        // Follow-Up Tasks
        val t1 = FollowUpTaskEntity(
            id = "task-p1-1",
            patientId = p1.id,
            uhid = p1.uhid,
            taskType = "Outpatient Follow-up",
            description = "1-week Post-discharge Clinical Review with Dr. S. Mukherjee",
            dueDate = "2026-11-02",
            dueTime = "10:30",
            responsibleDepartmentOrDoctor = "Internal Medicine OPD Room 14",
            priority = TaskPriority.HIGH,
            status = TaskStatus.SCHEDULED,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertFollowUpTask(t1)

        val t2 = FollowUpTaskEntity(
            id = "task-p1-2",
            patientId = p1.id,
            uhid = p1.uhid,
            taskType = "Repeat Lab",
            description = "Repeat CBC and CRP prior to OPD review",
            dueDate = "2026-11-02",
            dueTime = "08:30",
            responsibleDepartmentOrDoctor = "Central Collection Room",
            linkedInvestigation = "CBC, CRP",
            priority = TaskPriority.MEDIUM,
            status = TaskStatus.SCHEDULED,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertFollowUpTask(t2)

        val t3 = FollowUpTaskEntity(
            id = "task-p1-3",
            patientId = p1.id,
            uhid = p1.uhid,
            taskType = "Pending Report Review",
            description = "Check final automated microbiology culture clearance",
            dueDate = "2026-10-27",
            dueTime = "12:00",
            responsibleDepartmentOrDoctor = "Dr. Ananya Ray",
            priority = TaskPriority.HIGH,
            status = TaskStatus.DUE_TODAY,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertFollowUpTask(t3)

        // Patient 2: Priya Patel (Cardiology)
        val p2 = PatientEntity(
            id = "demo-patient-002",
            uhid = "DEMO-00126",
            name = "Priya Patel",
            age = 58,
            sex = "Female",
            admissionNumber = "ADM-2026-9055",
            ward = "Cardiology Ward",
            bedNumber = "04",
            department = "Cardiology",
            consultant = "Dr. Vikram Seth, DM",
            admissionDate = "2026-10-25",
            admissionTime = "14:20",
            primaryDiagnosis = "Acute Coronary Syndrome (NSTEMI)",
            provisionalDiagnosis = "High-Risk Unstable Angina / NSTEMI",
            comorbidities = "Dyslipidemia, Hypothyroidism",
            allergies = "Aspirin (Mild Gastritis) - On Clopidogrel + PPI",
            pastMedicalHistory = "Hypothyroidism on Eltroxin 50 mcg OD.",
            pastSurgicalHistory = "Cholecystectomy (2018)",
            contactInfo = "+91 97654 32109",
            isDischarged = false,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertPatient(p2)

        val adm2 = AdmissionEntity(
            id = "adm-002",
            patientId = p2.id,
            uhid = p2.uhid,
            admissionNumber = p2.admissionNumber,
            admissionDate = p2.admissionDate,
            ward = p2.ward,
            bedNumber = p2.bedNumber,
            admissionDiagnosis = p2.primaryDiagnosis,
            status = AdmissionStatus.ACTIVE,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertAdmission(adm2)

        // Cardiac biomarkers serial trend
        dao.insertResult(InvestigationResultEntity(patientId = p2.id, admissionId = adm2.id, investigationName = "Troponin I", category = "Cardiology", parameterName = "High-Sensitivity Troponin I", numericValue = 0.85, originalTextValue = "0.85", unit = "ng/mL", refRangeLow = 0.0, refRangeHigh = 0.04, refRangeText = "< 0.04 ng/mL", measurementDate = "2026-10-25", measurementTime = "15:00", isReviewed = true, isAbnormal = true, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p2.id, admissionId = adm2.id, investigationName = "Troponin I", category = "Cardiology", parameterName = "High-Sensitivity Troponin I", numericValue = 0.45, originalTextValue = "0.45", unit = "ng/mL", refRangeLow = 0.0, refRangeHigh = 0.04, refRangeText = "< 0.04 ng/mL", measurementDate = "2026-10-25", measurementTime = "21:00", isReviewed = true, isAbnormal = true, syncStatus = SyncStatus.SYNCED))
        dao.insertResult(InvestigationResultEntity(patientId = p2.id, admissionId = adm2.id, investigationName = "Troponin I", category = "Cardiology", parameterName = "High-Sensitivity Troponin I", numericValue = 0.18, originalTextValue = "0.18", unit = "ng/mL", refRangeLow = 0.0, refRangeHigh = 0.04, refRangeText = "< 0.04 ng/mL", measurementDate = "2026-10-26", measurementTime = "09:00", isReviewed = false, isAbnormal = true, syncStatus = SyncStatus.SYNCED))

        // Patient 3: Amit Verma (General Surgery)
        val p3 = PatientEntity(
            id = "demo-patient-003",
            uhid = "DEMO-00127",
            name = "Amit Verma",
            age = 32,
            sex = "Male",
            admissionNumber = "ADM-2026-9061",
            ward = "Surgery Ward",
            bedNumber = "19",
            department = "General Surgery",
            consultant = "Dr. Rajesh Rao, MS",
            admissionDate = "2026-10-25",
            admissionTime = "18:00",
            primaryDiagnosis = "Acute Appendicitis - Post Laparoscopic Appendectomy",
            provisionalDiagnosis = "Suppurative Appendicitis",
            comorbidities = "None",
            allergies = "NKDA",
            pastMedicalHistory = "Nil significant",
            pastSurgicalHistory = "Nil",
            contactInfo = "+91 91234 56789",
            isDischarged = false,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertPatient(p3)

        val adm3 = AdmissionEntity(
            id = "adm-003",
            patientId = p3.id,
            uhid = p3.uhid,
            admissionNumber = p3.admissionNumber,
            admissionDate = p3.admissionDate,
            ward = p3.ward,
            bedNumber = p3.bedNumber,
            admissionDiagnosis = p3.primaryDiagnosis,
            status = AdmissionStatus.ACTIVE,
            syncStatus = SyncStatus.SYNCED
        )
        dao.insertAdmission(adm3)

        // Seed Initial Shift Handover Entries (SBAR + I-PASS)
        val today = todayString()
        dao.insertHandover(
            HandoverEntryEntity(
                id = "ho-p2-001",
                patientId = p2.id,
                uhid = p2.uhid,
                patientName = p2.name,
                ward = p2.ward,
                bedNumber = p2.bedNumber,
                shiftDate = today,
                shiftType = ShiftType.EVENING_TO_NIGHT,
                illnessSeverity = IllnessSeverity.UNSTABLE,
                situation = "58F Day 2 NSTEMI on dual antiplatelet therapy (Clopidogrel + Atorvastatin). Telemetry monitoring ongoing.",
                background = "PMH: Hypothyroidism on Eltroxin 50 mcg, Dyslipidemia. Allergy: Aspirin gastritis.",
                assessment = "Troponin I trending down (0.85 -> 0.45 -> 0.18 ng/mL), morning value awaits physician review. Mild exertional dyspnea earlier.",
                recommendationAndActions = "1. Review pending 09:00 Troponin I result.\n2. Continue continuous ECG telemetry.\n3. Cardiology cath-lab planning review tomorrow 08:30.",
                contingencyPlan = "IF chest pain recurs or ST depression deepens: obtain stat 12-lead ECG, give sublingual NTG if SBP > 100, and page Cardiology resident on call.",
                pendingCriticalItems = "Unreviewed Troponin I (0.18 ng/mL) • Echocardiogram report",
                outgoingClinician = "Dr. Ananya Ray",
                outgoingTime = "19:45",
                status = HandoverStatus.HANDED_OVER,
                syncStatus = SyncStatus.SYNCED
            )
        )

        dao.insertHandover(
            HandoverEntryEntity(
                id = "ho-p1-001",
                patientId = p1.id,
                uhid = p1.uhid,
                patientName = p1.name,
                ward = p1.ward,
                bedNumber = p1.bedNumber,
                shiftDate = today,
                shiftType = ShiftType.EVENING_TO_NIGHT,
                illnessSeverity = IllnessSeverity.WATCHER,
                situation = "46M recovering Community Acquired Pneumonia (RLL), weaned to Room Air (SpO2 98%). Discharge planned in 24h.",
                background = "T2DM + HTN (on Metformin & Telmisartan). Completed IV Ceftriaxone/Azithromycin, switched to oral Cefpodoxime.",
                assessment = "Afebrile x 36h, WBC 16.4 -> 9.2, CRP 85 -> 18. Blood culture 5-day final incubation pending.",
                recommendationAndActions = "1. Check automated blood culture clearance tomorrow morning.\n2. Finalize & print Discharge Summary PDF after morning rounds.",
                contingencyPlan = "IF fever > 100.4F or SpO2 drops < 94% on room air: restart nasal cannula O2 2L/min and review blood cultures.",
                pendingCriticalItems = "5-day Blood Culture clearance • Discharge Summary Sign-Off",
                outgoingClinician = "Dr. Ananya Ray",
                outgoingTime = "19:50",
                status = HandoverStatus.HANDED_OVER,
                syncStatus = SyncStatus.SYNCED
            )
        )

        dao.insertHandover(
            HandoverEntryEntity(
                id = "ho-p3-001",
                patientId = p3.id,
                uhid = p3.uhid,
                patientName = p3.name,
                ward = p3.ward,
                bedNumber = p3.bedNumber,
                shiftDate = today,
                shiftType = ShiftType.EVENING_TO_NIGHT,
                illnessSeverity = IllnessSeverity.STABLE,
                situation = "32M Post-op Day 1 Laparoscopic Appendectomy. Pain controlled, tolerating sips of clear liquids.",
                background = "No comorbidities, NKDA. Intra-op uncomplicated suppurative appendicitis.",
                assessment = "Hemodynamically stable, surgical port sites clean and dry, bowel sounds present.",
                recommendationAndActions = "1. Advance to soft diet if tolerating fluids.\n2. Encourage ambulation.\n3. Morning surgical dressing check.",
                contingencyPlan = "IF persistent vomiting or severe abdominal distension: hold oral intake and notify surgical resident.",
                pendingCriticalItems = "None urgent",
                outgoingClinician = "Dr. Ananya Ray",
                outgoingTime = "19:55",
                incomingClinician = "Dr. Vikram Seth, DM",
                acknowledgedAt = "$today 20:05",
                synthesisConfirmed = true,
                status = HandoverStatus.ACKNOWLEDGED,
                syncStatus = SyncStatus.SYNCED
            )
        )

        logAudit("SYSTEM", "database", "SEED", "Initialized synthetic demonstration patients, admissions, lab panels, and shift handovers.")
    }
}

