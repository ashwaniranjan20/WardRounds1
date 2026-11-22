package com.example.domain.ocr

import java.util.UUID

enum class ClinicalCategory(val title: String) {
    DEMOGRAPHICS("Patient Demographics"),
    VITALS("Vital Signs"),
    INVESTIGATIONS("Laboratory Investigations"),
    CLINICAL_NOTES("Clinical Notes & Management Plan"),
    OTHER("Other Extracted Content")
}

enum class ConflictResolutionChoice {
    USE_OCR,
    KEEP_EXISTING,
    APPEND_BOTH
}

data class ExtractedClinicalField(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val category: ClinicalCategory,
    val label: String,
    val extractedValue: String,
    val currentFormValue: String? = null,
    val unit: String? = null,
    val referenceRange: String? = null,
    val confidence: Float = 0.95f,
    val isLowConfidence: Boolean = false,
    val uncertaintyReason: String? = null,
    val isAccepted: Boolean = true,
    val userEditedValue: String = extractedValue,
    val conflictChoice: ConflictResolutionChoice = ConflictResolutionChoice.USE_OCR
) {
    val hasConflict: Boolean
        get() = !currentFormValue.isNullOrBlank() && 
                !currentFormValue.equals(extractedValue.trim(), ignoreCase = true)

    val finalEffectiveValue: String
        get() = when {
            hasConflict && conflictChoice == ConflictResolutionChoice.KEEP_EXISTING -> {
                currentFormValue.orEmpty()
            }
            hasConflict && conflictChoice == ConflictResolutionChoice.APPEND_BOTH -> {
                "${currentFormValue.orEmpty()} | OCR: ${userEditedValue.trim()}"
            }
            else -> userEditedValue.trim()
        }
}

data class OcrExtractionResult(
    val documentType: OcrDocumentType,
    val rawText: String,
    val fields: List<ExtractedClinicalField>,
    val warnings: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
