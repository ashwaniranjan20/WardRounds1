package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClinicalChangeClassification
import com.example.data.model.ParameterTrendSummary
import com.example.data.model.ReferenceRangeStatus
import com.example.data.model.TrendDirection
import com.example.ui.components.TrendLineChart
import com.example.ui.components.WardRoundTopBar
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DeepRoyalNavy
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.MutedSurface
import com.example.ui.theme.PeriwinkleBlue
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SoftCloudWhite
import com.example.ui.theme.StatusAlert
import com.example.ui.theme.StatusPending
import com.example.ui.viewmodel.WardRoundViewModel
import kotlin.math.abs

enum class TrendCategoryFilter {
    ALL,
    FAVOURABLE,
    CONCERNING,
    OUTSIDE_REF_RANGE,
    INSUFFICIENT_DATA
}

enum class TrendSortBy {
    MOST_RECENT,
    LARGEST_PERCENT_CHANGE,
    LARGEST_ABSOLUTE_CHANGE,
    NAME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendDashboardTab(
    patientId: String,
    viewModel: WardRoundViewModel,
    modifier: Modifier = Modifier
) {
    val trends by viewModel.getTrendsForPatient(patientId).collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedCategory by remember { mutableStateOf(TrendCategoryFilter.ALL) }
    var sortBy by remember { mutableStateOf(TrendSortBy.MOST_RECENT) }
    var selectedTrendForChart by remember { mutableStateOf<ParameterTrendSummary?>(null) }

    // Counts for the 4 Mandatory Dashboard Summary Cards (PDF page 22)
    val favourableCount = remember(trends) {
        trends.count { it.classification == ClinicalChangeClassification.POTENTIALLY_FAVOURABLE }
    }
    val concerningCount = remember(trends) {
        trends.count { it.classification == ClinicalChangeClassification.POTENTIALLY_CONCERNING }
    }
    val outsideRefRangeCount = remember(trends) {
        trends.count { it.refRangeStatus in listOf(ReferenceRangeStatus.ABOVE_REFERENCE_RANGE, ReferenceRangeStatus.BELOW_REFERENCE_RANGE) }
    }
    val insufficientDataCount = remember(trends) {
        trends.count { it.direction == TrendDirection.INSUFFICIENT_DATA }
    }

    // Filtered and sorted parameter cards
    val displayedTrends = remember(trends, selectedCategory, sortBy) {
        val filtered = when (selectedCategory) {
            TrendCategoryFilter.ALL -> trends
            TrendCategoryFilter.FAVOURABLE -> trends.filter { it.classification == ClinicalChangeClassification.POTENTIALLY_FAVOURABLE }
            TrendCategoryFilter.CONCERNING -> trends.filter { it.classification == ClinicalChangeClassification.POTENTIALLY_CONCERNING }
            TrendCategoryFilter.OUTSIDE_REF_RANGE -> trends.filter { it.refRangeStatus in listOf(ReferenceRangeStatus.ABOVE_REFERENCE_RANGE, ReferenceRangeStatus.BELOW_REFERENCE_RANGE) }
            TrendCategoryFilter.INSUFFICIENT_DATA -> trends.filter { it.direction == TrendDirection.INSUFFICIENT_DATA }
        }

        when (sortBy) {
            TrendSortBy.MOST_RECENT -> filtered.sortedByDescending { it.latestDate ?: "" }
            TrendSortBy.LARGEST_PERCENT_CHANGE -> filtered.sortedByDescending { abs(it.percentageChange ?: 0.0) }
            TrendSortBy.LARGEST_ABSOLUTE_CHANGE -> filtered.sortedByDescending { abs(it.absoluteChange ?: 0.0) }
            TrendSortBy.NAME -> filtered.sortedBy { it.parameterName }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 20 Dashboard 4 Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Favourable Changes
            TrendSummaryCard(
                title = "Potentially Favourable",
                count = favourableCount,
                subtitle = "Improving trajectory",
                accentColor = ElectricLime,
                selected = selectedCategory == TrendCategoryFilter.FAVOURABLE,
                onClick = {
                    selectedCategory = if (selectedCategory == TrendCategoryFilter.FAVOURABLE) TrendCategoryFilter.ALL else TrendCategoryFilter.FAVOURABLE
                },
                modifier = Modifier.weight(1f)
            )

            // Card 2: Concerning Changes
            TrendSummaryCard(
                title = "Potentially Concerning",
                count = concerningCount,
                subtitle = "Requires vigilance",
                accentColor = StatusAlert,
                selected = selectedCategory == TrendCategoryFilter.CONCERNING,
                onClick = {
                    selectedCategory = if (selectedCategory == TrendCategoryFilter.CONCERNING) TrendCategoryFilter.ALL else TrendCategoryFilter.CONCERNING
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: Results Outside Ref Range
            TrendSummaryCard(
                title = "Outside Ref Range",
                count = outsideRefRangeCount,
                subtitle = "High / Low vs Lab Norm",
                accentColor = StatusPending,
                selected = selectedCategory == TrendCategoryFilter.OUTSIDE_REF_RANGE,
                onClick = {
                    selectedCategory = if (selectedCategory == TrendCategoryFilter.OUTSIDE_REF_RANGE) TrendCategoryFilter.ALL else TrendCategoryFilter.OUTSIDE_REF_RANGE
                },
                modifier = Modifier.weight(1f)
            )

            // Card 4: Insufficient Data
            TrendSummaryCard(
                title = "Insufficient Data",
                count = insufficientDataCount,
                subtitle = "< 2 measurements",
                accentColor = SecondaryText,
                selected = selectedCategory == TrendCategoryFilter.INSUFFICIENT_DATA,
                onClick = {
                    selectedCategory = if (selectedCategory == TrendCategoryFilter.INSUFFICIENT_DATA) TrendCategoryFilter.ALL else TrendCategoryFilter.INSUFFICIENT_DATA
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Sorting row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tracked Parameters (${displayedTrends.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = DeepRoyalNavy
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sort, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                FilterChip(
                    selected = sortBy == TrendSortBy.LARGEST_PERCENT_CHANGE,
                    onClick = {
                        sortBy = if (sortBy == TrendSortBy.LARGEST_PERCENT_CHANGE) TrendSortBy.MOST_RECENT else TrendSortBy.LARGEST_PERCENT_CHANGE
                    },
                    label = { Text("% Change", fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // Clinical Safety Rule Note (PDF page 21)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MutedSurface)
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = PeriwinkleBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clinical Safety Rule: Trend direction describes numerical changes. Interpretation depends on clinical context and patient goals.",
                    fontSize = 11.sp,
                    color = SecondaryText,
                    lineHeight = 15.sp
                )
            }
        }

        // Parameter cards list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayedTrends, key = { it.parameterName }) { trend ->
                ParameterTrendCard(
                    trend = trend,
                    onViewFullTrend = { selectedTrendForChart = trend }
                )
            }
        }
    }

    // Modal Sheet showing full interactive chart
    selectedTrendForChart?.let { trend ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val latestPt = trend.historyPoints.lastOrNull()

        ModalBottomSheet(
            onDismissRequest = { selectedTrendForChart = null },
            sheetState = sheetState,
            containerColor = SoftCloudWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${trend.parameterName} Serial Trend",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepRoyalNavy
                        )
                        Text(
                            text = "Category: ${trend.category} • Unit: ${trend.unit}",
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                    }

                    IconButton(onClick = { selectedTrendForChart = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Interactive Chart Component
                TrendLineChart(
                    parameterName = trend.parameterName,
                    unit = trend.unit,
                    points = trend.historyPoints,
                    refRangeLow = latestPt?.refRangeLow,
                    refRangeHigh = latestPt?.refRangeHigh,
                    refRangeText = trend.refRangeText
                )

                // Date-wise History Table (PDF page 18)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Chronological Observations (${trend.historyPoints.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = DeepRoyalNavy
                        )

                        trend.historyPoints.reversed().forEach { pt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MutedSurface)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${pt.date} at ${pt.time}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryText
                                    )
                                    Text(
                                        text = pt.dataSource,
                                        fontSize = 10.sp,
                                        color = SecondaryText
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${pt.value} ${pt.unit}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepRoyalNavy
                                    )
                                    if (pt.isReviewed) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Reviewed",
                                            tint = DeepRoyalNavy,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ParameterTrendCard(
    trend: ParameterTrendSummary,
    onViewFullTrend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("trend_card_${trend.parameterName.lowercase().replace(" ", "_")}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Parameter Name + Category & Classification Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trend.parameterName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepRoyalNavy
                    )
                    Text(
                        text = "${trend.category} • ${trend.unit}",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }

                // Classification Badge
                when (trend.classification) {
                    ClinicalChangeClassification.POTENTIALLY_FAVOURABLE -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricLime.copy(alpha = 0.35f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Favourable",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepRoyalNavy
                            )
                        }
                    }
                    ClinicalChangeClassification.POTENTIALLY_CONCERNING -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusAlert.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Concerning",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusAlert
                            )
                        }
                    }
                    ClinicalChangeClassification.INSUFFICIENT_DATA -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE2E8F0))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Single Reading",
                                fontSize = 10.sp,
                                color = SecondaryText
                            )
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Values & Change Display (PDF page 22 example format)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Latest: ${trend.latestValue ?: "--"} ${trend.unit}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    if (trend.previousValue != null) {
                        Text(
                            text = "Previous: ${trend.previousValue} ${trend.unit}",
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                    } else {
                        Text(
                            text = "Previous: N/A",
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                    }
                }

                // Change & Direction
                if (trend.absoluteChange != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dirIcon = when (trend.direction) {
                                TrendDirection.INCREASING -> Icons.Default.TrendingUp
                                TrendDirection.DECREASING -> Icons.Default.TrendingDown
                                else -> Icons.Default.Remove
                            }
                            Icon(
                                imageVector = dirIcon,
                                contentDescription = null,
                                tint = if (trend.classification == ClinicalChangeClassification.POTENTIALLY_CONCERNING) StatusAlert else DeepRoyalNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = trend.formattedPercentageChange,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (trend.classification == ClinicalChangeClassification.POTENTIALLY_CONCERNING) StatusAlert else DeepRoyalNavy
                            )
                        }
                        Text(
                            text = trend.formattedAbsoluteChange,
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }
                }
            }

            // Reference Range Tag
            if (trend.refRangeText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ref: ${trend.refRangeText}",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                    if (trend.refRangeStatus == ReferenceRangeStatus.ABOVE_REFERENCE_RANGE) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "▲ Above Normal", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusAlert)
                    } else if (trend.refRangeStatus == ReferenceRangeStatus.BELOW_REFERENCE_RANGE) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "▼ Below Normal", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusAlert)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: View Full Trend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onViewFullTrend,
                    modifier = Modifier
                        .testTag("view_full_trend_${trend.parameterName.lowercase().replace(" ", "_")}")
                        .height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PeriwinkleBlue)
                ) {
                    Icon(Icons.Default.Timeline, contentDescription = null, tint = PeriwinkleBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View Full Trend & Chart", fontSize = 11.sp, color = DeepRoyalNavy, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TrendSummaryCard(
    title: String,
    count: Int,
    subtitle: String,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag("trend_summary_card_${title.lowercase().replace(" ", "_")}")
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accentColor.copy(alpha = 0.15f) else PureWhite
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) accentColor else CardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = SecondaryText,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = DeepRoyalNavy
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = SecondaryText,
                maxLines = 1
            )
        }
    }
}
