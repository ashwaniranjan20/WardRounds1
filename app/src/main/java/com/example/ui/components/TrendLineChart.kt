package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TrendPoint
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DeepRoyalNavy
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.MutedSurface
import com.example.ui.theme.PeriwinkleBlue
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.StatusAlert
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun TrendLineChart(
    parameterName: String,
    unit: String,
    points: List<TrendPoint>,
    refRangeLow: Double? = null,
    refRangeHigh: Double? = null,
    refRangeText: String = "",
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .testTag("trend_line_chart_${parameterName.lowercase().replace(" ", "_")}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        // Chart Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Trend",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = parameterName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                    )
                    if (unit.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "($unit)",
                            fontSize = 13.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (refRangeText.isNotBlank()) {
                    Text(
                        text = "Ref Interval: $refRangeText",
                        fontSize = 11.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                    )
                }
            }

            // Legend
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PeriwinkleBlue)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Recorded", fontSize = 10.sp, color = SecondaryText)

                if (refRangeLow != null || refRangeHigh != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ElectricLime.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Normal Band", fontSize = 10.sp, color = SecondaryText)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MutedSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No laboratory observations available",
                    color = SecondaryText,
                    fontSize = 13.sp
                )
            }
            return
        }

        if (points.size == 1) {
            val single = points.first()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MutedSurface)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${single.value} $unit",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepRoyalNavy
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Recorded on ${single.date} at ${single.time}",
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PureWhite)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Notice",
                            tint = PeriwinkleBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Single measurement recorded. Additional data needed to assess trend.",
                            fontSize = 11.sp,
                            color = PrimaryText
                        )
                    }
                }
            }
            return
        }

        // Selected Data Point Tooltip Inspection Banner
        val activePoint = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
        if (activePoint != null) {
            val isOutOfRange = (refRangeLow != null && activePoint.value < refRangeLow) ||
                    (refRangeHigh != null && activePoint.value > refRangeHigh)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isOutOfRange) StatusAlert.copy(alpha = 0.12f) else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${activePoint.date} • ${activePoint.time}",
                        fontSize = 11.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Source: ${activePoint.dataSource}",
                        fontSize = 10.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${activePoint.value} $unit",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOutOfRange) StatusAlert else androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                    if (isOutOfRange) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (refRangeHigh != null && activePoint.value > refRangeHigh) "(HIGH)" else "(LOW)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusAlert
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Custom Canvas Line Plot with touch interaction and reference range band
        val sortedPoints = points.sortedWith(compareBy({ it.date }, { it.time }))
        val values = sortedPoints.map { it.value }
        val minDataVal = values.minOrNull() ?: 0.0
        val maxDataVal = values.maxOrNull() ?: 10.0

        val effectiveMin = min(minDataVal, refRangeLow ?: minDataVal)
        val effectiveMax = max(maxDataVal, refRangeHigh ?: maxDataVal)
        val paddingSpan = max((effectiveMax - effectiveMin) * 0.2, 1.0)
        val yMin = max(0.0, effectiveMin - paddingSpan)
        val yMax = effectiveMax + paddingSpan

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(sortedPoints) {
                        detectTapGestures { offset ->
                            val n = sortedPoints.size
                            if (n <= 1) return@detectTapGestures
                            val paddingLeft = 40f
                            val paddingRight = 20f
                            val plotWidth = size.width - paddingLeft - paddingRight
                            val stepX = plotWidth / (n - 1)

                            // Find closest point
                            var bestIndex = 0
                            var minDistance = Float.MAX_VALUE
                            for (i in 0 until n) {
                                val x = paddingLeft + i * stepX
                                val dist = kotlin.math.abs(offset.x - x)
                                if (dist < minDistance) {
                                    minDistance = dist
                                    bestIndex = i
                                }
                            }
                            if (minDistance < 60f) {
                                selectedIndex = bestIndex
                            }
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val paddingLeft = 45f
                val paddingRight = 25f
                val paddingTop = 25f
                val paddingBottom = 35f

                val plotWidth = canvasWidth - paddingLeft - paddingRight
                val plotHeight = canvasHeight - paddingTop - paddingBottom

                fun yCoord(value: Double): Float {
                    val norm = (value - yMin) / (yMax - yMin)
                    return (paddingTop + (1.0 - norm) * plotHeight).toFloat()
                }

                // Draw Reference Range Band if available
                if (refRangeLow != null || refRangeHigh != null) {
                    val bandTop = yCoord(refRangeHigh ?: effectiveMax)
                    val bandBottom = yCoord(refRangeLow ?: effectiveMin)
                    val bandHeight = bandBottom - bandTop
                    if (bandHeight > 0) {
                        drawRect(
                            color = ElectricLime.copy(alpha = 0.18f),
                            topLeft = Offset(paddingLeft, bandTop),
                            size = Size(plotWidth, bandHeight)
                        )
                        // Dashed reference lines
                        drawLine(
                            color = ElectricLime.copy(alpha = 0.8f),
                            start = Offset(paddingLeft, bandTop),
                            end = Offset(paddingLeft + plotWidth, bandTop),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                        drawLine(
                            color = ElectricLime.copy(alpha = 0.8f),
                            start = Offset(paddingLeft, bandBottom),
                            end = Offset(paddingLeft + plotWidth, bandBottom),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }
                }

                // Grid lines (3 horizontal levels)
                val gridSteps = 3
                for (i in 0..gridSteps) {
                    val y = paddingTop + (i.toFloat() / gridSteps) * plotHeight
                    drawLine(
                        color = CardBorder.copy(alpha = 0.6f),
                        start = Offset(paddingLeft, y),
                        end = Offset(paddingLeft + plotWidth, y),
                        strokeWidth = 1f
                    )
                }

                // Plot Trend Line
                val n = sortedPoints.size
                val stepX = plotWidth / (n - 1)
                val path = Path()

                val coords = mutableListOf<Offset>()
                for (i in 0 until n) {
                    val x = paddingLeft + i * stepX
                    val y = yCoord(sortedPoints[i].value)
                    coords.add(Offset(x, y))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                // Draw smooth stroke
                drawPath(
                    path = path,
                    color = PeriwinkleBlue,
                    style = Stroke(width = 4f)
                )

                // Draw observation nodes
                coords.forEachIndexed { i, offset ->
                    val isSelected = selectedIndex == i || (selectedIndex == null && i == n - 1)
                    val isAbnormal = (refRangeLow != null && sortedPoints[i].value < refRangeLow) ||
                            (refRangeHigh != null && sortedPoints[i].value > refRangeHigh)

                    val outerRadius = if (isSelected) 8f else 5f
                    val pointColor = if (isAbnormal) StatusAlert else DeepRoyalNavy

                    drawCircle(
                        color = PureWhite,
                        radius = outerRadius + 2f,
                        center = offset
                    )
                    drawCircle(
                        color = pointColor,
                        radius = outerRadius,
                        center = offset
                    )

                    if (isSelected) {
                        drawCircle(
                            color = PeriwinkleBlue.copy(alpha = 0.35f),
                            radius = outerRadius + 6f,
                            center = offset
                        )
                    }
                }
            }
        }

        // X-axis date labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            sortedPoints.forEachIndexed { idx, pt ->
                // Show condensed date
                val dateParts = pt.date.split("-")
                val shortDate = if (dateParts.size == 3) "${dateParts[2]}/${dateParts[1]}" else pt.date
                Text(
                    text = shortDate,
                    fontSize = 10.sp,
                    color = if (selectedIndex == idx) DeepRoyalNavy else SecondaryText,
                    fontWeight = if (selectedIndex == idx) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
