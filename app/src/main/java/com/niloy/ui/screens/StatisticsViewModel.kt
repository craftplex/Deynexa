package com.niloy.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.niloy.domain.model.*
import com.niloy.domain.repository.TaskRepository
import com.niloy.domain.service.SchedulingService
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private data class Tuple6<A, B, C, D, E, F>(
    val v1: A, val v2: B, val v3: C, val v4: D, val v5: E, val v6: F
)

@Immutable
data class DailyChartPoint(
    val dayLabel: String,
    val date: LocalDate,
    val totalCount: Int,
    val completedCount: Int,
    val completionRate: Float
)

@Immutable
data class HeatmapCell(
    val date: LocalDate,
    val totalTasks: Int,
    val completedTasks: Int,
    val intensity: Int // 0: None, 1: Low (1-49%), 2: Medium (50-79%), 3: High (80-100%)
)

@Immutable
data class CategoryStat(
    val category: Category,
    val totalCount: Int,
    val completedCount: Int,
    val completionRate: Float
)

@Immutable
data class PeriodStats(
    val total: Int = 0,
    val completed: Int = 0,
    val pending: Int = 0,
    val skipped: Int = 0,
    val completionRate: Float = 0f
)

@Immutable
data class StatisticsUiState(
    val totalCompletedAllTime: Int = 0,
    val totalPendingAllTime: Int = 0,
    val totalSkippedAllTime: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val bestDayName: String = "—",
    val peakFocusHourLabel: String = "10 AM - 12 PM",
    val smartSuggestionTip: String = "Your routine consistency is strong! Maintain your momentum.",
    val overallCompletionRate: Float = 0f,
    val productivityScore: Int = 85,
    val productivityRating: String = "High",
    val weeklyComparisonDelta: Int = 0,
    val todayStats: PeriodStats = PeriodStats(),
    val weekStats: PeriodStats = PeriodStats(),
    val monthStats: PeriodStats = PeriodStats(),
    val last7DaysPoints: List<DailyChartPoint> = emptyList(),
    val heatmapCells: List<HeatmapCell> = emptyList(),
    val categoryStats: List<CategoryStat> = emptyList(),
    val isLoading: Boolean = true
)

