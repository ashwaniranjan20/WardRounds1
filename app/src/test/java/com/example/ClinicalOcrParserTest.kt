package com.example

import com.example.domain.ocr.ClinicalCategory
import com.example.domain.ocr.ClinicalOcrParser
import com.example.domain.ocr.ConflictResolutionChoice
import com.example.domain.ocr.ExtractedClinicalField
import com.example.domain.ocr.OcrDocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalOcrParserTest {

    @Test
    fun parse_demographics_extractsExpectedFields() {
        val sampleText = """
            ST. JUDE HOSPITAL
            UHID: WR-2026-88192
            Patient Name: Johnathan Doe
            Age / Sex: 54 Yrs / Male
            Ward: Cardiology ICU Bed: C-14
            Primary Diagnosis: Acute Anterior Wall Myocardial Infarction
        """.trimIndent()

        val result = ClinicalOcrParser.parse(sampleText)

        val uhidField = result.fields.find { it.key == "uhid" }
        assertNotNull(uhidField)
        assertEquals("WR-2026-88192", uhidField?.extractedValue)

        val nameField = result.fields.find { it.key == "name" }
        assertNotNull(nameField)
        assertEquals("Johnathan Doe", nameField?.extractedValue)

        val ageField = result.fields.find { it.key == "age" }
        assertNotNull(ageField)
        assertEquals("54", ageField?.extractedValue)

        val sexField = result.fields.find { it.key == "sex" }
        assertNotNull(sexField)
        assertEquals("Male", sexField?.extractedValue)

        val bedField = result.fields.find { it.key == "bed" }
        assertNotNull(bedField)
        assertEquals("C-14", bedField?.extractedValue)
    }

    @Test
    fun parse_vitals_extractsBloodPressureAndPulse() {
        val sampleVitals = """
            Bedside Nursing Observation:
            BP: 135/85 mmHg
            Pulse Rate: 76 bpm
            SpO2: 98%
            Temp: 98.4 F
            RR: 18 /min
        """.trimIndent()

        val result = ClinicalOcrParser.parse(sampleVitals)

        val bpField = result.fields.find { it.key == "bp" }
        assertNotNull(bpField)
        assertEquals("135/85", bpField?.extractedValue)
        assertEquals("mmHg", bpField?.unit)

        val pulseField = result.fields.find { it.key == "pulse" }
        assertNotNull(pulseField)
        assertEquals("76", pulseField?.extractedValue)

        val spo2Field = result.fields.find { it.key == "spo2" }
        assertNotNull(spo2Field)
        assertEquals("98", spo2Field?.extractedValue)

        val tempField = result.fields.find { it.key == "temperature" }
        assertNotNull(tempField)
        assertEquals("98.4", tempField?.extractedValue)

        val rrField = result.fields.find { it.key == "respiratory_rate" }
        assertNotNull(rrField)
        assertEquals("18", rrField?.extractedValue)
    }

    @Test
    fun parse_investigations_extractsLabParameters() {
        val labSlip = """
            CENTRAL DIAGNOSTIC LABORATORY
            Hemoglobin: 13.5 g/dL
            Serum Creatinine: 1.1 mg/dL
            Total Leukocyte Count: 8500 /cumm
            Platelet Count: 240000 /cumm
            Serum Potassium: 4.2 mEq/L
        """.trimIndent()

        val result = ClinicalOcrParser.parse(labSlip)

        val hbField = result.fields.find { it.key == "lab_hemoglobin" }
        assertNotNull(hbField)
        assertEquals("13.5", hbField?.extractedValue)

        val creatField = result.fields.find { it.key == "lab_serum_creatinine" }
        assertNotNull(creatField)
        assertEquals("1.1", creatField?.extractedValue)

        val kField = result.fields.find { it.key == "lab_serum_potassium" }
        assertNotNull(kField)
        assertEquals("4.2", kField?.extractedValue)
    }

    @Test
    fun conflictResolution_handlesKeepExistingAndUseOcr() {
        val field = ExtractedClinicalField(
            key = "bp",
            category = ClinicalCategory.VITALS,
            label = "Blood Pressure",
            extractedValue = "140/90",
            currentFormValue = "120/80",
            conflictChoice = ConflictResolutionChoice.USE_OCR
        )

        assertTrue(field.hasConflict)
        assertEquals("140/90", field.finalEffectiveValue)

        val keepExistingField = field.copy(conflictChoice = ConflictResolutionChoice.KEEP_EXISTING)
        assertEquals("120/80", keepExistingField.finalEffectiveValue)

        val appendField = field.copy(conflictChoice = ConflictResolutionChoice.APPEND_BOTH)
        assertEquals("120/80 | OCR: 140/90", appendField.finalEffectiveValue)
    }

    @Test
    fun lowConfidence_flagsAbnormalVitals() {
        val extremeText = """
            BP: 260/160 mmHg
            Pulse: 180 bpm
            SpO2: 120%
        """.trimIndent()

        val result = ClinicalOcrParser.parse(extremeText)

        val bpField = result.fields.find { it.key == "bp" }
        assertTrue(bpField?.isLowConfidence == true)

        val pulseField = result.fields.find { it.key == "pulse" }
        assertTrue(pulseField?.isLowConfidence == true)
    }
}
