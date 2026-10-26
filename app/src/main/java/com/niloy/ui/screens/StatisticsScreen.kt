package com.niloy.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niloy.ui.components.EmptyStateView
import com.niloy.ui.theme.AccentPrimary
import com.niloy.ui.theme.StateCompleted
import com.niloy.ui.theme.StatePending
import com.niloy.ui.theme.StateSkipped
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedPeriodTab by remember { mutableStateOf(1) } // 0: Today, 1: Week, 2: Month

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analytics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.last7DaysPoints.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.Insights,
                title = "No statistics available yet",
                subtitle = "Start scheduling and completing daily tasks to unlock rich analytics and consistency heatmaps.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Productivity Score Hero Card
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Productivity Score",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Consistency & execution",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                ) {
                                    Text(
                                        text = uiState.productivityRating,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${uiState.productivityScore}",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "/ 100",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                if (uiState.weeklyComparisonDelta != 0) {
                                    val isPositive = uiState.weeklyComparisonDelta > 0
                                    Text(
                                        text = if (isPositive) "▲ +${uiState.weeklyComparisonDelta}% vs last week" else "▼ ${uiState.weeklyComparisonDelta}% vs last week",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isPositive) StateCompleted else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { uiState.productivityScore / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                // Top KPI Metric Cards Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiCard(
                                title = "Current Streak",
                                value = "${uiState.currentStreak} Days",
                                subtitle = "Best: ${uiState.bestStreak} days",
                                icon = Icons.Outlined.LocalFireDepartment,
                                accentColor = Color(0xFFF97316),
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                title = "Success Rate",
                                value = "${(uiState.overallCompletionRate * 100).toInt()}%",
                                subtitle = "This month's pace",
                                icon = Icons.Outlined.CheckCircle,
                                accentColor = StateCompleted,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiCard(
                                title = "Best Day",
                                value = uiState.bestDayName,
                                subtitle = "Peak consistency",
                                icon = Icons.Outlined.Star,
                                accentColor = AccentPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                title = "Completed",
                                value = "${uiState.totalCompletedAllTime}",
                                subtitle = "All-time completions",
                                icon = Icons.Outlined.Timeline,
                                accentColor = Color(0xFF8B5CF6),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Local Smart AI Recommendation Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Insights,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Smart Routine Insight",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = uiState.smartSuggestionTip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Completion Overview Tabbed Card
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Completion Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Responsive Segmented period selector
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SegmentedButton(
                                    selected = selectedPeriodTab == 0,
                                    onClick = { selectedPeriodTab = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Today", style = MaterialTheme.typography.labelMedium)
                                }
                                SegmentedButton(
                                    selected = selectedPeriodTab == 1,
                                    onClick = { selectedPeriodTab = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Week", style = MaterialTheme.typography.labelMedium)
                                }
                                SegmentedButton(
                                    selected = selectedPeriodTab == 2,
                                    onClick = { selectedPeriodTab = 2 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Month", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            val activeStats = when (selectedPeriodTab) {
                                0 -> uiState.todayStats
                                1 -> uiState.weekStats
                                else -> uiState.monthStats
                            }

                            // Progress row with percentage
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${(activeStats.completionRate * 100).toInt()}% Done",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeStats.completionRate >= 1f && activeStats.total > 0) StateCompleted else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${activeStats.completed} of ${activeStats.total} tasks",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { activeStats.completionRate },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = if (activeStats.completionRate >= 1f && activeStats.total > 0) StateCompleted else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatCounterBox(
                                    label = "Completed",
                                    count = activeStats.completed,
                                    color = StateCompleted,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCounterBox(
                                    label = "Pending",
                                    count = activeStats.pending,
                                    color = StatePending,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCounterBox(
                                    label = "Skipped",
                                    count = activeStats.skipped,
                                    color = StateSkipped,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 7-Day Performance Chart
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Last 7 Days Performance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            WeeklyBarChart(points = uiState.last7DaysPoints)
                        }
                    }
                }

                // Consistency Heatmap Card
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Routine Consistency Heatmap",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "14 Weeks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HeatmapGrid(cells = uiState.heatmapCells)
                        }
                    }
                }

                // Categories Breakdown
                if (uiState.categoryStats.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Category Completion",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                uiState.categoryStats.forEach { catStat ->
                                    CategoryProgressRow(stat = catStat)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun StatCounterBox(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun WeeklyBarChart(points: List<DailyChartPoint>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEach { point ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                // Percentage or count text on top
                Text(
                    text = if (point.totalCount > 0) "${point.completedCount}/${point.totalCount}" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Bar track and fill
                val fillFraction = if (point.totalCount == 0) 0.05f else maxOf(point.completionRate, 0.05f)
                val animatedFraction by animateFloatAsState(
                    targetValue = fillFraction,
                    animationSpec = tween(durationMillis = 600),
                    label = "bar_fill"
                )

                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(70.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(animatedFraction)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                when {
                                    point.totalCount == 0 -> Color.Transparent
                                    point.completionRate >= 1f -> StateCompleted
                                    point.completedCount > 0 -> MaterialTheme.colorScheme.primary
                                    else -> Color.Transparent
                                }
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Day Label
                Text(
                    text = point.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun HeatmapGrid(cells: List<HeatmapCell>) {
    val columns = cells.chunked(7)
    val weekdays = listOf("M", "", "W", "", "F", "", "")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(end = 2.dp)
            ) {
                weekdays.forEach { day ->
                    Box(
                        modifier = Modifier.size(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day.isNotEmpty()) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                columns.forEach { columnDays ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        columnDays.forEach { cell ->
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(getHeatmapColor(cell.intensity))
                            )
                        }
                    }
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Less",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0..3) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(getHeatmapColor(i))
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "More",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun getHeatmapColor(intensity: Int): Color {
    return when (intensity) {
        0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        1 -> StateCompleted.copy(alpha = 0.35f)
        2 -> StateCompleted.copy(alpha = 0.65f)
        3 -> StateCompleted
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun CategoryProgressRow(stat: CategoryStat) {
    val categoryColor = Color(stat.category.color)
    val animatedProgress by animateFloatAsState(
        targetValue = stat.completionRate,
        animationSpec = tween(durationMillis = 500),
        label = "cat_progress"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
                Text(
                    text = stat.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "${(stat.completionRate * 100).toInt()}% (${stat.completedCount}/${stat.totalCount})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = categoryColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
