package com.example.data.model

enum class SyncStatus {
    SAVED_LOCALLY,
    SYNC_PENDING,
    SYNCED,
    SYNC_FAILED
}

enum class UserRole {
    DOCTOR,
    CONSULTANT,
    ADMINISTRATOR
}

enum class AdmissionStatus {
    ACTIVE,
    DISCHARGED,
    TRANSFERRED
}

enum class RoundType {
    MORNING,
    EVENING,
    EMERGENCY,
    OTHER
}

enum class InvestigationPriority {
    ROUTINE,
    URGENT,
    STAT
}

enum class InvestigationStatus {
    ORDERED,
    COLLECTION_PENDING,
    SAMPLE_COLLECTED,
    SENT_TO_LAB,
    RECEIPT_CONFIRMED,
    IN_PROGRESS,
    RESULT_AVAILABLE,
    RESULT_REVIEWED,
    COMPLETED,
    CANCELLED
}

enum class ResultStatus {
    PRELIMINARY,
    FINAL,
    CORRECTED
}

enum class TrendDirection {
    INCREASING,
    DECREASING,
    STABLE,
    INSUFFICIENT_DATA
}

enum class ReferenceRangeStatus {
    WITHIN_RANGE,
    ABOVE_REFERENCE_RANGE,
    BELOW_REFERENCE_RANGE,
    NO_REFERENCE_RANGE
}

enum class ClinicalChangeClassification {
    POTENTIALLY_FAVOURABLE,
    POTENTIALLY_CONCERNING,
    NEUTRAL,
    INSUFFICIENT_DATA
}

enum class DischargeStatus {
    NOT_PLANNED,
    UNDER_CONSIDERATION,
    PLANNED,
    AWAITING_INVESTIGATION,
    AWAITING_CONSULTATION,
    READY_FOR_CLINICIAN_REVIEW,
    DISCHARGED
}

enum class TaskPriority {
    ROUTINE,
    MEDIUM,
    HIGH,
    URGENT
}

enum class TaskStatus {
    SCHEDULED,
    DUE_TODAY,
    OVERDUE,
    COMPLETED,
    CANCELLED
}

enum class ShiftType(val label: String) {
    MORNING_TO_EVENING("Morning → Evening"),
    EVENING_TO_NIGHT("Evening → Night"),
    NIGHT_TO_MORNING("Night → Morning")
}

enum class IllnessSeverity(val label: String) {
    UNSTABLE("Unstable"),
    WATCHER("Watcher"),
    STABLE("Stable")
}

enum class HandoverStatus(val label: String) {
    DRAFT("Draft"),
    HANDED_OVER("Ready for Sign-Off"),
    ACKNOWLEDGED("Acknowledged")
}
