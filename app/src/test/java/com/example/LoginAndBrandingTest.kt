package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.UserRole
import com.example.data.preferences.LocalAuthManager
import com.example.data.preferences.LocalAuthMode
import com.example.data.repository.WardRoundRepository
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.WardRoundTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LoginAndBrandingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var authManager: LocalAuthManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("wardround_local_auth", Context.MODE_PRIVATE)
            .edit().clear().commit()
        authManager = LocalAuthManager(context)
    }

    @After
    fun tearDown() {
        authManager.signOut()
    }

    @Test
    fun `login screen renders WardRounds branding and all three auth options`() {
        composeTestRule.setContent {
            WardRoundTheme {
                LoginScreen(
                    selectedRole = UserRole.DOCTOR,
                    onRoleSelected = {},
                    localAuthManager = authManager
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("WardRounds").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_email_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_sign_in_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("guest_sign_in_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `email username sign in verifies password and rejects wrong password`() {
        val bad = authManager.signInWithCredentials("dr.ray", "wrongpass", UserRole.DOCTOR)
        assertTrue(bad.isFailure)
        assertNull(authManager.activeSession.value)

        composeTestRule.setContent {
            WardRoundTheme {
                LoginScreen(
                    selectedRole = UserRole.DOCTOR,
                    onRoleSelected = {},
                    localAuthManager = authManager
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("login_email_input").performScrollTo().performTextInput("dr.ray")
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("wardround123")
        composeTestRule.onNodeWithTag("email_sign_in_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val active = authManager.activeSession.value
        assertNotNull(active)
        assertEquals(LocalAuthMode.LOCAL_EMAIL, active?.mode)
        assertEquals("dr.ray@hospital.org", active?.identifier)
    }

    @Test
    fun `creating new local clinician account and guest login keep cloud sync disabled`() {
        val created = authManager.registerLocalAccount(
            rawIdentifier = "nurse.meera",
            rawDisplayName = "Dr. Meera Iyer",
            password = "securePassword99",
            role = UserRole.CONSULTANT
        )
        assertTrue(created.isSuccess)
        assertEquals("Dr. Meera Iyer", created.getOrNull()?.displayName)
        authManager.signOut()
        assertNull(authManager.activeSession.value)

        composeTestRule.setContent {
            WardRoundTheme {
                LoginScreen(
                    selectedRole = UserRole.ADMINISTRATOR,
                    onRoleSelected = {},
                    localAuthManager = authManager
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("guest_sign_in_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val guestSession = authManager.activeSession.value
        assertNotNull(guestSession)
        assertEquals(LocalAuthMode.GUEST, guestSession?.mode)
        assertEquals(UserRole.ADMINISTRATOR, guestSession?.role)

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = WardRoundRepository(dao = db.wardRoundDao(), scope = repoScope, firestoreSync = null)
            repo.activateLocalSession(
                sessionId = guestSession!!.sessionId,
                displayName = guestSession.displayName,
                identifier = guestSession.identifier,
                role = guestSession.role,
                department = guestSession.department,
                isGuest = true
            )
            assertFalse(repo.isCloudSyncEnabled)
            assertTrue(repo.isOfflineMode.value)
        } finally {
            repoScope.cancel()
            db.close()
        }
    }
}
