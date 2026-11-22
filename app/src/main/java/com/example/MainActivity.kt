package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.database.AppDatabase
import com.example.data.model.UserRole
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.LocalAuthManager
import com.example.data.preferences.LocalAuthSession
import com.example.data.preferences.ThemePreferences
import com.example.data.remote.FirestoreClinicalSync
import com.example.data.repository.WardRoundRepository
import com.example.ui.components.NavSection
import com.example.ui.components.WardRoundBottomNav
import com.example.ui.navigation.Screen
import com.example.ui.screens.AddPatientScreen
import com.example.ui.screens.DailyRoundScreen
import com.example.ui.screens.DischargePlanningScreen
import com.example.ui.screens.EditPatientScreen
import com.example.ui.screens.FollowUpScreen
import com.example.ui.screens.HandoverScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InvestigationMainScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PatientListScreen
import com.example.ui.screens.PatientProfileScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DeepRoyalNavy
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftCloudWhite
import com.example.ui.theme.WardRoundTheme
import com.example.ui.viewmodel.WardRoundViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    trySend(currentUser)
    awaitClose { removeAuthStateListener(listener) }
}

private sealed interface AuthBootState {
    data object Loading : AuthBootState
    data object SignedOut : AuthBootState
    data class CloudSignedIn(val user: FirebaseUser) : AuthBootState
    data class LocalSignedIn(val session: LocalAuthSession) : AuthBootState
}

