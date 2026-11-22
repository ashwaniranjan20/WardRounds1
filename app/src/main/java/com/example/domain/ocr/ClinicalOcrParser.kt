package com.example.domain.ocr

import java.util.regex.Pattern

object ClinicalOcrParser {

    fun parse(
        rawText: String,
        targetDocType: OcrDocumentType = OcrDocumentType.ALL_AUTO,
        existingFormValues: Map<String, String> = emptyMap()
    ): OcrExtractionResult {
        val fields = mutableListOf<ExtractedClinicalField>()
        val warnings = mutableListOf<String>()

        if (rawText.isBlank()) {
            return OcrExtractionResult(
                documentType = targetDocType,
                rawText = "",
                fields = emptyList(),
                warnings = listOf("No readable text detected in image.")
            )
        }

        // Clean lines
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

        // 1. Demographics
        parseDemographics(rawText, lines, existingFormValues, fields, warnings)

        // 2. Vital Signs
        parseVitals(rawText, lines, existingFormValues, fields, warnings)

        // 3. Lab Investigations
        parseInvestigations(rawText, lines, existingFormValues, fields, warnings)

        // 4. Clinical Notes & Plan
        parseClinicalNotes(rawText, lines, existingFormValues, fields, warnings)

        // Auto-detect document type if requested
        val detectedType = when {
            targetDocType != OcrDocumentType.ALL_AUTO -> targetDocType
            fields.any { it.category == ClinicalCategory.INVESTIGATIONS } && 
                fields.count { it.category == ClinicalCategory.INVESTIGATIONS } >= 2 -> OcrDocumentType.LAB_INVESTIGATION
            fields.any { it.key == "uhid" } && fields.any { it.key == "bed" } -> OcrDocumentType.PATIENT_REGISTRATION
            fields.any { it.category == ClinicalCategory.VITALS } -> OcrDocumentType.DAILY_ROUND_NOTE
            else -> OcrDocumentType.DAILY_ROUND_NOTE
        }

        return OcrExtractionResult(
            documentType = detectedType,
            rawText = rawText,
            fields = fields,
            warnings = warnings
        )
    }

