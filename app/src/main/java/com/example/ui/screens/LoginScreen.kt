package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.model.UserRole
import com.example.data.preferences.LocalAuthManager
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DeepRoyalNavy
import com.example.ui.theme.MutedSurface
import com.example.ui.theme.PeriwinkleBlue
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SoftCloudWhite
import com.example.ui.theme.StatusAlert
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val AUTH_TAG = "Auth"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun LoginScreen(
    selectedRole: UserRole,
    onRoleSelected: (UserRole) -> Unit,
    localAuthManager: LocalAuthManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isGoogleLoading by remember { mutableStateOf(false) }
    var isCreateAccountMode by remember { mutableStateOf(false) }
    var emailOrUsername by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun authenticateWithGoogleCredential(credential: androidx.credentials.Credential) {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            localAuthManager.signOut()
            Firebase.auth.signInWithCredential(firebaseCredential).await()
        } else {
            throw IllegalStateException("Unexpected credential type received from Google Identity.")
        }
    }

    // Attempt silent Google sign-in after first frame commit only when no local session is active.
    LaunchedEffect(Unit) {
        if (localAuthManager.activeSession.value != null) return@LaunchedEffect
        withFrameNanos { }
        delay(350)
        val activity = context.findActivity() ?: return@LaunchedEffect
        val clientId = context.getString(R.string.default_web_client_id)
        if (clientId.isBlank()) return@LaunchedEffect

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            authenticateWithGoogleCredential(result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoCredentialException) {
            Log.d(AUTH_TAG, "No previously authorized Google account available for silent sign-in")
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Silent Google Sign-In prompt cancelled", e)
        } catch (e: Exception) {
            Log.d(AUTH_TAG, "Silent Google Sign-In skipped: ${e.localizedMessage}")
        }
    }

    fun launchInteractiveGoogleSignIn() {
        val activity = context.findActivity()
        if (activity == null) {
            errorMessage = "Unable to access host activity for Google Sign-In."
            return
        }
        val clientId = context.getString(R.string.default_web_client_id)
        if (clientId.isBlank()) {
            errorMessage = "Google OAuth web client ID is missing from configuration."
            return
        }

        isGoogleLoading = true
        errorMessage = null

        coroutineScope.launch {
            try {
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(clientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = activity,
                    request = request
                )
                authenticateWithGoogleCredential(result.credential)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GetCredentialCancellationException) {
                Log.w(AUTH_TAG, "Google Sign-In cancelled by clinician", e)
            } catch (e: GetCredentialException) {
                Log.e(AUTH_TAG, "Credential Manager sign-in failed", e)
                errorMessage = e.localizedMessage ?: "Google Sign-In could not be completed."
            } catch (e: Exception) {
                Log.e(AUTH_TAG, "Firebase credential sign-in failed", e)
                errorMessage = e.localizedMessage ?: "Authentication error occurred."
            } finally {
                isGoogleLoading = false
            }
        }
    }

    fun submitEmailPassword() {
        errorMessage = null
        val result = if (isCreateAccountMode) {
            localAuthManager.registerLocalAccount(
                rawIdentifier = emailOrUsername,
                rawDisplayName = displayName,
                password = password,
                role = selectedRole
            )
        } else {
            localAuthManager.signInWithCredentials(
                rawIdentifier = emailOrUsername,
                password = password,
                selectedRole = selectedRole
            )
        }
        result.onFailure { err ->
            errorMessage = err.localizedMessage ?: "Unable to verify clinician credentials."
        }
    }

    Scaffold(
        containerColor = SoftCloudWhite
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(DeepRoyalNavy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalHospital,
                        contentDescription = "WardRounds",
                        tint = PureWhite,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "WardRounds",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DeepRoyalNavy,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Your Daily Ward Companion",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PeriwinkleBlue
                )

                Text(
                    text = "Inpatient Clinical Documentation & Rounds",
                    fontSize = 12.sp,
                    color = SecondaryText,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = BorderStroke(1.dp, CardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isCreateAccountMode) "Create Local Account" else "Clinician Sign In",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepRoyalNavy
                            )

                            TextButton(
                                onClick = {
                                    isCreateAccountMode = !isCreateAccountMode
                                    errorMessage = null
                                },
                                modifier = Modifier.testTag("toggle_auth_mode_button")
                            ) {
                                Text(
                                    text = if (isCreateAccountMode) "Sign In Instead" else "Create Account",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PeriwinkleBlue
                                )
                            }
                        }

                        AnimatedVisibility(visible = errorMessage != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(StatusAlert.copy(alpha = 0.14f))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Sign-in error",
                                        tint = StatusAlert,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = errorMessage.orEmpty(),
                                        fontSize = 12.sp,
                                        color = StatusAlert,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Clinical Duty Role",
                            fontSize = 12.sp,
                            color = SecondaryText,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UserRole.entries.forEach { role ->
                                FilterChip(
                                    selected = selectedRole == role,
                                    onClick = { onRoleSelected(role) },
                                    label = {
                                        Text(
                                            text = role.name.lowercase().replaceFirstChar { it.uppercase() },
                                            fontSize = 11.sp
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = DeepRoyalNavy,
                                        selectedLabelColor = PureWhite
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = emailOrUsername,
                            onValueChange = {
                                emailOrUsername = it
                                if (errorMessage != null) errorMessage = null
                            },
                            label = { Text("Email or Username") },
                            placeholder = { Text("e.g. dr.ray@hospital.org or dr.ray") },
                            modifier = Modifier
                                .testTag("login_email_input")
                                .fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = PeriwinkleBlue)
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        AnimatedVisibility(visible = isCreateAccountMode) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Clinician Full Name (Optional)") },
                                placeholder = { Text("e.g. Dr. Priya Menon") },
                                modifier = Modifier
                                    .testTag("login_display_name_input")
                                    .fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                leadingIcon = {
                                    Icon(Icons.Default.Badge, contentDescription = null, tint = PeriwinkleBlue)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                if (errorMessage != null) errorMessage = null
                            },
                            label = { Text("Password") },
                            placeholder = { Text(if (isCreateAccountMode) "Minimum 6 characters" else "Enter password") },
                            visualTransformation = if (isPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .testTag("login_password_input")
                                .fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = PeriwinkleBlue)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (!isCreateAccountMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Demo: dr.ray / wardround123",
                                    fontSize = 11.sp,
                                    color = SecondaryText
                                )
                                TextButton(
                                    onClick = {
                                        emailOrUsername = when (selectedRole) {
                                            UserRole.DOCTOR -> LocalAuthManager.DEFAULT_DEMO_EMAIL
                                            UserRole.CONSULTANT -> "dr.mukherjee@hospital.org"
                                            UserRole.ADMINISTRATOR -> "admin.ward@hospital.org"
                                        }
                                        password = LocalAuthManager.DEFAULT_DEMO_PASSWORD
                                        errorMessage = null
                                    },
                                    modifier = Modifier.testTag("fill_demo_credentials_button")
                                ) {
                                    Text(
                                        text = "Autofill Demo",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepRoyalNavy
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { submitEmailPassword() },
                            enabled = !isGoogleLoading,
                            modifier = Modifier
                                .testTag("email_sign_in_button")
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepRoyalNavy,
                                contentColor = PureWhite
                            )
                        ) {
                            Text(
                                text = if (isCreateAccountMode) "Create Local Account" else "Sign In with Email / Username",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = CardBorder
                            )
                            Text(
                                text = "  OR  ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SecondaryText
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = CardBorder
                            )
                        }

                        Button(
                            onClick = { launchInteractiveGoogleSignIn() },
                            enabled = !isGoogleLoading,
                            modifier = Modifier
                                .testTag("google_sign_in_button")
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PeriwinkleBlue,
                                contentColor = PureWhite
                            )
                        ) {
                            if (isGoogleLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = PureWhite,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Signing in...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sign in with Google (Cloud Sync)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                errorMessage = null
                                localAuthManager.signInAsGuest(selectedRole)
                            },
                            enabled = !isGoogleLoading,
                            modifier = Modifier
                                .testTag("guest_sign_in_button")
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonOutline,
                                contentDescription = null,
                                tint = DeepRoyalNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Continue as Guest (Local Only)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DeepRoyalNavy
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = PeriwinkleBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Email/Guest sessions save locally in Room. Sign in with Google for Cloud Firestore sync.",
                                fontSize = 11.sp,
                                color = SecondaryText,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MutedSurface)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Clinical Sandbox Notice: Demonstration patient and investigation charts are synthetic fictional records isolated on your device or authenticated clinician cloud workspace.",
                        fontSize = 11.sp,
                        color = SecondaryText,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