private data class AppRuntimeDeps(
    val database: AppDatabase,
    val firestoreSync: FirestoreClinicalSync,
    val themePreferences: ThemePreferences,
    val localAuthManager: LocalAuthManager
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as? WardRoundApplication

        setContent {
            val runtimeDeps by produceState<AppRuntimeDeps?>(initialValue = null) {
                value = withContext(Dispatchers.IO) {
                    if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                        FirebaseApp.initializeApp(applicationContext)
                    }
                    val database = app?.database ?: AppDatabase.getDatabase(applicationContext)
                    database.wardRoundDao()
                    val firestoreSync = app?.firestoreSync ?: FirestoreClinicalSync(applicationContext)
                    val themePrefs = app?.themePreferences ?: ThemePreferences.getInstance(applicationContext)
                    val localAuth = app?.localAuthManager ?: LocalAuthManager.getInstance(applicationContext)
                    AppRuntimeDeps(
                        database = database,
                        firestoreSync = firestoreSync,
                        themePreferences = themePrefs,
                        localAuthManager = localAuth
                    )
                }
            }

            val loadedDeps = runtimeDeps
            val themeMode = if (loadedDeps != null) {
                loadedDeps.themePreferences.themeMode.collectAsStateWithLifecycle().value
            } else {
                AppThemeMode.SYSTEM
            }

            val authState by produceState<AuthBootState>(
                initialValue = AuthBootState.Loading,
                key1 = loadedDeps
            ) {
                if (loadedDeps == null) {
                    value = AuthBootState.Loading
                    return@produceState
                }
                val auth = withContext(Dispatchers.IO) {
                    if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                        FirebaseApp.initializeApp(applicationContext)
                    }
                    Firebase.auth
                }
                combine(
                    auth.authStateFlow(),
                    loadedDeps.localAuthManager.activeSession
                ) { firebaseUser, localSession ->
                    when {
                        firebaseUser != null -> AuthBootState.CloudSignedIn(firebaseUser)
                        localSession != null -> AuthBootState.LocalSignedIn(localSession)
                        else -> AuthBootState.SignedOut
                    }
                }.collect { merged ->
                    value = merged
                }
            }

            var pendingSelectedRole by remember { mutableStateOf(UserRole.DOCTOR) }

            WardRoundTheme(themeMode = themeMode) {
                val currentAuth = authState
                if (loadedDeps == null || currentAuth is AuthBootState.Loading) {
                    WardRoundLaunchSplash()
                } else {
                    when (currentAuth) {
                        is AuthBootState.Loading -> WardRoundLaunchSplash()
                        is AuthBootState.SignedOut -> {
                            LoginScreen(
                                selectedRole = pendingSelectedRole,
                                onRoleSelected = { pendingSelectedRole = it },
                                localAuthManager = loadedDeps.localAuthManager
                            )
                        }
                        is AuthBootState.CloudSignedIn -> {
                            val signedInUser = currentAuth.user
                            key("cloud_${signedInUser.uid}") {
                                val userScopedViewModel: WardRoundViewModel = viewModel(
                                    key = "cloud_${signedInUser.uid}",
                                    factory = object : ViewModelProvider.Factory {
                                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                            val repository = WardRoundRepository(
                                                dao = loadedDeps.database.wardRoundDao(),
                                                firestoreSync = loadedDeps.firestoreSync
                                            )
                                            @Suppress("UNCHECKED_CAST")
                                            return WardRoundViewModel(repository, loadedDeps.themePreferences) as T
                                        }
                                    }
                                )

                                LaunchedEffect(signedInUser.uid) {
                                    userScopedViewModel.syncFirebaseClinician(
                                        uid = signedInUser.uid,
                                        displayName = signedInUser.displayName,
                                        email = signedInUser.email
                                    )
                                    if (pendingSelectedRole != UserRole.DOCTOR) {
                                        val resolvedName = signedInUser.displayName?.takeIf { it.isNotBlank() }
                                            ?: userScopedViewModel.currentUser.value.name
                                        val dept = if (pendingSelectedRole == UserRole.ADMINISTRATOR) {
                                            "Clinical Governance"
                                        } else {
                                            userScopedViewModel.currentUser.value.department
                                        }
                                        userScopedViewModel.switchUser(resolvedName, pendingSelectedRole, dept)
                                    }
                                    userScopedViewModel.triggerSync()
                                }

                                WardRoundApp(viewModel = userScopedViewModel)
                            }
                        }
                        is AuthBootState.LocalSignedIn -> {
                            val localSession = currentAuth.session
                            key("local_${localSession.sessionId}") {
                                val localViewModel: WardRoundViewModel = viewModel(
                                    key = "local_${localSession.sessionId}",
                                    factory = object : ViewModelProvider.Factory {
                                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                            val repository = WardRoundRepository(
                                                dao = loadedDeps.database.wardRoundDao(),
                                                firestoreSync = null
                                            )
                                            @Suppress("UNCHECKED_CAST")
                                            return WardRoundViewModel(repository, loadedDeps.themePreferences) as T
                                        }
                                    }
                                )

                                LaunchedEffect(localSession) {
                                    localViewModel.activateLocalSession(localSession)
                                }

                                WardRoundApp(viewModel = localViewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WardRoundLaunchSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftCloudWhite),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DeepRoyalNavy),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalHospital,
                    contentDescription = "WardRounds",
                    tint = PureWhite,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "WardRounds",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DeepRoyalNavy
            )
            Spacer(modifier = Modifier.height(12.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = DeepRoyalNavy
            )
        }
    }
}

