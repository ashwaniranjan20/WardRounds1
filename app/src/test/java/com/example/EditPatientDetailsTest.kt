package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.AdmissionEntity
import com.example.data.model.AdmissionStatus
import com.example.data.model.PatientEntity
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.WardRoundRepository
import com.example.ui.components.PatientCard
import com.example.ui.screens.EditPatientScreen
import com.example.ui.screens.PatientProfileScreen
import com.example.ui.theme.WardRoundTheme
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditPatientDetailsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repoScope: CoroutineScope
    private lateinit var repository: WardRoundRepository
    private lateinit var viewModel: WardRoundViewModel

    private val primaryPatient = PatientEntity(
        id = "pt-edit-1",
        uhid = "WR-1001",
        name = "Kavita Menon",
        age = 46,
        sex = "Female",
        admissionNumber = "ADM-5001",
        ward = "Medicine Ward A",
        bedNumber = "04",
        department = "Internal Medicine",
        consultant = "Dr. S. Mukherjee, MD",
        admissionDate = "2026-11-20",
        primaryDiagnosis = "Acute Pyelonephritis",
        provisionalDiagnosis = "Rule out renal abscess",
        comorbidities = "T2DM",
        allergies = "NKDA",
        pastMedicalHistory = "Hypothyroidism",
        pastSurgicalHistory = "Appendectomy 2014",
        contactInfo = "+91-9811112222"
    )

    private val secondaryPatient = PatientEntity(
        id = "pt-edit-2",
        uhid = "WR-2002",
        name = "Rohan Das",
        age = 62,
        sex = "Male",
        ward = "ICU",
        bedNumber = "02",
        admissionDate = "2026-11-19",
        primaryDiagnosis = "COPD Exacerbation"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        repository = WardRoundRepository(
            dao = database.wardRoundDao(),
            scope = repoScope,
            firestoreSync = null
        )
        viewModel = WardRoundViewModel(repository, ThemePreferences.getInstance(context))

        runBlocking {
            repository.toggleOfflineMode(true)
            val dao = database.wardRoundDao()
            dao.insertPatient(primaryPatient)
            dao.insertPatient(secondaryPatient)
            dao.insertAdmission(
                AdmissionEntity(
                    id = "adm-edit-1",
                    patientId = primaryPatient.id,
                    uhid = primaryPatient.uhid,
                    admissionNumber = primaryPatient.admissionNumber,
                    admissionDate = primaryPatient.admissionDate,
                    ward = primaryPatient.ward,
                    bedNumber = primaryPatient.bedNumber,
                    admissionDiagnosis = primaryPatient.primaryDiagnosis,
                    status = AdmissionStatus.ACTIVE
                )
            )
        }
    }

    @After
    fun tearDown() {
        repoScope.cancel()
        database.close()
    }

    @Test
    fun `editing full patient details updates Room patient and active admission`() {
        var updatedCalled = false

        composeTestRule.setContent {
            WardRoundTheme {
                EditPatientScreen(
                    patientId = primaryPatient.id,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onPatientUpdated = { updatedCalled = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("edit_patient_name_input").performScrollTo().performTextReplacement("Kavita R. Menon")
        composeTestRule.onNodeWithTag("edit_patient_age_input").performScrollTo().performTextReplacement("47")
        composeTestRule.onNodeWithTag("edit_patient_contact_input").performScrollTo().performTextReplacement("+91-9900011223")
        composeTestRule.onNodeWithTag("edit_patient_ward_input").performScrollTo().performTextReplacement("Step-Down ICU")
        composeTestRule.onNodeWithTag("edit_patient_bed_input").performScrollTo().performTextReplacement("11B")
        composeTestRule.onNodeWithTag("edit_patient_diagnosis_input").performScrollTo().performTextReplacement("Complicated UTI with Sepsis Recovery")
        composeTestRule.onNodeWithTag("edit_patient_allergies_input").performScrollTo().performTextReplacement("Penicillin (rash)")
        composeTestRule.onNodeWithTag("edit_patient_pmh_input").performScrollTo().performTextReplacement("T2DM, Hypothyroidism")
        composeTestRule.onNodeWithTag("edit_patient_psh_input").performScrollTo().performTextReplacement("Appendectomy 2014, Lap Cholecystectomy")
        composeTestRule.onNodeWithTag("edit_patient_save_button").performScrollTo().performClick()

        waitForCondition { updatedCalled }
        assertEquals(true, updatedCalled)

        runBlocking {
            val saved = database.wardRoundDao().getPatientByIdOnce(primaryPatient.id)
            assertNotNull(saved)
            assertEquals("Kavita R. Menon", saved?.name)
            assertEquals(47, saved?.age)
            assertEquals("+91-9900011223", saved?.contactInfo)
            assertEquals("Step-Down ICU", saved?.ward)
            assertEquals("11B", saved?.bedNumber)
            assertEquals("Complicated UTI with Sepsis Recovery", saved?.primaryDiagnosis)
            assertEquals("Penicillin (rash)", saved?.allergies)
            assertEquals("T2DM, Hypothyroidism", saved?.pastMedicalHistory)
            assertEquals("Appendectomy 2014, Lap Cholecystectomy", saved?.pastSurgicalHistory)

            val activeAdm = database.wardRoundDao().getActiveAdmissionForPatient(primaryPatient.id)
            assertEquals("Step-Down ICU", activeAdm?.ward)
            assertEquals("11B", activeAdm?.bedNumber)
            assertEquals("Complicated UTI with Sepsis Recovery", activeAdm?.admissionDiagnosis)
        }
    }

    @Test
    fun `duplicate UHID warns without blocking save while blank name blocks save`() {
        var saveCompleted = false

        composeTestRule.setContent {
            WardRoundTheme {
                EditPatientScreen(
                    patientId = primaryPatient.id,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onPatientUpdated = { saveCompleted = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Blank name should show validation error and block save
        composeTestRule.onNodeWithTag("edit_patient_name_input").performScrollTo().performTextReplacement("")
        composeTestRule.onNodeWithTag("edit_patient_save_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_validation_error").performScrollTo().assertIsDisplayed()
        assertEquals(false, saveCompleted)

        // Restore name and set UHID to secondaryPatient's UHID -> warns, but allows saving
        composeTestRule.onNodeWithTag("edit_patient_name_input").performScrollTo().performTextReplacement("Kavita Menon")
        composeTestRule.onNodeWithTag("edit_patient_uhid_input").performScrollTo().performTextReplacement(secondaryPatient.uhid)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("edit_duplicate_uhid_warning").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_patient_save_button").performScrollTo().performClick()
        waitForCondition { saveCompleted }

        assertEquals(true, saveCompleted)
        runBlocking {
            val saved = database.wardRoundDao().getPatientByIdOnce(primaryPatient.id)
            assertEquals(secondaryPatient.uhid, saved?.uhid)
            val all = database.wardRoundDao().getAllPatients().first()
            assertEquals(2, all.size)
        }
    }

    private fun waitForCondition(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            if (!condition()) {
                Thread.sleep(25)
            }
        }
    }

    @Test
    fun `edit entry point on PatientCard invokes navigation callback`() {
        var editedFromCard: String? = null

        composeTestRule.setContent {
            WardRoundTheme {
                PatientCard(
                    patient = primaryPatient,
                    onEditClick = { editedFromCard = primaryPatient.id },
                    onClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("patient_card_edit_${primaryPatient.uhid}").performClick()
        composeTestRule.waitForIdle()
        assertEquals(primaryPatient.id, editedFromCard)
    }

    @Test
    fun `edit entry points on PatientProfileHeader invoke navigation callback`() {
        var editTapCount = 0
        var lastEditedPatientId: String? = null

        composeTestRule.setContent {
            WardRoundTheme {
                PatientProfileScreen(
                    patientId = primaryPatient.id,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToDailyRound = {},
                    onNavigateToDischarge = {},
                    onNavigateToEditPatient = {
                        lastEditedPatientId = it
                        editTapCount++
                    }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("patient_header_edit_button").performClick()
        composeTestRule.onNodeWithTag("quick_action_edit_patient").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(primaryPatient.id, lastEditedPatientId)
        assertEquals(2, editTapCount)
    }
}
