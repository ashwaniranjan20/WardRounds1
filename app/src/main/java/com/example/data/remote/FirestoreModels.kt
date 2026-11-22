package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FirestoreClinicianProfile(
    val userId: String = "",
    val displayName: String = "",
    val role: String = "DOCTOR",
    val department: String = "Internal Medicine",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.ifBlank { "Duty Clinician" }.take(120),
        "role" to role.ifBlank { "DOCTOR" },
        "department" to department.ifBlank { "Internal Medicine" }.take(120),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "displayName" to displayName.ifBlank { "Duty Clinician" }.take(120),
        "role" to role.ifBlank { "DOCTOR" },
        "department" to department.ifBlank { "Internal Medicine" }.take(120),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FirestorePatientRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val uhid: String = "",
    val name: String = "",
    val age: Int? = null,
    val sex: String? = null,
    val ward: String = "",
    val bedNumber: String = "",
    val department: String = "Internal Medicine",
    val consultant: String = "",
    val admissionDate: String = "",
    val primaryDiagnosis: String = "",
    val allergies: String = "NKDA",
    val discharged: Boolean = false,
    val dischargeDate: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "uhid" to uhid.ifBlank { "UHID-UNKNOWN" }.take(64),
        "name" to name.ifBlank { "Unnamed Patient" }.take(120),
        "age" to age?.coerceIn(0, 150),
        "sex" to sex?.take(24),
        "ward" to ward.take(80),
        "bedNumber" to bedNumber.take(32),
        "department" to department.take(80),
        "consultant" to consultant.take(120),
        "admissionDate" to admissionDate.take(32),
        "primaryDiagnosis" to primaryDiagnosis.take(1000),
        "allergies" to allergies.ifBlank { "NKDA" }.take(500),
        "discharged" to discharged,
        "dischargeDate" to dischargeDate?.take(32),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

    fun toProfileUpdateMap(): Map<String, Any> = mapOf(
        "uhid" to uhid.ifBlank { "UHID-UNKNOWN" }.take(64),
        "name" to name.ifBlank { "Unnamed Patient" }.take(120),
        "age" to age?.coerceIn(0, 150),
        "sex" to sex?.take(24),
        "ward" to ward.take(80),
        "bedNumber" to bedNumber.take(32),
        "department" to department.take(80),
        "consultant" to consultant.take(120),
        "admissionDate" to admissionDate.take(32),
        "primaryDiagnosis" to primaryDiagnosis.take(1000),
        "allergies" to allergies.ifBlank { "NKDA" }.take(500),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

    fun toDischargeUpdateMap(): Map<String, Any> = mapOf(
        "discharged" to true,
        "dischargeDate" to (dischargeDate?.take(32) ?: ""),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FirestoreClinicalDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val patientId: String = "",
    val docCategory: String = "CLINICAL_NOTE",
    val title: String = "",
    val clinicalText: String = "",
    val authorName: String = "",
    val ward: String = "",
    val docDate: String = "",
    val docStatus: String = "ACTIVE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "patientId" to patientId,
        "docCategory" to docCategory,
        "title" to title.ifBlank { "Clinical Entry" }.take(240),
        "clinicalText" to clinicalText.take(8000),
        "authorName" to authorName.take(120),
        "ward" to ward.take(80),
        "docDate" to docDate.take(32),
        "docStatus" to docStatus,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.ifBlank { "Clinical Entry" }.take(240),
        "clinicalText" to clinicalText.take(8000),
        "authorName" to authorName.take(120),
        "ward" to ward.take(80),
        "docDate" to docDate.take(32),
        "docStatus" to docStatus,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
