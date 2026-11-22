package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

enum class LocalAuthMode {
    LOCAL_EMAIL,
    GUEST
}

data class LocalAuthSession(
    val sessionId: String,
    val mode: LocalAuthMode,
    val displayName: String,
    val identifier: String,
    val role: UserRole,
    val department: String
)

class LocalAuthManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeSession = MutableStateFlow(readStoredSession())
    val activeSession: StateFlow<LocalAuthSession?> = _activeSession.asStateFlow()

    init {
        ensureSeededAccounts()
    }

    private fun ensureSeededAccounts() {
        if (prefs.contains(KEY_ACCOUNTS_JSON)) return
        val seeded = JSONArray().apply {
            put(
                createAccountJson(
                    identifier = "dr.ray@hospital.org",
                    displayName = "Dr. Ananya Ray",
                    role = UserRole.DOCTOR,
                    department = "Internal Medicine",
                    password = DEFAULT_DEMO_PASSWORD
                )
            )
            put(
                createAccountJson(
                    identifier = "dr.mukherjee@hospital.org",
                    displayName = "Dr. S. Mukherjee, MD",
                    role = UserRole.CONSULTANT,
                    department = "Internal Medicine",
                    password = DEFAULT_DEMO_PASSWORD
                )
            )
            put(
                createAccountJson(
                    identifier = "admin.ward@hospital.org",
                    displayName = "Chief Medical Admin",
                    role = UserRole.ADMINISTRATOR,
                    department = "Clinical Governance",
                    password = DEFAULT_DEMO_PASSWORD
                )
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS_JSON, seeded.toString()).apply()
    }

    fun signInWithCredentials(
        rawIdentifier: String,
        password: String,
        selectedRole: UserRole
    ): Result<LocalAuthSession> {
        val normalized = normalizeIdentifier(rawIdentifier)
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your hospital email or username."))
        }
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your password."))
        }

        val accounts = readAccountsArray()
        for (i in 0 until accounts.length()) {
            val obj = accounts.optJSONObject(i) ?: continue
            val storedId = obj.optString("identifier", "")
            val storedUsernamePrefix = storedId.substringBefore("@")
            if (storedId == normalized || (!normalized.contains("@") && storedUsernamePrefix == normalized)) {
                val saltHex = obj.optString("saltHex", "")
                val expectedHashHex = obj.optString("passwordHashHex", "")
                if (verifyPassword(password, saltHex, expectedHashHex)) {
                    val session = LocalAuthSession(
                        sessionId = "local_${storedId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}",
                        mode = LocalAuthMode.LOCAL_EMAIL,
                        displayName = obj.optString("displayName", formatDisplayName(storedId)),
                        identifier = storedId,
                        role = selectedRole,
                        department = obj.optString("department", defaultDepartmentFor(selectedRole))
                    )
                    persistSession(session)
                    return Result.success(session)
                } else {
                    return Result.failure(IllegalArgumentException("Incorrect password for $rawIdentifier."))
                }
            }
        }

        return Result.failure(
            IllegalArgumentException("No local clinician account found for \"$rawIdentifier\". Switch to Create Account or try Guest mode.")
        )
    }

    fun registerLocalAccount(
        rawIdentifier: String,
        rawDisplayName: String,
        password: String,
        role: UserRole
    ): Result<LocalAuthSession> {
        val normalized = normalizeIdentifier(rawIdentifier)
        if (normalized.length < 3) {
            return Result.failure(IllegalArgumentException("Email or username must be at least 3 characters."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        val accounts = readAccountsArray()
        for (i in 0 until accounts.length()) {
            val obj = accounts.optJSONObject(i) ?: continue
            val storedId = obj.optString("identifier", "")
            val storedPrefix = storedId.substringBefore("@")
            if (storedId == normalized || (!normalized.contains("@") && storedPrefix == normalized)) {
                return Result.failure(IllegalArgumentException("An account already exists for \"$rawIdentifier\". Sign in instead."))
            }
        }

        val resolvedEmail = if (normalized.contains("@")) normalized else "$normalized@hospital.local"
        val resolvedName = rawDisplayName.trim().ifBlank { formatDisplayName(normalized) }
        val department = defaultDepartmentFor(role)

        accounts.put(
            createAccountJson(
                identifier = resolvedEmail,
                displayName = resolvedName,
                role = role,
                department = department,
                password = password
            )
        )
        prefs.edit().putString(KEY_ACCOUNTS_JSON, accounts.toString()).apply()

        val session = LocalAuthSession(
            sessionId = "local_${UUID.randomUUID()}",
            mode = LocalAuthMode.LOCAL_EMAIL,
            displayName = resolvedName,
            identifier = resolvedEmail,
            role = role,
            department = department
        )
        persistSession(session)
        return Result.success(session)
    }

    fun signInAsGuest(role: UserRole = UserRole.DOCTOR): LocalAuthSession {
        val guestName = when (role) {
            UserRole.DOCTOR -> "Guest Duty Doctor"
            UserRole.CONSULTANT -> "Guest Consultant"
            UserRole.ADMINISTRATOR -> "Guest Ward Admin"
        }
        val session = LocalAuthSession(
            sessionId = "guest_local_session",
            mode = LocalAuthMode.GUEST,
            displayName = guestName,
            identifier = "guest@local.wardrounds",
            role = role,
            department = defaultDepartmentFor(role)
        )
        persistSession(session)
        return session
    }

    fun signOut() {
        prefs.edit().remove(KEY_ACTIVE_SESSION).apply()
        _activeSession.value = null
    }

    private fun persistSession(session: LocalAuthSession) {
        val json = JSONObject().apply {
            put("sessionId", session.sessionId)
            put("mode", session.mode.name)
            put("displayName", session.displayName)
            put("identifier", session.identifier)
            put("role", session.role.name)
            put("department", session.department)
        }
        prefs.edit().putString(KEY_ACTIVE_SESSION, json.toString()).apply()
        _activeSession.value = session
    }

    private fun readStoredSession(): LocalAuthSession? {
        val raw = prefs.getString(KEY_ACTIVE_SESSION, null) ?: return null
        return try {
            val json = JSONObject(raw)
            LocalAuthSession(
                sessionId = json.optString("sessionId", "local_session"),
                mode = LocalAuthMode.valueOf(json.optString("mode", LocalAuthMode.GUEST.name)),
                displayName = json.optString("displayName", "Duty Clinician"),
                identifier = json.optString("identifier", ""),
                role = UserRole.valueOf(json.optString("role", UserRole.DOCTOR.name)),
                department = json.optString("department", "Internal Medicine")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readAccountsArray(): JSONArray {
        ensureSeededAccounts()
        val raw = prefs.getString(KEY_ACCOUNTS_JSON, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun createAccountJson(
        identifier: String,
        displayName: String,
        role: UserRole,
        department: String,
        password: String
    ): JSONObject {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.toHex()
        val hashHex = hashPassword(password, salt).toHex()
        return JSONObject().apply {
            put("identifier", normalizeIdentifier(identifier))
            put("displayName", displayName)
            put("role", role.name)
            put("department", department)
            put("saltHex", saltHex)
            put("passwordHashHex", hashHex)
        }
    }

    private fun verifyPassword(password: String, saltHex: String, expectedHashHex: String): Boolean {
        val salt = saltHex.hexToBytesOrNull() ?: return false
        val expected = expectedHashHex.hexToBytesOrNull() ?: return false
        val computed = hashPassword(password, salt)
        return MessageDigest.isEqual(computed, expected)
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(password.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0 || isEmpty()) return null
        return try {
            ByteArray(length / 2) { idx ->
                substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeIdentifier(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    private fun formatDisplayName(identifier: String): String {
        val base = identifier.substringBefore("@").replace(".", " ").replace("_", " ").trim()
        return base.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .ifBlank { "Duty Clinician" }
    }

    private fun defaultDepartmentFor(role: UserRole): String = when (role) {
        UserRole.DOCTOR -> "Internal Medicine"
        UserRole.CONSULTANT -> "Internal Medicine"
        UserRole.ADMINISTRATOR -> "Clinical Governance"
    }

    companion object {
        private const val PREFS_NAME = "wardround_local_auth"
        private const val KEY_ACCOUNTS_JSON = "key_local_accounts_v1"
        private const val KEY_ACTIVE_SESSION = "key_active_local_session_v1"
        const val DEFAULT_DEMO_EMAIL = "dr.ray@hospital.org"
        const val DEFAULT_DEMO_PASSWORD = "wardround123"

        @Volatile
        private var instance: LocalAuthManager? = null

        fun getInstance(context: Context): LocalAuthManager {
            return instance ?: synchronized(this) {
                instance ?: LocalAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
