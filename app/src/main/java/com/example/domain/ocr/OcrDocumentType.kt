package com.example.domain.ocr

enum class OcrDocumentType(val displayName: String) {
    ALL_AUTO("Auto Detect Document"),
    PATIENT_REGISTRATION("Patient Admission / Demographics"),
    DAILY_ROUND_NOTE("Daily Clinical Round / Vitals"),
    LAB_INVESTIGATION("Laboratory / Diagnostic Report"),
    PRESCRIPTION_MEDICATION("Prescription / Treatment Plan"),
    DISCHARGE_SUMMARY("Discharge Summary / Referral")
}
