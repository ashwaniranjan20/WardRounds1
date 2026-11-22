package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ClinicalChangeClassification
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
import com.example.data.model.UserEntity
import com.example.data.model.UserRole
import com.example.data.model.VitalObservationEntity
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.LocalAuthMode
import com.example.data.preferences.LocalAuthSession
import com.example.data.preferences.ThemePreferences
import com.example.data.remote.FirestoreClinicalDoc
import com.example.data.repository.WardRoundRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class PatientFilter {
    ALL,
    NOT_REVIEWED,
    PENDING_INVESTIGATIONS,
    RESULTS_AWAITING_REVIEW,
    DISCHARGE_PLANNED
}

data class DashboardSummaryStats(
    val totalActivePatients: Int = 0,
    val reviewedToday: Int = 0,
    val pendingInvestigations: Int = 0,
    val resultsAwaitingReview: Int = 0,
    val notReviewedToday: Int = 0,
    val followUpsDueToday: Int = 0
)

sealed interface CloudDocsUiState {
    data object Loading : CloudDocsUiState
    data class Success(
        val docs: List<FirestoreClinicalDoc>,
        val patientCount: Int
    ) : CloudDocsUiState
    data class Error(val message: String) : CloudDocsUiState
}

class WardRoundViewModel(
    private val repository: WardRoundRepository,
    private val themePreferences: ThemePreferences? = null
) : ViewModel() {

    private val _fallbackThemeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = themePreferences?.themeMode ?: _fallbackThemeMode.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        if (themePreferences != null) {
            themePreferences.setThemeMode(mode)
        } else {
            _fallbackThemeMode.value = mode
        }
    }

    val currentUser: StateFlow<UserEntity> = repository.currentUser
    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus
    val isOfflineMode: StateFlow<Boolean> = repository.isOfflineMode
    val syncMessage: StateFlow<String> = repository.syncMessage
    val isCloudSyncEnabled: Boolean
        get() = repository.isCloudSyncEnabled

    val cloudDocsState: StateFlow<CloudDocsUiState> = combine(
        repository.observeCloudDocs(),
        repository.observeCloudPatients()
    ) { docs, patients ->
        CloudDocsUiState.Success(
            docs = docs,
            patientCount = patients.size
        ) as CloudDocsUiState
    }
        .catch { e ->
            if (e is CancellationException) throw e
            val message = when (val cause = e as? FirebaseFirestoreException) {
                null -> e.localizedMessage ?: "Failed to load cloud clinical documentation"
                else -> if (cause.message?.contains("index", ignoreCase = true) == true) {
                    "Index building — wait 2-5 min"
                } else {
                    cause.localizedMessage ?: "Cloud sync error (${cause.code})"
                }
            }
            emit(CloudDocsUiState.Error(message))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CloudDocsUiState.Loading
        )

    val activePatients: StateFlow<List<PatientEntity>> = repository.getActivePatients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPatients: StateFlow<List<PatientEntity>> = repository.getAllPatients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dischargedPatients: StateFlow<List<PatientEntity>> = repository.getDischargedPatients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOrders: StateFlow<List<InvestigationOrderEntity>> = repository.getAllOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allResults: StateFlow<List<InvestigationResultEntity>> = repository.getAllResults()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val resultsAwaitingReview: StateFlow<List<InvestigationResultEntity>> = repository.getResultsAwaitingReview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFollowUpTasks: StateFlow<List<FollowUpTaskEntity>> = repository.getAllFollowUpTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHandovers: StateFlow<List<HandoverEntryEntity>> = repository.getAllHandovers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingHandoversCount: StateFlow<Int> = allHandovers
        .combine(MutableStateFlow(Unit)) { list, _ ->
            list.count { it.status == HandoverStatus.HANDED_OVER }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingOrdersCount: StateFlow<Int> = repository.getPendingOrdersCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val awaitingReviewCount: StateFlow<Int> = repository.getAwaitingReviewCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Today's Rounds list for date verification
    private val todayString: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    val todayRounds: StateFlow<List<RoundEntryEntity>> = repository.getRoundsForDate(todayString)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard Filter
    private val _currentFilter = MutableStateFlow(PatientFilter.ALL)
    val currentFilter: StateFlow<PatientFilter> = _currentFilter.asStateFlow()

    // Search Query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setFilter(filter: PatientFilter) {
        _currentFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val filterAndQuery = combine(_currentFilter, _searchQuery) { f, q -> Pair(f, q) }

    // Filtered patients for Home Dashboard
    val dashboardPatients: StateFlow<List<PatientEntity>> = combine(
        activePatients,
        todayRounds,
        allOrders,
        allResults,
        filterAndQuery
    ) { patients, rounds, orders, results, filterQuery ->
        val (filter, query) = filterQuery
        val reviewedPatientIds = rounds.map { it.patientId }.toSet()
        val pendingOrderPatientIds = orders
            .filter { it.currentStatus !in listOf(InvestigationStatus.RESULT_REVIEWED, InvestigationStatus.COMPLETED, InvestigationStatus.CANCELLED) }
            .map { it.patientId }.toSet()
        val unreviewedResultPatientIds = results
            .filter { !it.isReviewed }
            .map { it.patientId }.toSet()

        val matchingSearch = if (query.isBlank()) {
            patients
        } else {
            val q = query.lowercase(Locale.US)
            patients.filter {
                it.name.lowercase(Locale.US).contains(q) ||
                        it.uhid.lowercase(Locale.US).contains(q) ||
                        it.ward.lowercase(Locale.US).contains(q) ||
                        it.bedNumber.lowercase(Locale.US).contains(q) ||
                        it.primaryDiagnosis.lowercase(Locale.US).contains(q)
            }
        }

        when (filter) {
            PatientFilter.ALL -> matchingSearch
            PatientFilter.NOT_REVIEWED -> matchingSearch.filter { it.id !in reviewedPatientIds }
            PatientFilter.PENDING_INVESTIGATIONS -> matchingSearch.filter { it.id in pendingOrderPatientIds }
            PatientFilter.RESULTS_AWAITING_REVIEW -> matchingSearch.filter { it.id in unreviewedResultPatientIds }
            PatientFilter.DISCHARGE_PLANNED -> matchingSearch.filter { it.primaryDiagnosis.contains("Discharge", ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard Summary Metrics
    val dashboardStats: StateFlow<DashboardSummaryStats> = combine(
        activePatients,
        todayRounds,
        pendingOrdersCount,
        awaitingReviewCount,
        allFollowUpTasks
    ) { active, rounds, pendingOrders, unreviewedResults, tasks ->
        val reviewedCount = active.count { pt -> rounds.any { it.patientId == pt.id } }
        val dueTodayTasks = tasks.count { it.status == TaskStatus.DUE_TODAY || it.dueDate == todayString }
        DashboardSummaryStats(
            totalActivePatients = active.size,
            reviewedToday = reviewedCount,
            pendingInvestigations = pendingOrders,
            resultsAwaitingReview = unreviewedResults,
            notReviewedToday = active.size - reviewedCount,
            followUpsDueToday = dueTodayTasks
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummaryStats())

    // Patient Profile Specific Flow Getters
    fun getPatient(patientId: String) = repository.getPatientById(patientId)
    fun getRoundsForPatient(patientId: String) = repository.getRoundsForPatient(patientId)
    fun getVitalsForPatient(patientId: String) = repository.getVitalsForPatient(patientId)
    fun getNotesForPatient(patientId: String) = repository.getNotesForPatient(patientId)
    fun getOrdersForPatient(patientId: String) = repository.getOrdersForPatient(patientId)
    fun getResultsForPatient(patientId: String) = repository.getResultsForPatient(patientId)
    fun getTrendsForPatient(patientId: String) = repository.getTrendsForPatient(patientId)
    fun getDischargeSummaryForPatient(patientId: String) = repository.getDischargeSummaryForPatient(patientId)
    fun getFollowUpTasksForPatient(patientId: String) = repository.getFollowUpTasksForPatient(patientId)
    fun getEventsForOrder(orderId: String) = repository.getEventsForOrder(orderId)

    // Hospital Day Calculation
    fun calculateHospitalDay(admissionDateString: String, targetDateString: String = todayString): Int {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val admDate = format.parse(admissionDateString)
            val curDate = format.parse(targetDateString)
            if (admDate != null && curDate != null) {
                val diffMillis = curDate.time - admDate.time
                val days = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
                maxOf(1, days + 1)
            } else {
                1
            }
        } catch (e: Exception) {
            1
        }
    }

    // Check duplicate UHID
    suspend fun isUhidRegistered(uhid: String): Boolean {
        return repository.getPatientByUhid(uhid.trim()) != null
    }

    suspend fun findDuplicateUhidOwner(uhid: String, excludePatientId: String): String? {
        val trimmed = uhid.trim()
        if (trimmed.isBlank()) return null
        return repository.getPatientByUhidExcluding(trimmed, excludePatientId)?.name
    }

    // Actions
    fun registerPatient(
        patient: PatientEntity,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val existing = repository.getPatientByUhid(patient.uhid.trim())
                if (existing != null) {
                    onError("A patient with UHID ${patient.uhid} already exists in the ward database (${existing.name}).")
                    return@launch
                }
                val id = repository.registerPatient(patient)
                onSuccess(id)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to register patient")
            }
        }
    }

    fun updatePatient(patient: PatientEntity) {
        viewModelScope.launch {
            repository.updatePatient(patient)
        }
    }

    fun saveEditedPatient(
        patient: PatientEntity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (patient.name.isBlank() || patient.uhid.isBlank()) {
                    onError("Patient Name and UHID are mandatory.")
                    return@launch
                }
                repository.updatePatient(patient)
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to save patient updates")
            }
        }
    }

    fun saveRound(
        round: RoundEntryEntity,
        vitals: VitalObservationEntity? = null,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repository.saveRoundEntry(round, vitals)
            onSuccess(id)
        }
    }

    fun recordVitals(vital: VitalObservationEntity) {
        viewModelScope.launch {
            repository.recordVital(vital)
        }
    }

    fun addClinicalNote(note: ClinicalNoteEntity, onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.saveClinicalNote(note)
            onSuccess(id)
        }
    }

    fun placeInvestigationOrder(order: InvestigationOrderEntity, onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.placeInvestigationOrder(order)
            onSuccess(id)
        }
    }

    fun advanceOrderWorkflow(
        order: InvestigationOrderEntity,
        newStatus: InvestigationStatus,
        comments: String = ""
    ) {
        viewModelScope.launch {
            repository.advanceInvestigationWorkflow(
                order = order,
                newStatus = newStatus,
                responsiblePerson = currentUser.value.name,
                comments = comments
            )
        }
    }

    fun saveInvestigationResult(result: InvestigationResultEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveInvestigationResult(result)
            onSuccess()
        }
    }

    fun reviewResult(resultId: String) {
        viewModelScope.launch {
            repository.reviewInvestigationResult(resultId, currentUser.value.name)
        }
    }

    fun saveDischargeSummary(summary: DischargeSummaryEntity) {
        viewModelScope.launch {
            repository.saveDischargeSummary(summary)
        }
    }

    fun saveFollowUpTask(task: FollowUpTaskEntity) {
        viewModelScope.launch {
            repository.saveFollowUpTask(task)
        }
    }

    fun completeFollowUpTask(taskId: String) {
        viewModelScope.launch {
            repository.completeFollowUpTask(taskId)
        }
    }

    // --- Shift Handover Operations ---
    fun getHandoversForPatient(patientId: String) = repository.getHandoversForPatient(patientId)

    fun saveHandover(handover: HandoverEntryEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveHandover(handover)
            onSuccess()
        }
    }

    fun acknowledgeHandover(
        handoverId: String,
        synthesisConfirmed: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.acknowledgeHandover(
                handoverId = handoverId,
                incomingClinician = currentUser.value.name,
                synthesisConfirmed = synthesisConfirmed
            )
            onSuccess()
        }
    }

    fun deleteHandover(handoverId: String) {
        viewModelScope.launch {
            repository.deleteHandover(handoverId)
        }
    }

    suspend fun buildAutoPopulatedHandoverDraft(
        patient: PatientEntity,
        shiftDate: String = todayString,
        shiftType: ShiftType = ShiftType.EVENING_TO_NIGHT
    ): HandoverEntryEntity {
        val latestRound = repository.getLatestRoundForPatient(patient.id)
        val latestVital = repository.getLatestVitalOnce(patient.id)
        val orders = repository.getOrdersForPatient(patient.id).firstOrNull().orEmpty()
        val results = repository.getResultsForPatient(patient.id).firstOrNull().orEmpty()
        val tasks = repository.getFollowUpTasksForPatient(patient.id).firstOrNull().orEmpty()

        val pendingOrders = orders.filter {
            it.currentStatus !in listOf(
                InvestigationStatus.RESULT_REVIEWED,
                InvestigationStatus.COMPLETED,
                InvestigationStatus.CANCELLED
            )
        }
        val unreviewedResults = results.filter { !it.isReviewed }
        val openTasks = tasks.filter { it.status != TaskStatus.COMPLETED && it.status != TaskStatus.CANCELLED }

        val vitalsSnippet = latestVital?.let { v ->
            val parts = mutableListOf<String>()
            if (v.bpSystolic != null && v.bpDiastolic != null) parts.add("BP ${v.bpSystolic}/${v.bpDiastolic}")
            if (v.pulseRate != null) parts.add("HR ${v.pulseRate} bpm")
            if (v.spO2 != null) parts.add("SpO2 ${v.spO2}% (${v.oxygenDeliveryMethod})")
            if (v.temperatureF != null) parts.add("Temp ${v.temperatureF}°F")
            parts.joinToString(", ")
        }.orEmpty()

        val hasAbnormalUnreviewed = unreviewedResults.any { it.isAbnormal || it.isCritical }
        val derivedSeverity = when {
            latestVital?.spO2 != null && latestVital.spO2 < 92 -> IllnessSeverity.UNSTABLE
            latestRound?.currentCondition?.contains("critical", ignoreCase = true) == true -> IllnessSeverity.UNSTABLE
            hasAbnormalUnreviewed || pendingOrders.any { it.priority == InvestigationPriority.STAT } -> IllnessSeverity.WATCHER
            else -> IllnessSeverity.STABLE
        }

        val day = calculateHospitalDay(patient.admissionDate, shiftDate)
        val ageSex = "${patient.age ?: "--"}${patient.sex?.firstOrNull() ?: ""}"
        val situationText = buildString {
            append("$ageSex Day $day, ${patient.primaryDiagnosis}.")
            if (latestRound != null && latestRound.currentCondition.isNotBlank()) {
                append(" Current status: ${latestRound.currentCondition}.")
            }
        }

        val backgroundText = buildString {
            if (patient.comorbidities.isNotBlank()) append("PMH: ${patient.comorbidities}. ")
            append("Allergies: ${patient.allergies.ifBlank { "NKDA" }}.")
            if (latestRound != null && latestRound.treatmentChanges.isNotBlank()) {
                append(" Treatment: ${latestRound.treatmentChanges}")
            }
        }

        val assessmentText = buildString {
            if (vitalsSnippet.isNotBlank()) append("Vitals: $vitalsSnippet. ")
            if (latestRound != null && latestRound.clinicalAssessment.isNotBlank()) {
                append(latestRound.clinicalAssessment)
            }
            if (unreviewedResults.isNotEmpty()) {
                val resSummary = unreviewedResults.take(3).joinToString("; ") {
                    "${it.parameterName}: ${it.originalTextValue} ${it.unit}"
                }
                append(" Unreviewed results: $resSummary.")
            }
        }

        val actionsText = buildString {
            val items = mutableListOf<String>()
            if (latestRound != null && latestRound.monitoringPlan.isNotBlank()) {
                items.add(latestRound.monitoringPlan)
            }
            openTasks.forEach { items.add("${it.taskType}: ${it.description} (Due ${it.dueDate})") }
            if (items.isEmpty()) append("Continue routine ward monitoring and morning round review.")
            else items.forEachIndexed { idx, s -> append("${idx + 1}. $s\n") }
        }.trim()

        val pendingItems = buildList {
            if (pendingOrders.isNotEmpty()) add("${pendingOrders.size} pending lab/imaging order(s): ${pendingOrders.take(2).joinToString { it.investigationName }}")
            if (unreviewedResults.isNotEmpty()) add("${unreviewedResults.size} unreviewed result(s)")
        }.joinToString(" • ").ifBlank { "None urgent" }

        val nowTime = SimpleDateFormat("HH:mm", Locale.US).format(Date())

        return HandoverEntryEntity(
            patientId = patient.id,
            uhid = patient.uhid,
            patientName = patient.name,
            ward = patient.ward,
            bedNumber = patient.bedNumber,
            shiftDate = shiftDate,
            shiftType = shiftType,
            illnessSeverity = derivedSeverity,
            situation = situationText.trim(),
            background = backgroundText.trim(),
            assessment = assessmentText.trim().ifBlank { "Stable on current ward regimen." },
            recommendationAndActions = actionsText,
            contingencyPlan = "IF clinical deterioration, fever spike >101°F, or SpO2 <92%: perform bedside reassessment, repeat vitals, and notify on-call physician.",
            pendingCriticalItems = pendingItems,
            outgoingClinician = currentUser.value.name,
            outgoingTime = nowTime,
            status = HandoverStatus.HANDED_OVER
        )
    }

    fun seedMissingShiftHandovers(
        shiftDate: String = todayString,
        shiftType: ShiftType = ShiftType.EVENING_TO_NIGHT,
        onComplete: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val active = activePatients.value
            val existingForShift = allHandovers.value.filter {
                it.shiftDate == shiftDate && it.shiftType == shiftType
            }.map { it.patientId }.toSet()

            var created = 0
            for (pt in active) {
                if (pt.id !in existingForShift) {
                    val draft = buildAutoPopulatedHandoverDraft(pt, shiftDate, shiftType)
                    repository.saveHandover(draft)
                    created++
                }
            }
            onComplete(created)
        }
    }

    fun toggleOfflineMode(enabled: Boolean) {
        repository.toggleOfflineMode(enabled)
    }

    fun triggerSync() {
        repository.triggerSync()
    }

    fun switchUser(name: String, role: UserRole, department: String) {
        repository.setDoctorUser(name, role, department)
    }

    fun syncFirebaseClinician(uid: String, displayName: String?, email: String?) {
        repository.syncFirebaseClinician(uid, displayName, email)
    }

    fun activateLocalSession(session: LocalAuthSession) {
        repository.activateLocalSession(
            sessionId = session.sessionId,
            displayName = session.displayName,
            identifier = session.identifier,
            role = session.role,
            department = session.department,
            isGuest = session.mode == LocalAuthMode.GUEST
        )
    }

    fun clearCloudSession() {
        repository.clearCloudSession()
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetDatabase()
        }
    }
}

