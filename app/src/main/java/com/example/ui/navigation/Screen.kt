package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Patients : Screen("patients")
    object AddPatient : Screen("add_patient")
    object Investigations : Screen("investigations")
    object FollowUps : Screen("follow_ups")
    object Reports : Screen("reports")
    object Settings : Screen("settings")

    object PatientProfile : Screen("patient/{patientId}?tab={initialTab}") {
        fun createRoute(patientId: String, initialTab: Int = 0): String {
            return "patient/$patientId?tab=$initialTab"
        }
    }

    object EditPatient : Screen("patient/{patientId}/edit") {
        fun createRoute(patientId: String): String {
            return "patient/$patientId/edit"
        }
    }

    object DailyRoundEntry : Screen("patient/{patientId}/round_entry?roundId={roundId}") {
        fun createRoute(patientId: String, roundId: String? = null): String {
            return if (roundId != null) "patient/$patientId/round_entry?roundId=$roundId"
            else "patient/$patientId/round_entry"
        }
    }

    object AddInvestigationOrder : Screen("patient/{patientId}/order_investigation") {
        fun createRoute(patientId: String): String {
            return "patient/$patientId/order_investigation"
        }
    }

    object AddInvestigationResult : Screen("patient/{patientId}/add_result?orderId={orderId}") {
        fun createRoute(patientId: String, orderId: String? = null): String {
            return if (orderId != null) "patient/$patientId/add_result?orderId=$orderId"
            else "patient/$patientId/add_result"
        }
    }

    object TrendDetail : Screen("patient/{patientId}/trend/{paramName}") {
        fun createRoute(patientId: String, paramName: String): String {
            return "patient/$patientId/trend/$paramName"
        }
    }

    object Handover : Screen("handover?patientId={patientId}") {
        fun createRoute(patientId: String? = null): String {
            return if (patientId != null) "handover?patientId=$patientId"
            else "handover"
        }
    }
}