    private fun parseDemographics(
        text: String,
        lines: List<String>,
        existing: Map<String, String>,
        fields: MutableList<ExtractedClinicalField>,
        warnings: MutableList<String>
    ) {
        // UHID / MRN
        val uhidRegex = Regex("""(?:UHID|MRN|Reg(?:istration)?\.?\s*No\.?|IPD\s*No\.?)[:\s]+([A-Za-z0-9\-_/]{4,20})""", RegexOption.IGNORE_CASE)
        uhidRegex.find(text)?.let { match ->
            val value = match.groupValues[1].trim()
            val isLow = value.length < 5 || value.contains(" ")
            fields.add(
                ExtractedClinicalField(
                    key = "uhid",
                    category = ClinicalCategory.DEMOGRAPHICS,
                    label = "UHID / Hospital ID",
                    extractedValue = value,
                    currentFormValue = existing["uhid"],
                    isLowConfidence = isLow,
                    uncertaintyReason = if (isLow) "Verify full ID characters" else null
                )
            )
        }

        // Patient Name
        val nameRegex = Regex("""(?:Patient\s*Name|Pt\.?\s*Name|Name)[:\s]+([A-Za-z\s.]+?)(?=\n|Age|Sex|Gender|UHID|Bed|Date|$)""", RegexOption.IGNORE_CASE)
        nameRegex.find(text)?.let { match ->
            val name = match.groupValues[1].trim().trimEnd(',', '.', ';')
            if (name.length in 3..40 && !name.equals("Dr", ignoreCase = true) && !name.contains("Hospital", ignoreCase = true)) {
                fields.add(
                    ExtractedClinicalField(
                        key = "name",
                        category = ClinicalCategory.DEMOGRAPHICS,
                        label = "Patient Name",
                        extractedValue = name,
                        currentFormValue = existing["name"],
                        confidence = 0.9f
                    )
                )
            }
        }

        // Age & Sex combined or separate
        val ageSexRegex = Regex("""(?:Age\s*[/:]?\s*Sex|Age\s*&\s*Sex)[:\s]*(\d{1,3})\s*(?:Y|Yrs)?\s*[/,\-]\s*([MF]|Male|Female)""", RegexOption.IGNORE_CASE)
        val ageSexMatch = ageSexRegex.find(text)
        if (ageSexMatch != null) {
            val ageVal = ageSexMatch.groupValues[1]
            val sexVal = if (ageSexMatch.groupValues[2].startsWith("F", ignoreCase = true)) "Female" else "Male"
            fields.add(
                ExtractedClinicalField(
                    key = "age",
                    category = ClinicalCategory.DEMOGRAPHICS,
                    label = "Age (Years)",
                    extractedValue = ageVal,
                    currentFormValue = existing["age"]
                )
            )
            fields.add(
                ExtractedClinicalField(
                    key = "sex",
                    category = ClinicalCategory.DEMOGRAPHICS,
                    label = "Sex / Gender",
                    extractedValue = sexVal,
                    currentFormValue = existing["sex"]
                )
            )
        } else {
            // Standalone Age
            val ageRegex = Regex("""\b(?:Age)[:\s]*(\d{1,3})\s*(?:Y|Yrs|Years)?\b""", RegexOption.IGNORE_CASE)
            ageRegex.find(text)?.let { match ->
                val ageInt = match.groupValues[1].toIntOrNull()
                if (ageInt != null && ageInt in 0..120) {
                    fields.add(
                        ExtractedClinicalField(
                            key = "age",
                            category = ClinicalCategory.DEMOGRAPHICS,
                            label = "Age (Years)",
                            extractedValue = ageInt.toString(),
                            currentFormValue = existing["age"]
                        )
                    )
                }
            }

            // Standalone Sex
            val sexRegex = Regex("""\b(?:Sex|Gender)[:\s]*([MF]|Male|Female)\b""", RegexOption.IGNORE_CASE)
            sexRegex.find(text)?.let { match ->
                val rawSex = match.groupValues[1]
                val normalizedSex = if (rawSex.startsWith("F", ignoreCase = true)) "Female" else "Male"
                fields.add(
                    ExtractedClinicalField(
                        key = "sex",
                        category = ClinicalCategory.DEMOGRAPHICS,
                        label = "Sex / Gender",
                        extractedValue = normalizedSex,
                        currentFormValue = existing["sex"]
                    )
                )
            }
        }

        // Bed & Ward
        val bedRegex = Regex("""(?:Bed(?:\s*No\.?)?)[:\s]*([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE)
        bedRegex.find(text)?.let { match ->
            val bed = match.groupValues[1].trim()
            if (bed.length in 1..10) {
                fields.add(
                    ExtractedClinicalField(
                        key = "bed",
                        category = ClinicalCategory.DEMOGRAPHICS,
                        label = "Bed Number",
                        extractedValue = bed,
                        currentFormValue = existing["bed"]
                    )
                )
            }
        }

        val wardRegex = Regex("""(?:Ward(?:\s*No\.?|Name)?)[:\s]*([A-Za-z0-9\s\-]+?)(?=\n|Bed|Date|Doctor|$)""", RegexOption.IGNORE_CASE)
        wardRegex.find(text)?.let { match ->
            val ward = match.groupValues[1].trim()
            if (ward.length in 2..30) {
                fields.add(
                    ExtractedClinicalField(
                        key = "ward",
                        category = ClinicalCategory.DEMOGRAPHICS,
                        label = "Ward",
                        extractedValue = ward,
                        currentFormValue = existing["ward"]
                    )
                )
            }
        }

        // Primary Diagnosis
        val diagRegex = Regex("""(?:Primary\s*Diagnosis|Diagnosis|Working\s*Diagnosis)[:\s]*([^\n]+)""", RegexOption.IGNORE_CASE)
        diagRegex.find(text)?.let { match ->
            val diag = match.groupValues[1].trim()
            if (diag.length in 3..80) {
                fields.add(
                    ExtractedClinicalField(
                        key = "diagnosis",
                        category = ClinicalCategory.DEMOGRAPHICS,
                        label = "Primary Diagnosis",
                        extractedValue = diag,
                        currentFormValue = existing["diagnosis"]
                    )
                )
            }
        }
    }

    private fun parseVitals(
        text: String,
        lines: List<String>,
        existing: Map<String, String>,
        fields: MutableList<ExtractedClinicalField>,
        warnings: MutableList<String>
    ) {
        // Blood Pressure (e.g. 120/80, 130 / 85 mmHg)
        val bpRegex = Regex("""(?:BP|Blood\s*Pressure)[:\s]*(\d{2,3})\s*[/xX\-]\s*(\d{2,3})\s*(?:mmHg)?""", RegexOption.IGNORE_CASE)
        bpRegex.find(text)?.let { match ->
            val sys = match.groupValues[1].toIntOrNull() ?: 120
            val dia = match.groupValues[2].toIntOrNull() ?: 80
            val isAbnormal = sys !in 90..180 || dia !in 50..110

            fields.add(
                ExtractedClinicalField(
                    key = "bp",
                    category = ClinicalCategory.VITALS,
                    label = "Blood Pressure",
                    extractedValue = "$sys/$dia",
                    currentFormValue = existing["bp"],
                    unit = "mmHg",
                    referenceRange = "90-120 / 60-80",
                    isLowConfidence = isAbnormal,
                    uncertaintyReason = if (isAbnormal) "High/Low blood pressure value - verify image" else null
                )
            )
        }

        // Pulse Rate / Heart Rate (e.g. Pulse: 78 bpm)
        val pulseRegex = Regex("""(?:Pulse|Pulse\s*Rate|Heart\s*Rate|HR)[:\s]*(\d{2,3})\s*(?:bpm|/min)?""", RegexOption.IGNORE_CASE)
        pulseRegex.find(text)?.let { match ->
            val pulse = match.groupValues[1].toIntOrNull()
            if (pulse != null && pulse in 30..220) {
                val isLow = pulse < 50 || pulse > 120
                fields.add(
                    ExtractedClinicalField(
                        key = "pulse",
                        category = ClinicalCategory.VITALS,
                        label = "Pulse Rate",
                        extractedValue = pulse.toString(),
                        currentFormValue = existing["pulse"],
                        unit = "bpm",
                        referenceRange = "60-100",
                        isLowConfidence = isLow,
                        uncertaintyReason = if (isLow) "Bradycardia/Tachycardia - verify digits" else null
                    )
                )
            }
        }

        // SpO2 / Oxygen Saturation (e.g. SpO2: 98%)
        val spo2Regex = Regex("""(?:SpO2|SPO2|Oxygen\s*Sat(?:uration)?)[:\s]*(\d{2,3})\s*%?""", RegexOption.IGNORE_CASE)
        spo2Regex.find(text)?.let { match ->
            val spo2 = match.groupValues[1].toIntOrNull()
            if (spo2 != null) {
                val isInvalid = spo2 > 100 || spo2 < 70
                fields.add(
                    ExtractedClinicalField(
                        key = "spo2",
                        category = ClinicalCategory.VITALS,
                        label = "SpO₂ Saturation",
                        extractedValue = if (spo2 <= 100) spo2.toString() else "98",
                        currentFormValue = existing["spo2"],
                        unit = "%",
                        referenceRange = "95-100",
                        isLowConfidence = isInvalid,
                        uncertaintyReason = if (isInvalid) "Out-of-range SpO2 percentage" else null
                    )
                )
            }
        }

        // Temperature (e.g. Temp: 98.6 F, 37.2 C)
        val tempRegex = Regex("""(?:Temp(?:erature)?)[:\s]*(\d{2,3}(?:\.\d)?)\s*(?:°?\s*([FC]))?""", RegexOption.IGNORE_CASE)
        tempRegex.find(text)?.let { match ->
            val tempVal = match.groupValues[1]
            val unit = match.groupValues[2].ifBlank { "°F" }
            fields.add(
                ExtractedClinicalField(
                    key = "temperature",
                    category = ClinicalCategory.VITALS,
                    label = "Temperature",
                    extractedValue = tempVal,
                    currentFormValue = existing["temperature"],
                    unit = unit
                )
            )
        }

        // Respiratory Rate
        val rrRegex = Regex("""(?:RR|Resp(?:iratory)?\s*Rate)[:\s]*(\d{1,2})\s*(?:/min|cpm|bpm)?""", RegexOption.IGNORE_CASE)
        rrRegex.find(text)?.let { match ->
            val rr = match.groupValues[1].toIntOrNull()
            if (rr != null && rr in 8..60) {
                fields.add(
                    ExtractedClinicalField(
                        key = "respiratory_rate",
                        category = ClinicalCategory.VITALS,
                        label = "Respiratory Rate",
                        extractedValue = rr.toString(),
                        currentFormValue = existing["respiratory_rate"],
                        unit = "breaths/min",
                        referenceRange = "12-20"
                    )
                )
            }
        }
    }

    private fun parseInvestigations(
        text: String,
        lines: List<String>,
        existing: Map<String, String>,
        fields: MutableList<ExtractedClinicalField>,
        warnings: MutableList<String>
    ) {
        val labPatterns = listOf(
            LabPattern("Hemoglobin", listOf("Hemoglobin", "Hb", "Hgb"), """(\d{1,2}(?:\.\d{1,2})?)""", "g/dL", "12.0 - 16.0"),
            LabPattern("Total Leukocyte Count (TLC)", listOf("TLC", "Total Leukocyte", "WBC"), """([\d,]+(?:\.\d+)?)""", "/cumm", "4,000 - 11,000"),
            LabPattern("Platelet Count", listOf("Platelet", "Platelets", "PLT"), """([\d,]+(?:\.\d+)?)""", "/cumm", "150,000 - 450,000"),
            LabPattern("Serum Creatinine", listOf("Creatinine", "S. Creatinine", "S Creat"), """(\d{1,2}(?:\.\d{1,2})?)""", "mg/dL", "0.7 - 1.3"),
            LabPattern("Blood Urea", listOf("Blood Urea", "Urea", "BUN"), """(\d{1,3}(?:\.\d+)?)""", "mg/dL", "15 - 45"),
            LabPattern("Total Bilirubin", listOf("Total Bilirubin", "Bilirubin", "S. Bili"), """(\d{1,2}(?:\.\d{1,2})?)""", "mg/dL", "0.2 - 1.2"),
            LabPattern("SGOT (AST)", listOf("SGOT", "AST"), """(\d{1,4})""", "U/L", "10 - 40"),
            LabPattern("SGPT (ALT)", listOf("SGPT", "ALT"), """(\d{1,4})""", "U/L", "10 - 45"),
            LabPattern("Serum Sodium", listOf("Sodium", "S. Sodium", "Na+"), """(\d{2,3}(?:\.\d+)?)""", "mEq/L", "135 - 145"),
            LabPattern("Serum Potassium", listOf("Potassium", "S. Potassium", "K+"), """(\d{1,2}(?:\.\d{1,2})?)""", "mEq/L", "3.5 - 5.1"),
            LabPattern("C-Reactive Protein (CRP)", listOf("CRP", "C-Reactive Protein"), """(\d{1,3}(?:\.\d+)?)""", "mg/L", "< 5.0"),
            LabPattern("Blood Glucose (RBS)", listOf("RBS", "Random Blood Sugar", "Glucose"), """(\d{2,3})""", "mg/dL", "70 - 140"),
            LabPattern("HbA1c", listOf("HbA1c", "Glycated Hb"), """(\d{1,2}(?:\.\d{1,2})?)""", "%", "< 5.7")
        )

        for (pattern in labPatterns) {
            val key = pattern.name.lowercase().replace(Regex("[^a-z0-9]"), "_")
            for (synonym in pattern.synonyms) {
                val escaped = Pattern.quote(synonym)
                val regex = Regex("""(?:\b$escaped\b)[:\s\-]*${pattern.valueRegex}\s*(?:${Pattern.quote(pattern.defaultUnit)})?""", RegexOption.IGNORE_CASE)
                val match = regex.find(text)
                if (match != null) {
                    val rawVal = match.groupValues[1].replace(",", "").trim()
                    // Check decimal point ambiguity
                    val isLow = rawVal.contains(".") && rawVal.length > 5
                    fields.add(
                        ExtractedClinicalField(
                            key = "lab_$key",
                            category = ClinicalCategory.INVESTIGATIONS,
                            label = pattern.name,
                            extractedValue = rawVal,
                            currentFormValue = existing["lab_$key"],
                            unit = pattern.defaultUnit,
                            referenceRange = pattern.referenceRange,
                            isLowConfidence = isLow,
                            uncertaintyReason = if (isLow) "Check decimal point placement" else null
                        )
                    )
                    break
                }
            }
        }
    }

    private fun parseClinicalNotes(
        text: String,
        lines: List<String>,
        existing: Map<String, String>,
        fields: MutableList<ExtractedClinicalField>,
        warnings: MutableList<String>
    ) {
        // Clinical Assessment
        val assessRegex = Regex("""(?:Clinical\s*Assessment|Assessment|Impression)[:\s]*([^\n]+(?:\n[^\n]+){0,2})""", RegexOption.IGNORE_CASE)
        assessRegex.find(text)?.let { match ->
            val note = match.groupValues[1].trim()
            if (note.length > 5) {
                fields.add(
                    ExtractedClinicalField(
                        key = "assessment",
                        category = ClinicalCategory.CLINICAL_NOTES,
                        label = "Clinical Assessment",
                        extractedValue = note,
                        currentFormValue = existing["assessment"]
                    )
                )
            }
        }

        // Treatment & Management Plan
        val planRegex = Regex("""(?:Treatment\s*Plan|Plan|Management|Rx)[:\s]*([^\n]+(?:\n[^\n]+){0,3})""", RegexOption.IGNORE_CASE)
        planRegex.find(text)?.let { match ->
            val plan = match.groupValues[1].trim()
            if (plan.length > 5) {
                fields.add(
                    ExtractedClinicalField(
                        key = "plan",
                        category = ClinicalCategory.CLINICAL_NOTES,
                        label = "Treatment & Management Plan",
                        extractedValue = plan,
                        currentFormValue = existing["plan"]
                    )
                )
            }
        }

        // Symptoms & General Condition
        val symptomRegex = Regex("""(?:Symptoms|Complaints|Chief\s*Complaints)[:\s]*([^\n]+(?:\n[^\n]+){0,2})""", RegexOption.IGNORE_CASE)
        symptomRegex.find(text)?.let { match ->
            val sym = match.groupValues[1].trim()
            if (sym.length > 4) {
                fields.add(
                    ExtractedClinicalField(
                        key = "symptoms",
                        category = ClinicalCategory.CLINICAL_NOTES,
                        label = "Symptoms & Complaints",
                        extractedValue = sym,
                        currentFormValue = existing["symptoms"]
                    )
                )
            }
        }
    }

    private data class LabPattern(
        val name: String,
        val synonyms: List<String>,
        val valueRegex: String,
        val defaultUnit: String,
        val referenceRange: String
    )
}
