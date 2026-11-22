package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.ClinicalNoteEntity
import com.example.data.model.DischargeSummaryEntity
import com.example.data.model.HandoverEntryEntity
import com.example.data.model.HandoverStatus
import com.example.data.model.PatientEntity
import com.example.data.model.RoundEntryEntity
import com.example.data.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreClinicalSync(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id)),
        auth = Firebase.auth
    )

    val currentUserId: String?
        get() = auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    private fun requireUserId(): String =
        currentUserId ?: throw IllegalStateException("Clinician must be signed in before accessing cloud storage")

    private fun sanitizeDocId(rawId: String): String {
        val cleaned = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return cleaned.ifBlank { UUID.randomUUID().toString() }.take(128)
    }

    private fun patientsCollection(uid: String) =
        db.collection("users").document(uid).collection("patients")

    private fun clinicalNotesCollection(uid: String) =
        db.collection("users").document(uid).collection("clinical_notes")

    fun observePatients(userId: String): Flow<List<FirestorePatientRecord>> {
        if (userId.isBlank()) return flowOf(emptyList())
        val path = "users/$userId/patients"
        return patientsCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        FirestorePatientRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    FirestoreErrorHandler.handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeClinicalDocs(userId: String): Flow<List<FirestoreClinicalDoc>> {
        if (userId.isBlank()) return flowOf(emptyList())
        val path = "users/$userId/clinical_notes"
        return clinicalNotesCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        FirestoreClinicalDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    FirestoreErrorHandler.handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun upsertClinicianProfile(
        displayName: String,
        role: UserRole,
        department: String
    ): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(IllegalStateException("Clinician not authenticated"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val profile = FirestoreClinicianProfile(
                userId = uid,
                displayName = displayName,
                role = role.name,
                department = department
            )
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(profile.toUpdateMap()).await()
            } else {
                docRef.set(profile.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertPatient(patient: PatientEntity): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(IllegalStateException("Clinician not authenticated"))
        val safeId = sanitizeDocId(patient.id)
        val path = "users/$uid/patients/$safeId"
        return try {
            val docRef = patientsCollection(uid).document(safeId)
            val record = FirestorePatientRecord(
                id = safeId,
                userId = uid,
                uhid = patient.uhid,
                name = patient.name,
                age = patient.age,
                sex = patient.sex,
                ward = patient.ward,
                bedNumber = patient.bedNumber,
                department = patient.department,
                consultant = patient.consultant,
                admissionDate = patient.admissionDate,
                primaryDiagnosis = patient.primaryDiagnosis,
                allergies = patient.allergies,
                discharged = patient.isDischarged,
                dischargeDate = patient.dischargeDate
            )
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(record.toCreateMap()).await()
            } else {
                val alreadyDischarged = snap.getBoolean("discharged") == true
                if (!alreadyDischarged) {
                    if (patient.isDischarged) {
                        docRef.update(record.toDischargeUpdateMap()).await()
                    } else {
                        docRef.update(record.toProfileUpdateMap()).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertClinicalDoc(
        docId: String,
        doc: FirestoreClinicalDoc
    ): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(IllegalStateException("Clinician not authenticated"))
        val safeDocId = sanitizeDocId(docId)
        val safePatientId = sanitizeDocId(doc.patientId)
        val path = "users/$uid/clinical_notes/$safeDocId"
        return try {
            val noteRef = clinicalNotesCollection(uid).document(safeDocId)
            val normalizedDoc = doc.copy(
                id = safeDocId,
                userId = uid,
                patientId = safePatientId
            )
            val snap = noteRef.get().await()
            if (!snap.exists()) {
                noteRef.set(normalizedDoc.toCreateMap()).await()
            } else {
                val remoteStatus = snap.getString("docStatus") ?: "ACTIVE"
                if (remoteStatus != "FINALIZED" && remoteStatus != "ACKNOWLEDGED") {
                    noteRef.update(normalizedDoc.toUpdateMap()).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncAllFromRoom(
        clinicianName: String,
        clinicianRole: UserRole,
        clinicianDepartment: String,
        patients: List<PatientEntity>,
        rounds: List<RoundEntryEntity>,
        notes: List<ClinicalNoteEntity>,
        discharges: List<DischargeSummaryEntity>,
        handovers: List<HandoverEntryEntity>
    ): Result<Int> {
        val uid = try {
            requireUserId()
        } catch (e: IllegalStateException) {
            return Result.failure(e)
        }

        upsertClinicianProfile(clinicianName, clinicianRole, clinicianDepartment)
            .onFailure { return Result.failure(it) }

        var syncedCount = 0
        val patientsById = patients.associateBy { it.id }

        for (patient in patients) {
            upsertPatient(patient).onFailure { return Result.failure(it) }
            syncedCount++
        }

        for (round in rounds) {
            val parent = patientsById[round.patientId] ?: continue
            val doc = FirestoreClinicalDoc(
                userId = uid,
                patientId = sanitizeDocId(parent.id),
                docCategory = "DAILY_ROUND",
                title = "Day ${round.hospitalDay} ${round.roundType.name} Round — ${parent.name}".take(240),
                clinicalText = buildString {
                    if (round.currentDiagnosis.isNotBlank()) appendLine("Diagnosis: ${round.currentDiagnosis}")
                    if (round.currentCondition.isNotBlank()) appendLine("Condition: ${round.currentCondition}")
                    if (round.clinicalAssessment.isNotBlank()) appendLine("Assessment: ${round.clinicalAssessment}")
                    if (round.medicationPlan.isNotBlank()) appendLine("Plan: ${round.medicationPlan}")
                    if (round.dailySummary.isNotBlank()) append("Summary: ${round.dailySummary}")
                }.ifBlank { "Ward round recorded." },
                authorName = round.doctorName,
                ward = round.ward,
                docDate = round.roundDate,
                docStatus = if (round.isDraft) "DRAFT" else "FINALIZED"
            )
            upsertClinicalDoc("round_${round.id}", doc).onFailure { return Result.failure(it) }
            syncedCount++
        }

        for (note in notes) {
            val parent = patientsById[note.patientId] ?: continue
            val doc = FirestoreClinicalDoc(
                userId = uid,
                patientId = sanitizeDocId(parent.id),
                docCategory = "CLINICAL_NOTE",
                title = note.title.ifBlank { "${note.noteType} — ${parent.name}" },
                clinicalText = note.content,
                authorName = note.author,
                ward = parent.ward,
                docDate = note.date,
                docStatus = if (note.isDraft) "DRAFT" else "ACTIVE"
            )
            upsertClinicalDoc("note_${note.id}", doc).onFailure { return Result.failure(it) }
            syncedCount++
        }

        for (summary in discharges) {
            val parent = patientsById[summary.patientId] ?: continue
            val doc = FirestoreClinicalDoc(
                userId = uid,
                patientId = sanitizeDocId(parent.id),
                docCategory = "DISCHARGE_SUMMARY",
                title = "Discharge Summary — ${parent.name} (${parent.uhid})",
                clinicalText = buildString {
                    appendLine("Final Diagnosis: ${summary.finalDiagnosis.ifBlank { parent.primaryDiagnosis }}")
                    if (summary.relevantClinicalCourse.isNotBlank()) appendLine("Course: ${summary.relevantClinicalCourse}")
                    if (summary.dischargeMedications.isNotBlank()) appendLine("Medications: ${summary.dischargeMedications}")
                    if (summary.followUpDate.isNotBlank()) append("Follow-up: ${summary.followUpDate} (${summary.followUpDepartment})")
                },
                authorName = summary.responsibleClinician,
                ward = parent.ward,
                docDate = summary.finalizedAt?.take(10) ?: parent.dischargeDate ?: parent.admissionDate,
                docStatus = if (summary.isFinalized) "FINALIZED" else "DRAFT"
            )
            upsertClinicalDoc("discharge_${summary.id}", doc).onFailure { return Result.failure(it) }
            syncedCount++
        }

        for (handover in handovers) {
            val parent = patientsById[handover.patientId] ?: continue
            val mappedStatus = when (handover.status) {
                HandoverStatus.ACKNOWLEDGED -> "ACKNOWLEDGED"
                HandoverStatus.DRAFT -> "DRAFT"
                HandoverStatus.HANDED_OVER -> "ACTIVE"
            }
            val doc = FirestoreClinicalDoc(
                userId = uid,
                patientId = sanitizeDocId(parent.id),
                docCategory = "HANDOVER",
                title = "${handover.shiftType.label} [${handover.illnessSeverity.label}] — ${handover.patientName}",
                clinicalText = buildString {
                    appendLine("S: ${handover.situation}")
                    appendLine("B: ${handover.background}")
                    appendLine("A: ${handover.assessment}")
                    appendLine("R: ${handover.recommendationAndActions}")
                    if (handover.contingencyPlan.isNotBlank()) appendLine("Contingency: ${handover.contingencyPlan}")
                    if (!handover.incomingClinician.isNullOrBlank()) {
                        append("Signed off by: ${handover.incomingClinician} at ${handover.acknowledgedAt.orEmpty()}")
                    }
                },
                authorName = handover.outgoingClinician.ifBlank { clinicianName },
                ward = handover.ward,
                docDate = handover.shiftDate,
                docStatus = mappedStatus
            )
            upsertClinicalDoc("handover_${handover.id}", doc).onFailure { return Result.failure(it) }
            syncedCount++
        }

        return Result.success(syncedCount)
    }
}
