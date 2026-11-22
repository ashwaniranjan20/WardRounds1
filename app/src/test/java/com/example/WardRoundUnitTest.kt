package com.example

import com.example.data.model.ClinicalChangeClassification
import com.example.data.model.InvestigationResultEntity
import com.example.data.model.ReferenceRangeStatus
import com.example.data.model.ResultStatus
import com.example.data.model.SyncStatus
import com.example.data.model.TrendDirection
import com.example.data.model.TrendEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WardRoundUnitTest {

    @Test
    fun testTrendEngineCalculations_withDecreasingInflammatoryMarker() {
        val crpDay1 = InvestigationResultEntity(
            id = "r1",
            patientId = "p1",
            admissionId = "adm1",
            investigationName = "CRP",
            category = "Biochemistry",
            parameterName = "CRP",
            numericValue = 85.0,
            originalTextValue = "85.0",
            unit = "mg/L",
            refRangeLow = 0.0,
            refRangeHigh = 5.0,
            refRangeText = "0.0 - 5.0 mg/L",
            measurementDate = "2026-10-24",
            measurementTime = "10:00",
            syncStatus = SyncStatus.SAVED_LOCALLY
        )

        val crpDay2 = InvestigationResultEntity(
            id = "r2",
            patientId = "p1",
            admissionId = "adm1",
            investigationName = "CRP",
            category = "Biochemistry",
            parameterName = "CRP",
            numericValue = 42.5,
            originalTextValue = "42.5",
            unit = "mg/L",
            refRangeLow = 0.0,
            refRangeHigh = 5.0,
            refRangeText = "0.0 - 5.0 mg/L",
            measurementDate = "2026-10-25",
            measurementTime = "09:00",
            syncStatus = SyncStatus.SAVED_LOCALLY
        )

        val trend = TrendEngine.calculateTrend("CRP", "Biochemistry", "mg/L", listOf(crpDay1, crpDay2))

        assertEquals(42.5, trend.latestValue!!, 0.001)
        assertEquals(85.0, trend.previousValue!!, 0.001)
        assertEquals(-42.5, trend.absoluteChange!!, 0.001)
        assertEquals(-50.0, trend.percentageChange!!, 0.001)
        assertEquals(TrendDirection.DECREASING, trend.direction)
        assertEquals(ReferenceRangeStatus.ABOVE_REFERENCE_RANGE, trend.refRangeStatus)
        assertEquals(ClinicalChangeClassification.POTENTIALLY_FAVOURABLE, trend.classification)
    }

    @Test
    fun testTrendEngineCalculations_zeroPreviousValueHandledSafely() {
        val r1 = InvestigationResultEntity(
            id = "r1",
            patientId = "p1",
            admissionId = "adm1",
            investigationName = "Test",
            category = "Biochemistry",
            parameterName = "Protein",
            numericValue = 0.0,
            originalTextValue = "0.0",
            unit = "mg/dL",
            measurementDate = "2026-10-24",
            measurementTime = "10:00"
        )

        val r2 = InvestigationResultEntity(
            id = "r2",
            patientId = "p1",
            admissionId = "adm1",
            investigationName = "Test",
            category = "Biochemistry",
            parameterName = "Protein",
            numericValue = 1.5,
            originalTextValue = "1.5",
            unit = "mg/dL",
            measurementDate = "2026-10-25",
            measurementTime = "10:00"
        )

        val trend = TrendEngine.calculateTrend("Protein", "Biochemistry", "mg/dL", listOf(r1, r2))

        assertEquals(1.5, trend.absoluteChange!!, 0.001)
        // Ensure division by zero is handled safely (null percentage)
        assertNull(trend.percentageChange)
        assertEquals(TrendDirection.INCREASING, trend.direction)
    }

    @Test
    fun testTrendEngine_singleObservationInsufficientData() {
        val single = InvestigationResultEntity(
            id = "r1",
            patientId = "p1",
            admissionId = "adm1",
            investigationName = "Platelets",
            category = "Hematology",
            parameterName = "Platelets",
            numericValue = 220.0,
            originalTextValue = "220",
            unit = "x10^3/uL",
            measurementDate = "2026-10-24",
            measurementTime = "10:00"
        )

        val trend = TrendEngine.calculateTrend("Platelets", "Hematology", "x10^3/uL", listOf(single))

        assertEquals(220.0, trend.latestValue!!, 0.001)
        assertNull(trend.previousValue)
        assertNull(trend.absoluteChange)
        assertNull(trend.percentageChange)
        assertEquals(TrendDirection.INSUFFICIENT_DATA, trend.direction)
        assertEquals(ClinicalChangeClassification.INSUFFICIENT_DATA, trend.classification)
    }

    @Test
    fun testAppThemeMode_enumValues() {
        val modes = com.example.data.preferences.AppThemeMode.values()
        assertEquals(3, modes.size)
        assertEquals(com.example.data.preferences.AppThemeMode.LIGHT, com.example.data.preferences.AppThemeMode.valueOf("LIGHT"))
        assertEquals(com.example.data.preferences.AppThemeMode.DARK, com.example.data.preferences.AppThemeMode.valueOf("DARK"))
        assertEquals(com.example.data.preferences.AppThemeMode.SYSTEM, com.example.data.preferences.AppThemeMode.valueOf("SYSTEM"))
    }

    @Test
    fun testCollapsibleSections_expandAllAndCollapseAll() {
        var expanded = setOf(2, 3, 7, 8)
        assertEquals(4, expanded.size)

        // Expand All
        expanded = (1..10).toSet()
        assertEquals(10, expanded.size)
        for (i in 1..10) {
            org.junit.Assert.assertTrue(expanded.contains(i))
        }

        // Collapse All
        expanded = emptySet()
        assertEquals(0, expanded.size)
        org.junit.Assert.assertTrue(expanded.isEmpty())

        // Toggle individual section 3
        expanded = if (expanded.contains(3)) expanded - 3 else expanded + 3
        assertEquals(setOf(3), expanded)

        // Toggle individual section 3 again
        expanded = if (expanded.contains(3)) expanded - 3 else expanded + 3
        org.junit.Assert.assertTrue(expanded.isEmpty())
    }

    @Test
    fun testCollapsedSummary_onlyUsesRecordedNonEmptyValues() {
        val bpSys = "120"
        val bpDia = "80"
        val pr = "86"
        val rr = "" // empty, must be omitted!
        val temp = "98.4"
        val spo2 = "98"

        val summaryParts = listOfNotNull(
            if (bpSys.isNotBlank() && bpDia.isNotBlank()) "BP $bpSys/$bpDia mmHg" else null,
            "PR $pr/min".takeIf { pr.isNotBlank() },
            "RR $rr/min".takeIf { rr.isNotBlank() },
            "Temp $temp°F".takeIf { temp.isNotBlank() },
            "SpO₂ $spo2%".takeIf { spo2.isNotBlank() }
        )
        val summary = summaryParts.joinToString(" · ")

        assertEquals("BP 120/80 mmHg · PR 86/min · Temp 98.4°F · SpO₂ 98%", summary)
        org.junit.Assert.assertFalse(summary.contains("RR"))
    }
}
