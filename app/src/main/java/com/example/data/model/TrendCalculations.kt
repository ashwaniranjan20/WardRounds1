package com.example.data.model

import java.util.Locale
import kotlin.math.abs

data class TrendPoint(
    val date: String,
    val time: String,
    val value: Double,
    val unit: String,
    val refRangeLow: Double?,
    val refRangeHigh: Double?,
    val isReviewed: Boolean,
    val dataSource: String
)

data class ParameterTrendSummary(
    val parameterName: String,
    val category: String,
    val unit: String,
    val latestValue: Double?,
    val previousValue: Double?,
    val latestDate: String?,
    val previousDate: String?,
    val absoluteChange: Double?,
    val percentageChange: Double?,
    val direction: TrendDirection,
    val refRangeStatus: ReferenceRangeStatus,
    val classification: ClinicalChangeClassification,
    val refRangeText: String,
    val historyPoints: List<TrendPoint>
) {
    val formattedAbsoluteChange: String
        get() {
            val change = absoluteChange ?: return "N/A"
            val sign = if (change > 0) "+" else ""
            return String.format(Locale.US, "%s%.2f %s", sign, change, unit)
        }

    val formattedPercentageChange: String
        get() {
            val pct = percentageChange ?: return "N/A"
            val sign = if (pct > 0) "+" else ""
            return String.format(Locale.US, "%s%.1f%%", sign, pct)
        }
}

object TrendEngine {

    fun calculateTrend(
        parameterName: String,
        category: String,
        unit: String,
        results: List<InvestigationResultEntity>
    ): ParameterTrendSummary {
        // Filter results that have numeric values and match this parameter/unit
        val validResults = results
            .filter { it.parameterName.equals(parameterName, ignoreCase = true) && it.numericValue != null }
            .sortedWith(compareBy({ it.measurementDate }, { it.measurementTime }))

        if (validResults.isEmpty()) {
            return ParameterTrendSummary(
                parameterName = parameterName,
                category = category,
                unit = unit,
                latestValue = null,
                previousValue = null,
                latestDate = null,
                previousDate = null,
                absoluteChange = null,
                percentageChange = null,
                direction = TrendDirection.INSUFFICIENT_DATA,
                refRangeStatus = ReferenceRangeStatus.NO_REFERENCE_RANGE,
                classification = ClinicalChangeClassification.INSUFFICIENT_DATA,
                refRangeText = "",
                historyPoints = emptyList()
            )
        }

        val points = validResults.map {
            TrendPoint(
                date = it.measurementDate,
                time = it.measurementTime,
                value = it.numericValue!!,
                unit = it.unit,
                refRangeLow = it.refRangeLow,
                refRangeHigh = it.refRangeHigh,
                isReviewed = it.isReviewed,
                dataSource = it.dataSource
            )
        }

        val latest = validResults.last()
        val latestVal = latest.numericValue!!
        val latestDate = "${latest.measurementDate} ${latest.measurementTime}"

        val refRangeLow = latest.refRangeLow
        val refRangeHigh = latest.refRangeHigh
        val refRangeStatus = when {
            refRangeLow == null && refRangeHigh == null -> ReferenceRangeStatus.NO_REFERENCE_RANGE
            refRangeLow != null && latestVal < refRangeLow -> ReferenceRangeStatus.BELOW_REFERENCE_RANGE
            refRangeHigh != null && latestVal > refRangeHigh -> ReferenceRangeStatus.ABOVE_REFERENCE_RANGE
            else -> ReferenceRangeStatus.WITHIN_RANGE
        }

        if (validResults.size < 2) {
            return ParameterTrendSummary(
                parameterName = parameterName,
                category = category,
                unit = latest.unit.ifBlank { unit },
                latestValue = latestVal,
                previousValue = null,
                latestDate = latestDate,
                previousDate = null,
                absoluteChange = null,
                percentageChange = null,
                direction = TrendDirection.INSUFFICIENT_DATA,
                refRangeStatus = refRangeStatus,
                classification = ClinicalChangeClassification.INSUFFICIENT_DATA,
                refRangeText = latest.refRangeText,
                historyPoints = points
            )
        }

        val previous = validResults[validResults.size - 2]
        val prevVal = previous.numericValue!!
        val prevDate = "${previous.measurementDate} ${previous.measurementTime}"

        val absChange = latestVal - prevVal
        val pctChange: Double? = if (abs(prevVal) > 0.0001) {
            (absChange / abs(prevVal)) * 100.0
        } else {
            null // Avoid division by zero
        }

        val direction = when {
            abs(absChange) < 0.01 -> TrendDirection.STABLE
            absChange > 0 -> TrendDirection.INCREASING
            else -> TrendDirection.DECREASING
        }

        val classification = classifyChange(parameterName, direction, refRangeStatus, latestVal, prevVal)

        return ParameterTrendSummary(
            parameterName = parameterName,
            category = category,
            unit = latest.unit.ifBlank { unit },
            latestValue = latestVal,
            previousValue = prevVal,
            latestDate = latestDate,
            previousDate = prevDate,
            absoluteChange = absChange,
            percentageChange = pctChange,
            direction = direction,
            refRangeStatus = refRangeStatus,
            classification = classification,
            refRangeText = latest.refRangeText,
            historyPoints = points
        )
    }