class StatisticsViewModel(
    private val repository: TaskRepository,
    private val schedulingService: SchedulingService
) : ViewModel() {

    private val _weekendDays = MutableStateFlow(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))

    init {
        viewModelScope.launch {
            val weekendStr = repository.getSetting("weekend_days") ?: "SATURDAY,SUNDAY"
            if (weekendStr.isNotBlank()) {
                val parsed = weekendStr.split(",").mapNotNull {
                    try { DayOfWeek.valueOf(it.trim()) } catch (e: Exception) { null }
                }.toSet()
                if (parsed.isNotEmpty()) {
                    _weekendDays.value = parsed
                }
            }
        }
    }

    val uiState: StateFlow<StatisticsUiState> = combine(
        repository.getTasks(),
        repository.getAllOccurrences(),
        repository.getCategories(),
        _weekendDays
    ) { tasks, allOccurrences, categories, weekendDays ->
        if (tasks.isEmpty()) {
            return@combine StatisticsUiState(isLoading = false)
        }

        withContext(Dispatchers.Default) {
            coroutineScope {
                val today = LocalDate.now()

                // Date tasks lookup cache to avoid recalculating the same date multiple times
                val dateTasksMap = java.util.concurrent.ConcurrentHashMap<LocalDate, List<com.niloy.domain.service.TaskWithOccurrence>>()

                fun getTasksFor(date: LocalDate): List<com.niloy.domain.service.TaskWithOccurrence> {
                    return dateTasksMap.getOrPut(date) {
                        schedulingService.getTasksForDate(
                            tasks = tasks,
                            occurrences = allOccurrences,
                            date = date,
                            customWeekend = weekendDays
                        )
                    }
                }

                // 1. Parallel Task: Today & Period Stats
                val periodStatsDeferred = async {
                    val todayTasks = getTasksFor(today)
                    val todayCompleted = todayTasks.count { it.occurrence.state == TaskState.COMPLETED }
                    val todayPending = todayTasks.count { it.occurrence.state == TaskState.PENDING }
                    val todaySkipped = todayTasks.count { it.occurrence.state == TaskState.SKIPPED }
                    val todayRate = if (todayTasks.isEmpty()) 0f else todayCompleted.toFloat() / todayTasks.size

                    val startOfWeek = today.with(DayOfWeek.MONDAY)
                    val fullWeekDays = (0..6).map { startOfWeek.plusDays(it.toLong()) }
                    var weekTotal = 0
                    var weekCompleted = 0
                    var weekPending = 0
                    var weekSkipped = 0
                    fullWeekDays.forEach { d ->
                        val dTasks = getTasksFor(d)
                        weekTotal += dTasks.size
                        weekCompleted += dTasks.count { it.occurrence.state == TaskState.COMPLETED }
                        weekPending += dTasks.count { it.occurrence.state == TaskState.PENDING }
                        weekSkipped += dTasks.count { it.occurrence.state == TaskState.SKIPPED }
                    }
                    val weekRate = if (weekTotal == 0) 0f else weekCompleted.toFloat() / weekTotal

                    val startOfMonth = today.withDayOfMonth(1)
                    val lengthOfMonth = today.lengthOfMonth()
                    val fullMonthDays = (0 until lengthOfMonth).map { startOfMonth.plusDays(it.toLong()) }
                    var monthTotal = 0
                    var monthCompleted = 0
                    var monthPending = 0
                    var monthSkipped = 0
                    fullMonthDays.forEach { d ->
                        val dTasks = getTasksFor(d)
                        monthTotal += dTasks.size
                        monthCompleted += dTasks.count { it.occurrence.state == TaskState.COMPLETED }
                        monthPending += dTasks.count { it.occurrence.state == TaskState.PENDING }
                        monthSkipped += dTasks.count { it.occurrence.state == TaskState.SKIPPED }
                    }
                    val monthRate = if (monthTotal == 0) 0f else monthCompleted.toFloat() / monthTotal

                    val prevWeekStart = startOfWeek.minusDays(7)
                    val prevWeekDays = (0..6).map { prevWeekStart.plusDays(it.toLong()) }
                    var prevWeekTotal = 0
                    var prevWeekCompleted = 0
                    prevWeekDays.forEach { d ->
                        val dTasks = getTasksFor(d)
                        prevWeekTotal += dTasks.size
                        prevWeekCompleted += dTasks.count { it.occurrence.state == TaskState.COMPLETED }
                    }
                    val prevWeekRate = if (prevWeekTotal == 0) 0f else prevWeekCompleted.toFloat() / prevWeekTotal
                    val weeklyDelta = ((weekRate - prevWeekRate) * 100).toInt()

                    Triple(
                        PeriodStats(todayTasks.size, todayCompleted, todayPending, todaySkipped, todayRate),
                        PeriodStats(weekTotal, weekCompleted, weekPending, weekSkipped, weekRate),
                        Tuple6(monthTotal, monthCompleted, monthPending, monthSkipped, monthRate, weeklyDelta)
                    )
                }

                // 2. Parallel Task: Last 7 Days & Heatmap Cells
                val chartAndHeatmapDeferred = async {
                    val last7Days = (6 downTo 0).map { today.minusDays(it.toLong()) }
                    val last7DaysPoints = last7Days.map { date ->
                        val dayTasks = getTasksFor(date)
                        val comp = dayTasks.count { it.occurrence.state == TaskState.COMPLETED }
                        val rate = if (dayTasks.isEmpty()) 0f else comp.toFloat() / dayTasks.size
                        DailyChartPoint(
                            dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            date = date,
                            totalCount = dayTasks.size,
                            completedCount = comp,
                            completionRate = rate
                        )
                    }

                    val heatmapDaysCount = 14 * 7
                    val heatmapStartDate = today.minusDays((heatmapDaysCount - 1).toLong())
                    val heatmapCells = (0 until heatmapDaysCount).map { offset ->
                        val date = heatmapStartDate.plusDays(offset.toLong())
                        val dayTasks = getTasksFor(date)
                        val comp = dayTasks.count { it.occurrence.state == TaskState.COMPLETED }
                        val rate = if (dayTasks.isEmpty()) 0f else comp.toFloat() / dayTasks.size
                        val intensity = when {
                            dayTasks.isEmpty() -> 0
                            comp == 0 -> 0
                            rate < 0.5f -> 1
                            rate < 0.8f -> 2
                            else -> 3
                        }
                        HeatmapCell(
                            date = date,
                            totalTasks = dayTasks.size,
                            completedTasks = comp,
                            intensity = intensity
                        )
                    }

                    Pair(last7DaysPoints, heatmapCells)
                }

                // 3. Parallel Task: Category Stats & Insights
                val categoryAndInsightsDeferred = async {
                    val insights = schedulingService.calculateProductivityInsights(
                        tasks = tasks,
                        allOccurrences = allOccurrences,
                        customWeekend = weekendDays
                    )

                    val categoryStatsList = categories.map { cat ->
                        var catTotalOccurrences = 0
                        var catCompOccurrences = 0
                        (0..30).forEach { offset ->
                            val d = today.minusDays(offset.toLong())
                            val dTasks = getTasksFor(d).filter { it.task.categoryId == cat.id }
                            catTotalOccurrences += dTasks.size
                            catCompOccurrences += dTasks.count { it.occurrence.state == TaskState.COMPLETED }
                        }
                        val rate = if (catTotalOccurrences == 0) 0f else catCompOccurrences.toFloat() / catTotalOccurrences
                        CategoryStat(
                            category = cat,
                            totalCount = catTotalOccurrences,
                            completedCount = catCompOccurrences,
                            completionRate = rate
                        )
                    }

                    Pair(insights, categoryStatsList)
                }

                // Await all parallel calculations
                val (todayStats, weekStats, monthTuple) = periodStatsDeferred.await()
                val (last7DaysPoints, heatmapCells) = chartAndHeatmapDeferred.await()
                val (insights, categoryStatsList) = categoryAndInsightsDeferred.await()

                val monthStats = PeriodStats(monthTuple.v1, monthTuple.v2, monthTuple.v3, monthTuple.v4, monthTuple.v5)
                val weeklyDelta = monthTuple.v6

                val rateFactor = (weekStats.completionRate * 0.35f + monthStats.completionRate * 0.20f) * 100f
                val streakFactor = (insights.currentStreak.coerceAtMost(10) / 10f) * 25f
                val todayFactor = todayStats.completionRate * 20f
                val productivityScore = if (monthStats.total == 0 && todayStats.total == 0) 0 else (rateFactor + streakFactor + todayFactor).toInt().coerceIn(0, 100)

                val productivityRating = when {
                    productivityScore >= 85 -> "Outstanding"
                    productivityScore >= 70 -> "High"
                    productivityScore >= 50 -> "Moderate"
                    else -> "Growing"
                }

                val allTimeCompleted = allOccurrences.count { it.state == TaskState.COMPLETED }
                val allTimeSkipped = allOccurrences.count { it.state == TaskState.SKIPPED }
                val allTimePending = allOccurrences.count { it.state == TaskState.PENDING }

                // Heuristic Peak Focus Hours & Smart Suggestions
                val peakHour = when {
                    insights.bestCompletionWeekday.contains("Mon", ignoreCase = true) || insights.bestCompletionWeekday.contains("Tue", ignoreCase = true) -> "09:00 AM - 11:30 AM"
                    insights.bestCompletionWeekday.contains("Sat", ignoreCase = true) || insights.bestCompletionWeekday.contains("Sun", ignoreCase = true) -> "04:00 PM - 06:30 PM"
                    else -> "10:00 AM - 01:00 PM"
                }

                val smartTip = when {
                    productivityScore >= 85 -> "Outstanding consistency! Keep up your peak focus routines."
                    productivityScore >= 70 -> "High momentum. Consider scheduling deep work during $peakHour."
                    todayStats.skipped > 0 -> "You skipped ${todayStats.skipped} routine(s) today. Reschedule them to less busy hours?"
                    else -> "Build small daily wins! Completing 1-2 routines early boosts daily momentum."
                }

                StatisticsUiState(
                    totalCompletedAllTime = allTimeCompleted,
                    totalPendingAllTime = allTimePending,
                    totalSkippedAllTime = allTimeSkipped,
                    currentStreak = insights.currentStreak,
                    bestStreak = insights.longestRecurringStreak,
                    bestDayName = insights.bestCompletionWeekday.ifBlank { "Every Day" },
                    peakFocusHourLabel = peakHour,
                    smartSuggestionTip = smartTip,
                    overallCompletionRate = monthStats.completionRate,
                    productivityScore = productivityScore,
                    productivityRating = productivityRating,
                    weeklyComparisonDelta = weeklyDelta,
                    todayStats = todayStats,
                    weekStats = weekStats,
                    monthStats = monthStats,
                    last7DaysPoints = last7DaysPoints,
                    heatmapCells = heatmapCells,
                    categoryStats = categoryStatsList,
                    isLoading = false
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState()
    )

    class Factory(
        private val repository: TaskRepository,
        private val schedulingService: SchedulingService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatisticsViewModel(repository, schedulingService) as T
        }
    }
}
