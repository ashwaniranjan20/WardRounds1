package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.DischargeStatus
import com.example.data.model.DischargeSummaryEntity
import com.example.data.model.HandoverEntryEntity
import com.example.data.model.HandoverStatus
import com.example.data.model.IllnessSeverity
import com.example.data.model.PatientEntity
import com.example.data.model.ShiftType
import com.example.data.model.UserRole
import com.example.data.model.VitalObservationEntity
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.WardRoundRepository
import com.example.domain.pdf.ClinicalPdfExporter
import com.example.ui.viewmodel.WardRoundViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import java.io.File
import java.io.OutputStream

@Implements(PdfDocument::class)
class TestShadowPdfDocument {
    @RealObject
    private lateinit var realPdfDocument: PdfDocument
    private var openPage: PdfDocument.Page? = null
    private var pagesFinished = 0
    private var isClosed = false

    @Implementation
    fun __constructor__() {
        // Bypass native libandroid_runtime PDFium JNI on JVM
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        check(!isClosed) { "document is closed!" }
        check(openPage == null) { "Previous page not finished!" }
        val bitmap = Bitmap.createBitmap(pageInfo.pageWidth, pageInfo.pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val ctor = PdfDocument.Page::class.java.getDeclaredConstructor(
            Canvas::class.java,
            PdfDocument.PageInfo::class.java
        )
        ctor.isAccessible = true
        val page = ctor.newInstance(canvas, pageInfo)
        openPage = page
        return page
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        check(!isClosed) { "document is closed!" }
        check(openPage === page) { "Invalid page passed to finishPage" }
        openPage = null
        pagesFinished++
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        check(!isClosed) { "document is closed!" }
        check(openPage == null) { "Current page not finished!" }
        check(pagesFinished > 0) { "No pages written!" }
        val minimalPdf = "%PDF-1.4\n% WardRound Test PDF ($pagesFinished pages)\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n"
        out.write(minimalPdf.toByteArray(Charsets.US_ASCII))
    }

    @Implementation
    fun close() {
        isClosed = true
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [TestShadowPdfDocument::class])
class HandoverAndPdfTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repoScope: CoroutineScope
    private lateinit var repository: WardRoundRepository

    @Before
    fun setup() {
        // Clear FileProvider's static sCache because Robolectric allocates a fresh temp cacheDir per test
        val sCacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        sCacheField.isAccessible = true
        val cacheMap = sCacheField.get(null) as? MutableMap<*, *>
        cacheMap?.clear()

        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        repository = WardRoundRepository(
            dao = database.wardRoundDao(),
            scope = repoScope
        )
    }

    @After
    fun tearDown() {
        repoScope.cancel()
        database.close()
    }

    private fun samplePatient(): PatientEntity = PatientEntity(
        id = "test-pt-01",
        uhid = "TEST-UHID-901",
        name = "Rahul Sharma",
        age = 46,
        sex = "Male",
        admissionNumber = "ADM-9001",
        ward = "Medicine Ward",
        bedNumber = "12",
        department = "Internal Medicine",
        consultant = "Dr. S. Mukherjee, MD",
        admissionDate = "2026-10-24",
        admissionTime = "09:30",
        primaryDiagnosis = "Community Acquired Pneumonia (Right Lower Lobe)",
        comorbidities = "Type 2 Diabetes Mellitus, Hypertension",
        allergies = "NKDA"
    )

    private fun assertValidPdfFile(file: File) {
        assertTrue("PDF file must exist", file.exists())
        assertTrue("PDF file must be non-empty", file.length() > 20L)
        val header = file.inputStream().use { stream ->
            val buf = ByteArray(5)
            stream.read(buf)
            String(buf, Charsets.US_ASCII)
        }
        assertEquals("%PDF-", header)
    }

    @Test
    fun `generateDischargeSummaryPdf produces valid A4 PDF for finalized summary`() = runBlocking {
        val patient = samplePatient()
        val summary = DischargeSummaryEntity(
            id = "ds-01",
            patientId = patient.id,
            admissionId = patient.admissionNumber,
            admissionDiagnosis = "Community Acquired Pneumonia",
            finalDiagnosis = "Community Acquired Pneumonia — Resolved",
            relevantClinicalCourse = "IV Ceftriaxone 2g OD x 3 days, weaned off O2 by Day 3.",
            importantExamFindings = "Chest bilateral air entry equal, no crepitations.",
            conditionAtDischarge = "Stable, Afebrile, SpO2 98% on room air",
            dischargeMedications = "Tab Cefuroxime 500mg BD x 5 days\nTab Paracetamol 650mg SOS",
            dietAdvice = "High-protein diabetic diet",
            activityAdvice = "Normal mobilization as tolerated",
            warningSigns = "Fever >101°F, worsening dyspnea, chest pain",
            urgentCareInstructions = "Report to Emergency Triage immediately",
            followUpDepartment = "Internal Medicine OPD",
            followUpDate = "2026-11-02",
            followUpInvestigations = "Repeat CBC, CRP",
            responsibleClinician = "Dr. Ananya Ray",
            dischargeStatus = DischargeStatus.DISCHARGED,
            isFinalized = true,
            finalizedAt = "2026-10-27 11:00"
        )

        val output = ClinicalPdfExporter.generateDischargeSummaryPdf(
            context = context,
            patient = patient,
            summary = summary,
            clinicianName = "Dr. Ananya Ray"
        )

        assertValidPdfFile(output.file)
        assertTrue(output.suggestedFileName.endsWith(".pdf"))
        assertTrue(output.uri.toString().contains("fileprovider"))
    }

    @Test
    fun `generateHandoverPacketPdf produces multi-section SBAR IPASS packet`() = runBlocking {
        val handovers = listOf(
            HandoverEntryEntity(
                id = "ho-1",
                patientId = "p2",
                uhid = "DEMO-00142",
                patientName = "Priya Patel",
                ward = "HDU / ICU",
                bedNumber = "04",
                shiftDate = "2026-10-27",
                shiftType = ShiftType.EVENING_TO_NIGHT,
                illnessSeverity = IllnessSeverity.UNSTABLE,
                situation = "COPD exacerbation on BiPAP support.",
                background = "62F COPD GOLD III, ex-smoker. Allergies: Penicillin.",
                assessment = "SpO2 91% on FiO2 40%, ABG pH 7.34, PaCO2 52 mmHg.",
                recommendationAndActions = "1. Check repeat ABG at 22:00.\n2. Continue Neb Duolin Q6H.",
                contingencyPlan = "IF pH < 7.25 or drowsy: prepare for intubation and call ICU consultant.",
                pendingCriticalItems = "STAT Repeat ABG",
                outgoingClinician = "Dr. Ananya Ray",
                outgoingTime = "20:00",
                status = HandoverStatus.HANDED_OVER
            ),
            HandoverEntryEntity(
                id = "ho-2",
                patientId = "p1",
                uhid = "DEMO-00125",
                patientName = "Rahul Sharma",
                ward = "Medicine Ward",
                bedNumber = "12",
                shiftDate = "2026-10-27",
                shiftType = ShiftType.EVENING_TO_NIGHT,
                illnessSeverity = IllnessSeverity.WATCHER,
                situation = "Day 4 CAP improving on Ceftriaxone.",
                background = "T2DM, HTN. Allergies: NKDA.",
                assessment = "CRP trending down 85 -> 42.5 -> 18 mg/L.",
                recommendationAndActions = "Review evening blood culture interim report.",
                contingencyPlan = "IF fever spike >101°F: repeat blood cultures.",
                pendingCriticalItems = "Blood culture 48h interim check",
                outgoingClinician = "Dr. Ananya Ray",
                outgoingTime = "20:00",
                incomingClinician = "Dr. K. Sen",
                acknowledgedAt = "2026-10-27 20:10",
                synthesisConfirmed = true,
                status = HandoverStatus.ACKNOWLEDGED
            )
        )

        val output = ClinicalPdfExporter.generateHandoverPacketPdf(
            context = context,
            shiftDate = "2026-10-27",
            shiftType = ShiftType.EVENING_TO_NIGHT,
            handovers = handovers,
            clinicianName = "Dr. Ananya Ray"
        )

        assertValidPdfFile(output.file)
        assertTrue(output.suggestedFileName.endsWith(".pdf"))
    }

    @Test
    fun `generateClinicalReportPdf produces valid PDF document`() = runBlocking {
        val reportText = buildString {
            appendLine("REPORT: DAILY WARD ROUND LIST")
            appendLine("Total Admitted Patients: 3")
            appendLine("1. Rahul Sharma (UHID: DEMO-00125)")
            appendLine("   Location: Medicine Ward — Bed 12")
            appendLine("   Diagnosis: Community Acquired Pneumonia")
        }

        val output = ClinicalPdfExporter.generateClinicalReportPdf(
            context = context,
            reportTitle = "Daily Ward-Round Register",
            bodyText = reportText,
            clinicianName = "Dr. Ananya Ray",
            department = "Internal Medicine"
        )

        assertValidPdfFile(output.file)
        assertTrue(output.suggestedFileName.startsWith("WardRound_"))
    }

    @Test
    fun `repository saveHandover and acknowledgeHandover persists signoff and audit trail`() = runBlocking {
        val handover = HandoverEntryEntity(
            id = "ho-test-signoff",
            patientId = "test-pt-01",
            uhid = "TEST-UHID-901",
            patientName = "Rahul Sharma",
            ward = "Medicine Ward",
            bedNumber = "12",
            shiftDate = "2026-10-27",
            shiftType = ShiftType.MORNING_TO_EVENING,
            illnessSeverity = IllnessSeverity.WATCHER,
            situation = "CAP Day 3 — afebrile",
            background = "T2DM, HTN",
            assessment = "BP 120/80, SpO2 98%",
            recommendationAndActions = "Review evening CRP",
            contingencyPlan = "IF SpO2 < 94%: escalate oxygen",
            pendingCriticalItems = "CRP repeat",
            outgoingClinician = "Dr. Ananya Ray",
            outgoingTime = "14:00",
            status = HandoverStatus.HANDED_OVER
        )

        repository.toggleOfflineMode(true)
        repository.saveHandover(handover)

        val savedList = repository.getAllHandovers().first()
        val inserted = savedList.find { it.id == "ho-test-signoff" }
        assertNotNull(inserted)
        assertEquals(HandoverStatus.HANDED_OVER, inserted?.status)

        repository.setDoctorUser("Dr. Vikram Mehta", UserRole.DOCTOR, "Internal Medicine")
        repository.acknowledgeHandover(
            handoverId = "ho-test-signoff",
            incomingClinician = "Dr. Vikram Mehta",
            synthesisConfirmed = true
        )

        val updatedList = repository.getAllHandovers().first()
        val acknowledged = updatedList.find { it.id == "ho-test-signoff" }
        assertNotNull(acknowledged)
        assertEquals(HandoverStatus.ACKNOWLEDGED, acknowledged?.status)
        assertEquals("Dr. Vikram Mehta", acknowledged?.incomingClinician)
        assertEquals(true, acknowledged?.synthesisConfirmed)
        assertNotNull(acknowledged?.acknowledgedAt)

        val audits = repository.getAllAuditLogs().first()
        assertTrue(audits.any { it.entityType == "HANDOVER" && it.entityId == "ho-test-signoff" && it.action == "ACKNOWLEDGED" })
    }

    @Test
    fun `viewModel buildAutoPopulatedHandoverDraft flags low SpO2 as UNSTABLE severity`() = runBlocking {
        repository.toggleOfflineMode(true)
        val patient = samplePatient()
        repository.registerPatient(patient)
        repository.recordVital(
            VitalObservationEntity(
                id = "vital-hypoxic-01",
                patientId = patient.id,
                admissionId = patient.admissionNumber,
                measurementDate = "2026-10-27",
                measurementTime = "18:00",
                bpSystolic = 104,
                bpDiastolic = 68,
                pulseRate = 116,
                spO2 = 88,
                oxygenDeliveryMethod = "Non-Rebreather Mask 10L/min"
            )
        )

        val viewModel = WardRoundViewModel(repository, ThemePreferences.getInstance(context))
        val draft = viewModel.buildAutoPopulatedHandoverDraft(patient, shiftDate = "2026-10-27")

        assertEquals(IllnessSeverity.UNSTABLE, draft.illnessSeverity)
        assertTrue(draft.assessment.contains("SpO2 88%"))
        assertTrue(draft.situation.contains("Community Acquired Pneumonia"))
        assertEquals(HandoverStatus.HANDED_OVER, draft.status)
    }

    @Test
    fun `room MIGRATION_1_2 creates handover_entries table cleanly`() {
        val db = database.openHelper.writableDatabase
        db.execSQL("DROP TABLE IF EXISTS `handover_entries`")
        AppDatabase.MIGRATION_1_2.migrate(db)

        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='handover_entries'")
        cursor.use {
            assertTrue("handover_entries table must exist after MIGRATION_1_2", it.moveToFirst())
        }
    }
}