    private fun classifyChange(
        name: String,
        direction: TrendDirection,
        refRangeStatus: ReferenceRangeStatus,
        latestVal: Double,
        prevVal: Double
    ): ClinicalChangeClassification {
        val lower = name.lowercase(Locale.US)
        return when {
            direction == TrendDirection.STABLE -> ClinicalChangeClassification.NEUTRAL
            // Inflammatory markers: CRP, Procalcitonin, ESR
            lower.contains("crp") || lower.contains("procalcitonin") || lower.contains("esr") -> {
                if (direction == TrendDirection.DECREASING) ClinicalChangeClassification.POTENTIALLY_FAVOURABLE
                else ClinicalChangeClassification.POTENTIALLY_CONCERNING
            }
            // WBC in infection
            lower.contains("wbc") || lower.contains("leukocyte") -> {
                if (direction == TrendDirection.DECREASING && refRangeStatus != ReferenceRangeStatus.BELOW_REFERENCE_RANGE) {
                    ClinicalChangeClassification.POTENTIALLY_FAVOURABLE
                } else if (direction == TrendDirection.INCREASING) {
                    ClinicalChangeClassification.POTENTIALLY_CONCERNING
                } else {
                    ClinicalChangeClassification.NEUTRAL
                }
            }
            // Hemoglobin / Platelets
            lower.contains("hemoglobin") || lower.contains("hb") || lower.contains("platelet") -> {
                if (direction == TrendDirection.INCREASING && refRangeStatus != ReferenceRangeStatus.ABOVE_REFERENCE_RANGE) {
                    ClinicalChangeClassification.POTENTIALLY_FAVOURABLE
                } else if (direction == TrendDirection.DECREASING) {
                    ClinicalChangeClassification.POTENTIALLY_CONCERNING
                } else {
                    ClinicalChangeClassification.NEUTRAL
                }
            }
            // Renal: Creatinine, Urea
            lower.contains("creatinine") || lower.contains("urea") || lower.contains("bun") -> {
                if (direction == TrendDirection.DECREASING) {
                    ClinicalChangeClassification.POTENTIALLY_FAVOURABLE
                } else {
                    ClinicalChangeClassification.POTENTIALLY_CONCERNING
                }
            }
            // Liver enzymes: AST, ALT, Bilirubin
            lower.contains("ast") || lower.contains("alt") || lower.contains("bilirubin") || lower.contains("sgot") || lower.contains("sgpt") -> {
                if (direction == TrendDirection.DECREASING) {
                    ClinicalChangeClassification.POTENTIALLY_FAVOURABLE
                } else {
                    ClinicalChangeClassification.POTENTIALLY_CONCERNING
                }
            }
            else -> ClinicalChangeClassification.NEUTRAL
        }
    }
}
