package com.example

import android.app.Application
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.preferences.LocalAuthManager
import com.example.data.preferences.ThemePreferences
import com.example.data.remote.FirestoreClinicalSync
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WardRoundApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppDatabase.getDatabase(this)
    }

    val themePreferences: ThemePreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ThemePreferences.getInstance(this)
    }

    val localAuthManager: LocalAuthManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalAuthManager.getInstance(this)
    }

    val firestoreDb: FirebaseFirestore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
    }

    val firestoreSync: FirestoreClinicalSync by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FirestoreClinicalSync(db = firestoreDb)
    }

    override fun onCreate() {
        super.onCreate()
        // Eagerly initialize Room, preferences, and Firebase on an IO dispatcher so the main
        // thread stays unblocked during initial window frame draw and SurfaceSyncGroup commit.
        appScope.launch {
            try {
                database.wardRoundDao()
            } catch (t: Throwable) {
                Log.w("WardRoundApp", "Room warm-up deferred: ${t.localizedMessage}")
            }

            try {
                themePreferences
                localAuthManager
            } catch (t: Throwable) {
                Log.w("WardRoundApp", "Preferences warm-up deferred: ${t.localizedMessage}")
            }

            try {
                if (FirebaseApp.getApps(this@WardRoundApplication).isEmpty()) {
                    FirebaseApp.initializeApp(this@WardRoundApplication)
                }
                Firebase.auth
                firestoreSync
            } catch (t: Throwable) {
                Log.w("WardRoundApp", "Firebase warm-up deferred: ${t.localizedMessage}")
            }
        }
    }
}
