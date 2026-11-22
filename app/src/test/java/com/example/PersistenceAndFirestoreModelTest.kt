package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.PatientEntity
import com.example.data.remote.FirestoreClinicalDoc
import com.example.data.remote.FirestoreClinicianProfile
import com.example.data.remote.FirestorePatientRecord
import com.google.firebase.firestore.FieldValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersistenceAndFirestoreModelTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `room database initializes clinical tables`() {
        val sqlite = db.openHelper.writableDatabase
        val expectedTables = listOf(
            "patients",
            "admissions",
            "round_entries",
            "vital_observations",
            "clinical_notes",
            "investigation_orders",
            "investigation_events",
            "investigation_results",
            "discharge_summaries",
            "follow_up_tasks",
            "audit_logs",
            "handover_entries"
        )
        for (table in expectedTables) {
            sqlite.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { cursor ->
                assertTrue("Expected Room table $table to exist", cursor.moveToFirst())
            }
        }
    }

    @Test
    fun `firebase_applet_config defines custom firestore_database_id`() {
        val dbId = context.getString(R.string.firestore_database_id)
        assertTrue("firestore_database_id must not be blank", dbId.isNotBlank())
        assertFalse("firestore_database_id must not be (default)", dbId == "(default)")
    }

    @Test
    fun `firestore patient map omits document id and maps boolean without is prefix`() {
        val patient = PatientEntity(
            id = "pt-001",
            uhid = "UHID-100",
            name = "Rahul Sharma",
            age = 46,
            sex = "Male",
            ward = "Medicine Ward",
            bedNumber = "12",
            department = "Internal Medicine",
            consultant = "Dr. S. Mukherjee, MD",
            admissionDate = "2026-10-24",
            primaryDiagnosis = "Community Acquired Pneumonia",
            allergies = "NKDA",
            isDischarged = false
        )
        val record = FirestorePatientRecord(
            id = patient.id,
            userId = "uid-123",
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

        val map = record.toCreateMap()
        assertFalse("Document ID 'id' must not be serialized in create map", map.containsKey("id"))
        assertFalse("Room 'isDischarged' must map to 'discharged'", map.containsKey("isDischarged"))
        assertEquals(false, map["discharged"])
        assertEquals(FieldValue.serverTimestamp(), map["createdAt"])
        assertEquals(FieldValue.serverTimestamp(), map["updatedAt"])
    }

    @Test
    fun `firestore clinical doc and clinician profile maps match security rules keys`() {
        val profileMap = FirestoreClinicianProfile(
            userId = "uid-123",
            displayName = "Dr. Ananya Ray",
            role = "DOCTOR",
            department = "Internal Medicine"
        ).toCreateMap()
        assertFalse(profileMap.containsKey("id"))
        assertEquals("uid-123", profileMap["userId"])
        assertEquals("DOCTOR", profileMap["role"])

        val docMap = FirestoreClinicalDoc(
            id = "doc-1",
            userId = "uid-123",
            patientId = "pt-001",
            docCategory = "DAILY_ROUND",
            title = "Day 1 Round",
            clinicalText = "Stable vitals",
            authorName = "Dr. Ananya Ray",
            ward = "Medicine Ward",
            docDate = "2026-10-24",
            docStatus = "FINALIZED"
        ).toCreateMap()
        assertFalse(docMap.containsKey("id"))
        assertEquals("DAILY_ROUND", docMap["docCategory"])
        assertEquals("FINALIZED", docMap["docStatus"])
    }
}
