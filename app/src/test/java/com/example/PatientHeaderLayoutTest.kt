package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.PatientEntity
import com.example.data.preferences.ThemePreferences
import com.example.data.repository.WardRoundRepository
import com.example.ui.screens.PatientProfileScreen
import com.example.ui.theme.WardRoundTheme
import com.example.ui.viewmodel.WardRoundViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PatientHeaderLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repoScope: CoroutineScope
    private lateinit var repository: WardRoundRepository
    private lateinit var viewModel: WardRoundViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        repository = WardRoundRepository(
            dao = database.wardRoundDao(),
            scope = repoScope
        )
        viewModel = WardRoundViewModel(repository, ThemePreferences.getInstance(context))
    }

    @After
    fun tearDown() {
        repoScope.cancel()
        database.close()
    }

    @Test
    fun `long diagnosis box stays compact initially and toggles cleanly on tap`() {
        val longDiagnosis = (1..20).joinToString(" ") { idx ->
            "Condition #$idx: Community Acquired Bacterial Pneumonia with Parapneumonic Effusion, Type 2 Diabetes Mellitus, Essential Hypertension, Chronic Kidney Disease Stage 3b,"
        }

        val patient = PatientEntity(
            id = "long-dx-pt",
            uhid = "TEST-LONG-001",
            name = "Arjun Nair",
            age = 58,
            sex = "Male",
            admissionNumber = "ADM-9911",
            ward = "Medicine Ward",
            bedNumber = "08",
            department = "Internal Medicine",
            consultant = "Dr. Ray",
            admissionDate = "2026-11-18",
            admissionTime = "08:45",
            primaryDiagnosis = longDiagnosis
        )

        runBlocking {
            repository.toggleOfflineMode(true)
            repository.registerPatient(patient)
        }

        composeTestRule.setContent {
            WardRoundTheme {
                PatientProfileScreen(
                    patientId = patient.id,
                    initialTab = 0,
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToDailyRound = {},
                    onNavigateToDischarge = {},
                    onNavigateToHandover = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Lower Overview tab content must remain clearly visible below header
        composeTestRule.onNodeWithText("Current Vital Signs").assertIsDisplayed()

        // Diagnosis box should be bounded compactly (~48-85dp) despite massive text
        val collapsedBounds = composeTestRule.onNodeWithTag("patient_header_diagnosis_box").getBoundsInRoot()
        val collapsedHeight = collapsedBounds.bottom - collapsedBounds.top
        assertTrue("Collapsed diagnosis height ($collapsedHeight) should be <= 96.dp", collapsedHeight <= 96.dp)

        // Tap to expand
        composeTestRule.onNodeWithTag("patient_header_diagnosis_box").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Collapse full diagnosis").assertIsDisplayed()
        val expandedBounds = composeTestRule.onNodeWithTag("patient_header_diagnosis_box").getBoundsInRoot()
        val expandedHeight = expandedBounds.bottom - expandedBounds.top
        assertTrue("Expanded diagnosis height ($expandedHeight) must stay bounded <= 160.dp", expandedHeight <= 160.dp)
        assertTrue("Expanded diagnosis should grow compared to collapsed", expandedHeight > collapsedHeight)

        // Tap again to collapse back
        composeTestRule.onNodeWithTag("patient_header_diagnosis_box").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Expand full diagnosis").assertIsDisplayed()

        // Scroll lower Overview tab content upward and verify header collapses smoothly
        val initialTabBounds = composeTestRule.onNodeWithText("Overview").getBoundsInRoot()
        composeTestRule.onNodeWithText("Current Vital Signs").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        val scrolledTabBounds = composeTestRule.onNodeWithText("Overview").getBoundsInRoot()
        assertTrue(
            "Scrolling Overview list upward should collapse top header so tabs move higher (${scrolledTabBounds.top} < ${initialTabBounds.top})",
            scrolledTabBounds.top < initialTabBounds.top
        )
    }
}
