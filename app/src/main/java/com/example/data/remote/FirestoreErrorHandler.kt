package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

object FirestoreErrorHandler {

    private const val TAG = "FirestoreError"

    fun handleFirestoreError(
        exception: Throwable,
        operationType: OperationType,
        path: String?
    ): String {
        var root: Throwable = exception
        while (root.cause != null && root.cause !== root) {
            root = root.cause!!
        }

        val currentUser = try {
            Firebase.auth.currentUser
        } catch (_: Throwable) {
            null
        }

        val providerArray = JSONArray().apply {
            currentUser?.providerData?.forEach { info ->
                put(
                    JSONObject().apply {
                        put("providerId", info.providerId)
                        put("displayName", info.displayName ?: JSONObject.NULL)
                        put("email", info.email ?: JSONObject.NULL)
                        put("photoUrl", info.photoUrl?.toString() ?: JSONObject.NULL)
                    }
                )
            }
        }

        val authInfo = JSONObject().apply {
            put("userId", currentUser?.uid ?: JSONObject.NULL)
            put("email", currentUser?.email ?: JSONObject.NULL)
            put("emailVerified", currentUser?.isEmailVerified ?: false)
            put("isAnonymous", currentUser?.isAnonymous ?: false)
            put("tenantId", currentUser?.tenantId ?: JSONObject.NULL)
            put("providerInfo", providerArray)
        }

        val firestoreCode = (root as? FirebaseFirestoreException)?.code?.name
            ?: (exception as? FirebaseFirestoreException)?.code?.name

        val errorPayload = JSONObject().apply {
            put("error", root.localizedMessage ?: exception.localizedMessage ?: "Unknown Firestore error")
            put("code", firestoreCode ?: JSONObject.NULL)
            put("operationType", operationType.name.lowercase())
            put("path", path ?: JSONObject.NULL)
            put("authInfo", authInfo)
        }

        Log.e(TAG, errorPayload.toString(), exception)

        return when {
            firestoreCode == "PERMISSION_DENIED" ->
                "Access denied while syncing clinical documentation at ${path ?: "cloud storage"}."
            root.message?.contains("index", ignoreCase = true) == true ->
                "Cloud index building — please wait a few minutes and retry."
            else -> root.localizedMessage ?: exception.localizedMessage ?: "Firestore sync error"
        }
    }
}