@Composable
fun WardRoundApp(viewModel: WardRoundViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val pendingLabsCount by viewModel.pendingOrdersCount.collectAsStateWithLifecycle()

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Patients.route,
        Screen.Investigations.route,
        Screen.FollowUps.route,
        Screen.Settings.route
    )

    val currentSection = when (currentRoute) {
        Screen.Home.route -> NavSection.ROUNDS
        Screen.Patients.route -> NavSection.PATIENTS
        Screen.Investigations.route -> NavSection.INVESTIGATIONS
        Screen.FollowUps.route -> NavSection.FOLLOW_UPS
        Screen.Settings.route -> NavSection.MORE
        else -> NavSection.ROUNDS
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                WardRoundBottomNav(
                    selectedSection = currentSection,
                    pendingLabsCount = pendingLabsCount,
                    onSectionSelected = { section ->
                        val targetRoute = when (section) {
                            NavSection.ROUNDS -> Screen.Home.route
                            NavSection.PATIENTS -> Screen.Patients.route
                            NavSection.INVESTIGATIONS -> Screen.Investigations.route
                            NavSection.FOLLOW_UPS -> Screen.FollowUps.route
                            NavSection.MORE -> Screen.Settings.route
                        }
                        if (currentRoute != targetRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else androidx.compose.ui.unit.Dp.Hairline)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToPatient = { patientId ->
                            navController.navigate("patient/$patientId?tab=0")
                        },
                        onNavigateToAddPatient = {
                            navController.navigate(Screen.AddPatient.route)
                        },
                        onNavigateToHandover = {
                            navController.navigate(Screen.Handover.createRoute())
                        },
                        onNavigateToReports = {
                            navController.navigate(Screen.Reports.route)
                        },
                        onNavigateToEditPatient = { patientId ->
                            navController.navigate(Screen.EditPatient.createRoute(patientId))
                        }
                    )
                }

                composable(Screen.Patients.route) {
                    PatientListScreen(
                        viewModel = viewModel,
                        onNavigateToPatient = { patientId ->
                            navController.navigate("patient/$patientId?tab=0")
                        },
                        onNavigateToAddPatient = {
                            navController.navigate(Screen.AddPatient.route)
                        },
                        onNavigateToEditPatient = { patientId ->
                            navController.navigate(Screen.EditPatient.createRoute(patientId))
                        }
                    )
                }

                composable(Screen.AddPatient.route) {
                    AddPatientScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onPatientRegistered = { patientId ->
                            navController.navigate("patient/$patientId?tab=0") {
                                popUpTo(Screen.AddPatient.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Investigations.route) {
                    InvestigationMainScreen(
                        viewModel = viewModel,
                        onNavigateToPatient = { patientId ->
                            navController.navigate("patient/$patientId?tab=4")
                        }
                    )
                }

                composable(Screen.FollowUps.route) {
                    FollowUpScreen(
                        viewModel = viewModel,
                        onNavigateToPatient = { patientId ->
                            navController.navigate("patient/$patientId?tab=8")
                        }
                    )
                }

                composable(Screen.Reports.route) {
                    ReportsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateToHandover = {
                            navController.navigate(Screen.Handover.createRoute())
                        },
                        onNavigateToReports = {
                            navController.navigate(Screen.Reports.route)
                        }
                    )
                }

                composable(
                    route = Screen.Handover.route,
                    arguments = listOf(
                        navArgument("patientId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val patientId = backStackEntry.arguments?.getString("patientId")
                    HandoverScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToPatient = { ptId ->
                            navController.navigate("patient/$ptId?tab=0")
                        },
                        initialPatientId = patientId
                    )
                }

                composable(
                    route = "patient/{patientId}?tab={initialTab}",
                    arguments = listOf(
                        navArgument("patientId") { type = NavType.StringType },
                        navArgument("initialTab") {
                            type = NavType.IntType
                            defaultValue = 0
                        }
                    )
                ) { backStackEntry ->
                    val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
                    val initialTab = backStackEntry.arguments?.getInt("initialTab") ?: 0
                    PatientProfileScreen(
                        patientId = patientId,
                        initialTab = initialTab,
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToDailyRound = { ptId ->
                            navController.navigate("patient/$ptId/round_entry")
                        },
                        onNavigateToDischarge = { ptId ->
                            navController.navigate("patient/$ptId/discharge")
                        },
                        onNavigateToHandover = { ptId ->
                            navController.navigate(Screen.Handover.createRoute(ptId))
                        },
                        onNavigateToEditPatient = { ptId ->
                            navController.navigate(Screen.EditPatient.createRoute(ptId))
                        }
                    )
                }

                composable(
                    route = Screen.EditPatient.route,
                    arguments = listOf(
                        navArgument("patientId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
                    EditPatientScreen(
                        patientId = patientId,
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onPatientUpdated = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "patient/{patientId}/round_entry",
                    arguments = listOf(
                        navArgument("patientId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
                    DailyRoundScreen(
                        patientId = patientId,
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "patient/{patientId}/discharge",
                    arguments = listOf(
                        navArgument("patientId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
                    DischargePlanningScreen(
                        patientId = patientId,
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
