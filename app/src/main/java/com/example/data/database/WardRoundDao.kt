package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AdmissionEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClinicalNoteEntity
import com.example.data.model.DischargeSummaryEntity
import com.example.data.model.FollowUpTaskEntity
import com.example.data.model.HandoverEntryEntity
import com.example.data.model.InvestigationEventEntity
import com.example.data.model.InvestigationOrderEntity
import com.example.data.model.InvestigationResultEntity
import com.example.data.model.PatientEntity
import com.example.data.model.RoundEntryEntity
import com.example.data.model.UserEntity
import com.example.data.model.VitalObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WardRoundDao {

    // --- Users ---
    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUser(): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // --- Patients ---
    @Query("SELECT * FROM patients ORDER BY ward ASC, bedNumber ASC")
    fun getAllPatients(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE isDischarged = 0 ORDER BY ward ASC, bedNumber ASC")
    fun getActivePatients(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE isDischarged = 1 ORDER BY dischargeDate DESC")
    fun getDischargedPatients(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE id = :id LIMIT 1")
    fun getPatientById(id: String): Flow<PatientEntity?>

    @Query("SELECT * FROM patients WHERE id = :id LIMIT 1")
    suspend fun getPatientByIdOnce(id: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE LOWER(uhid) = LOWER(:uhid) LIMIT 1")
    suspend fun getPatientByUhid(uhid: String): PatientEntity?

    @Query("SELECT * FROM patients WHERE LOWER(uhid) = LOWER(:uhid) AND id != :excludePatientId LIMIT 1")
    suspend fun getPatientByUhidExcluding(uhid: String, excludePatientId: String): PatientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: PatientEntity)

    @Update
    suspend fun updatePatient(patient: PatientEntity)

    @Delete
    suspend fun deletePatient(patient: PatientEntity)

    // --- Admissions ---
    @Query("SELECT * FROM admissions WHERE patientId = :patientId ORDER BY admissionDate DESC")
    fun getAdmissionsForPatient(patientId: String): Flow<List<AdmissionEntity>>

    @Query("SELECT * FROM admissions WHERE patientId = :patientId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveAdmissionForPatient(patientId: String): AdmissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmission(admission: AdmissionEntity)

    @Update
    suspend fun updateAdmission(admission: AdmissionEntity)

    // --- Round Entries ---
    @Query("SELECT * FROM round_entries WHERE patientId = :patientId ORDER BY roundDate DESC, roundTime DESC")
    fun getRoundsForPatient(patientId: String): Flow<List<RoundEntryEntity>>

    @Query("SELECT * FROM round_entries WHERE id = :id LIMIT 1")
    fun getRoundById(id: String): Flow<RoundEntryEntity?>

    @Query("SELECT * FROM round_entries WHERE patientId = :patientId ORDER BY roundDate DESC, roundTime DESC LIMIT 1")
    suspend fun getLatestRoundForPatient(patientId: String): RoundEntryEntity?

    @Query("SELECT * FROM round_entries WHERE roundDate = :todayDate")
    fun getRoundsForDate(todayDate: String): Flow<List<RoundEntryEntity>>

    @Query("SELECT * FROM round_entries ORDER BY roundDate DESC, roundTime DESC")
    suspend fun getAllRoundsOnce(): List<RoundEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRound(round: RoundEntryEntity)

    @Update
    suspend fun updateRound(round: RoundEntryEntity)

    // --- Vital Observations ---
    @Query("SELECT * FROM vital_observations WHERE patientId = :patientId ORDER BY measurementDate DESC, measurementTime DESC")
    fun getVitalsForPatient(patientId: String): Flow<List<VitalObservationEntity>>

    @Query("SELECT * FROM vital_observations WHERE patientId = :patientId ORDER BY measurementDate DESC, measurementTime DESC LIMIT 1")
    fun getLatestVitalForPatient(patientId: String): Flow<VitalObservationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVital(vital: VitalObservationEntity)

    // --- Clinical Notes ---
    @Query("SELECT * FROM clinical_notes WHERE patientId = :patientId ORDER BY date DESC, time DESC")
    fun getNotesForPatient(patientId: String): Flow<List<ClinicalNoteEntity>>

    @Query("SELECT * FROM clinical_notes ORDER BY date DESC, time DESC")
    suspend fun getAllNotesOnce(): List<ClinicalNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: ClinicalNoteEntity)

    @Update
    suspend fun updateNote(note: ClinicalNoteEntity)

    // --- Investigation Orders ---
    @Query("SELECT * FROM investigation_orders ORDER BY orderDate DESC, orderTime DESC")
    fun getAllOrders(): Flow<List<InvestigationOrderEntity>>

    @Query("SELECT * FROM investigation_orders WHERE patientId = :patientId ORDER BY orderDate DESC, orderTime DESC")
    fun getOrdersForPatient(patientId: String): Flow<List<InvestigationOrderEntity>>

    @Query("SELECT * FROM investigation_orders WHERE id = :id LIMIT 1")
    fun getOrderById(id: String): Flow<InvestigationOrderEntity?>

    @Query("SELECT COUNT(*) FROM investigation_orders WHERE currentStatus NOT IN ('RESULT_REVIEWED', 'COMPLETED', 'CANCELLED')")
    fun getPendingOrdersCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM investigation_orders WHERE patientId = :patientId AND currentStatus NOT IN ('RESULT_REVIEWED', 'COMPLETED', 'CANCELLED')")
    fun getPendingOrdersCountForPatient(patientId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: InvestigationOrderEntity)

    @Update
    suspend fun updateOrder(order: InvestigationOrderEntity)

    // --- Investigation Events ---
    @Query("SELECT * FROM investigation_events WHERE orderId = :orderId ORDER BY timestamp ASC")
    fun getEventsForOrder(orderId: String): Flow<List<InvestigationEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: InvestigationEventEntity)

    // --- Investigation Results ---
    @Query("SELECT * FROM investigation_results ORDER BY measurementDate DESC, measurementTime DESC")
    fun getAllResults(): Flow<List<InvestigationResultEntity>>

    @Query("SELECT * FROM investigation_results WHERE patientId = :patientId ORDER BY measurementDate DESC, measurementTime DESC")
    fun getResultsForPatient(patientId: String): Flow<List<InvestigationResultEntity>>

    @Query("SELECT * FROM investigation_results WHERE isReviewed = 0 ORDER BY measurementDate DESC, measurementTime DESC")
    fun getResultsAwaitingReview(): Flow<List<InvestigationResultEntity>>

    @Query("SELECT COUNT(*) FROM investigation_results WHERE isReviewed = 0")
    fun getAwaitingReviewCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: InvestigationResultEntity)

    @Update
    suspend fun updateResult(result: InvestigationResultEntity)

    // --- Discharge Summaries ---
    @Query("SELECT * FROM discharge_summaries WHERE patientId = :patientId LIMIT 1")
    fun getDischargeSummaryForPatient(patientId: String): Flow<DischargeSummaryEntity?>

    @Query("SELECT * FROM discharge_summaries")
    suspend fun getAllDischargeSummariesOnce(): List<DischargeSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDischargeSummary(summary: DischargeSummaryEntity)

    @Update
    suspend fun updateDischargeSummary(summary: DischargeSummaryEntity)

    // --- Follow-up Tasks ---
    @Query("SELECT * FROM follow_up_tasks ORDER BY dueDate ASC, priority DESC")
    fun getAllFollowUpTasks(): Flow<List<FollowUpTaskEntity>>

    @Query("SELECT * FROM follow_up_tasks WHERE patientId = :patientId ORDER BY dueDate ASC")
    fun getFollowUpTasksForPatient(patientId: String): Flow<List<FollowUpTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowUpTask(task: FollowUpTaskEntity)

    @Update
    suspend fun updateFollowUpTask(task: FollowUpTaskEntity)

    // --- Audit Logs ---
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    // --- Bulk Clear (for database restore/sync) ---
    @Query("DELETE FROM patients")
    suspend fun clearPatients()

    @Query("DELETE FROM admissions")
    suspend fun clearAdmissions()

    @Query("DELETE FROM round_entries")
    suspend fun clearRounds()

    @Query("DELETE FROM vital_observations")
    suspend fun clearVitals()

    @Query("DELETE FROM clinical_notes")
    suspend fun clearNotes()

    @Query("DELETE FROM investigation_orders")
    suspend fun clearOrders()

    @Query("DELETE FROM investigation_events")
    suspend fun clearEvents()

    @Query("DELETE FROM investigation_results")
    suspend fun clearResults()

    @Query("DELETE FROM discharge_summaries")
    suspend fun clearDischargeSummaries()

    // --- Shift Handover Entries ---
    @Query("SELECT * FROM handover_entries ORDER BY shiftDate DESC, lastUpdated DESC")
    fun getAllHandovers(): Flow<List<HandoverEntryEntity>>

    @Query("SELECT * FROM handover_entries WHERE patientId = :patientId ORDER BY shiftDate DESC, lastUpdated DESC")
    fun getHandoversForPatient(patientId: String): Flow<List<HandoverEntryEntity>>

    @Query("SELECT * FROM handover_entries WHERE id = :id LIMIT 1")
    suspend fun getHandoverByIdOnce(id: String): HandoverEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHandover(handover: HandoverEntryEntity)

    @Update
    suspend fun updateHandover(handover: HandoverEntryEntity)

    @Query("DELETE FROM handover_entries WHERE id = :id")
    suspend fun deleteHandover(id: String)

    @Query("DELETE FROM follow_up_tasks")
    suspend fun clearFollowUpTasks()

    @Query("DELETE FROM handover_entries")
    suspend fun clearHandovers()
}
