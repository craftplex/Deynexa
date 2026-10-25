package com.niloy.domain.service

import com.niloy.domain.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class TaskWithOccurrence(
    val task: Task,
    val occurrence: TaskOccurrence,
    val effectiveStartTime: Long? = occurrence.rescheduledStartTime ?: task.startTime,
    val effectiveEndTime: Long? = occurrence.rescheduledEndTime ?: task.endTime
)

data class NextOccurrenceInfo(
    val date: LocalDate,
    val timeMinutes: Long?,
    val formattedLabel: String
)

class SchedulingService {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun getTasksForDate(
        tasks: List<Task>,
        occurrences: List<TaskOccurrence>,
        date: LocalDate,
        customWeekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    ): List<TaskWithOccurrence> {
        val dateString = date.format(dateFormatter)

        val occurrencesByTask = occurrences.groupBy { it.taskId }

        val scheduledTasks = tasks.filter { task ->
            if (!task.isEnabled) return@filter false

            val taskOccurrences = occurrencesByTask[task.id] ?: emptyList()
            val directOccurrence = taskOccurrences.find { it.date == dateString }

            // If an occurrence on this date was rescheduled to another date, exclude it
            if (directOccurrence?.rescheduledDate != null && directOccurrence.rescheduledDate != dateString) {
                return@filter false
            }

            // Check if another date's occurrence was rescheduled TO this date
            val rescheduledHere = taskOccurrences.any { it.rescheduledDate == dateString }
            if (rescheduledHere) {
                return@filter true
            }

            // Standard recurrence rule match
            isTaskScheduledOnDate(task, date, customWeekend)
        }

        return scheduledTasks.map { task ->
            val taskOccurrences = occurrencesByTask[task.id] ?: emptyList()
            val directOccurrence = taskOccurrences.find { it.date == dateString }
            val rescheduledHere = taskOccurrences.find { it.rescheduledDate == dateString }

            val effectiveOccurrence = rescheduledHere ?: directOccurrence ?: TaskOccurrence(
                taskId = task.id,
                date = dateString,
                state = TaskState.PENDING
            )

            TaskWithOccurrence(
                task = task,
                occurrence = effectiveOccurrence,
                effectiveStartTime = effectiveOccurrence.rescheduledStartTime ?: task.startTime,
                effectiveEndTime = effectiveOccurrence.rescheduledEndTime ?: task.endTime
            )
        }.sortedWith(
            compareBy<TaskWithOccurrence> { it.task.isAllDay }
                .thenBy { it.effectiveStartTime ?: Long.MAX_VALUE }
                .thenBy { it.task.name }
        )
    }

    fun isTaskScheduledOnDate(
        task: Task,
        date: LocalDate,
        customWeekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    ): Boolean {
        val startDate = parseStartDate(task)

        // Cannot occur before the task's start date
        if (date.isBefore(startDate)) {
            return false
        }

        // Check End Conditions
        when (task.recurrenceEndType) {
            RecurrenceEndType.ON_DATE -> {
                if (!task.recurrenceEndDate.isNullOrBlank()) {
                    try {
                        val endDate = LocalDate.parse(task.recurrenceEndDate)
                        if (date.isAfter(endDate)) return false
                    } catch (e: Exception) {
                        // ignore parsing error
                    }
                }
            }
            RecurrenceEndType.AFTER_OCCURRENCES -> {
                val maxCount = task.recurrenceCount ?: Int.MAX_VALUE
                val occurrenceIndex = countOccurrencesBetween(task, startDate, date, customWeekend)
                if (occurrenceIndex > maxCount) return false
            }
            RecurrenceEndType.NEVER -> {
                // No end
            }
        }

        // Check recurrence types
        val interval = maxOf(1, task.recurrenceInterval)

        return when {
            // One-time task
            !task.isRecurring || task.recurrenceType == RecurrenceType.NONE -> {
                date == startDate
            }

            // If task specifies specific recurringDays set (and type is SPECIFIC_DAYS or DAILY with recurringDays)
            task.recurrenceType == RecurrenceType.SPECIFIC_DAYS || (task.recurrenceType == RecurrenceType.DAILY && task.recurringDays.isNotEmpty()) -> {
                if (task.recurringDays.contains(date.dayOfWeek)) {
                    if (interval > 1) {
                        val startWeek = startDate.with(DayOfWeek.MONDAY)
                        val currentWeek = date.with(DayOfWeek.MONDAY)
                        val weeksBetween = ChronoUnit.WEEKS.between(startWeek, currentWeek)
                        weeksBetween % interval == 0L
                    } else {
                        true
                    }
                } else {
                    false
                }
            }

            // Every Day
            task.recurrenceType == RecurrenceType.DAILY -> {
                if (interval > 1) {
                    val daysBetween = ChronoUnit.DAYS.between(startDate, date)
                    daysBetween % interval == 0L
                } else {
                    true
                }
            }

            // Weekdays (Working days - excludes custom weekend)
            task.recurrenceType == RecurrenceType.WEEKDAYS -> {
                !customWeekend.contains(date.dayOfWeek)
            }

            // Weekends (Custom weekend days, max 2)
            task.recurrenceType == RecurrenceType.WEEKENDS -> {
                customWeekend.contains(date.dayOfWeek)
            }

            // Every X Days
            task.recurrenceType == RecurrenceType.EVERY_X_DAYS -> {
                val daysBetween = ChronoUnit.DAYS.between(startDate, date)
                daysBetween >= 0 && daysBetween % interval == 0L
            }

            // Every X Weeks
            task.recurrenceType == RecurrenceType.EVERY_X_WEEKS -> {
                val startWeek = startDate.with(DayOfWeek.MONDAY)
                val currentWeek = date.with(DayOfWeek.MONDAY)
                val weeksBetween = ChronoUnit.WEEKS.between(startWeek, currentWeek)
                if (weeksBetween >= 0 && weeksBetween % interval == 0L) {
                    if (task.recurringDays.isNotEmpty()) {
                        task.recurringDays.contains(date.dayOfWeek)
                    } else {
                        date.dayOfWeek == startDate.dayOfWeek
                    }
                } else {
                    false
                }
            }

            // Monthly (on same day of month)
            task.recurrenceType == RecurrenceType.MONTHLY -> {
                val targetDay = task.recurrenceDayOfMonth ?: startDate.dayOfMonth
                val effectiveDay = minOf(targetDay, date.lengthOfMonth())
                val monthsBetween = ChronoUnit.MONTHS.between(startDate.withDayOfMonth(1), date.withDayOfMonth(1))
                monthsBetween >= 0 && (monthsBetween % interval == 0L) && date.dayOfMonth == effectiveDay
            }

            // Specific Day of Month (e.g. 1st, 15th, 28th)
            task.recurrenceType == RecurrenceType.SPECIFIC_DAY_OF_MONTH -> {
                val targetDay = task.recurrenceDayOfMonth ?: 1
                val effectiveDay = minOf(targetDay, date.lengthOfMonth())
                val monthsBetween = ChronoUnit.MONTHS.between(startDate.withDayOfMonth(1), date.withDayOfMonth(1))
                monthsBetween >= 0 && (monthsBetween % interval == 0L) && date.dayOfMonth == effectiveDay
            }

            // Yearly (e.g. Birthday, Anniversary)
            task.recurrenceType == RecurrenceType.YEARLY -> {
                val targetMonth = task.recurrenceMonthOfYear ?: startDate.monthValue
                val targetDay = task.recurrenceDayOfMonth ?: startDate.dayOfMonth
                val maxDay = YearMonth.of(date.year, targetMonth).lengthOfMonth()
                val effectiveDay = minOf(targetDay, maxDay)
                val yearsBetween = (date.year - startDate.year).toLong()
                yearsBetween >= 0 && (yearsBetween % interval == 0L) && date.monthValue == targetMonth && date.dayOfMonth == effectiveDay
            }

            // Custom recurrence
            task.recurrenceType == RecurrenceType.CUSTOM -> {
                val daysBetween = ChronoUnit.DAYS.between(startDate, date)
                daysBetween >= 0 && daysBetween % interval == 0L
            }

            else -> false
        }
    }

    private fun countOccurrencesBetween(
        task: Task,
        startDate: LocalDate,
        targetDate: LocalDate,
        customWeekend: Set<DayOfWeek>
    ): Int {
        var count = 0
        var current = startDate
        while (!current.isAfter(targetDate)) {
            // Check without end condition to prevent infinite loop
            val isScheduled = checkRawScheduleMatch(task, current, startDate, customWeekend)
            if (isScheduled) {
                count++
            }
            current = current.plusDays(1)
        }
        return count
    }

    private fun checkRawScheduleMatch(
        task: Task,
        date: LocalDate,
        startDate: LocalDate,
        customWeekend: Set<DayOfWeek>
    ): Boolean {
        val interval = maxOf(1, task.recurrenceInterval)
        return when {
            !task.isRecurring || task.recurrenceType == RecurrenceType.NONE -> date == startDate
            task.recurrenceType == RecurrenceType.SPECIFIC_DAYS || (task.recurrenceType == RecurrenceType.DAILY && task.recurringDays.isNotEmpty()) -> task.recurringDays.contains(date.dayOfWeek)
            task.recurrenceType == RecurrenceType.DAILY -> ChronoUnit.DAYS.between(startDate, date) % interval == 0L
            task.recurrenceType == RecurrenceType.WEEKDAYS -> !customWeekend.contains(date.dayOfWeek)
            task.recurrenceType == RecurrenceType.WEEKENDS -> customWeekend.contains(date.dayOfWeek)
            task.recurrenceType == RecurrenceType.EVERY_X_DAYS -> ChronoUnit.DAYS.between(startDate, date) % interval == 0L
            task.recurrenceType == RecurrenceType.EVERY_X_WEEKS -> {
                val weeksBetween = ChronoUnit.WEEKS.between(startDate.with(DayOfWeek.MONDAY), date.with(DayOfWeek.MONDAY))
                weeksBetween % interval == 0L && (if (task.recurringDays.isNotEmpty()) task.recurringDays.contains(date.dayOfWeek) else date.dayOfWeek == startDate.dayOfWeek)
            }
            task.recurrenceType == RecurrenceType.MONTHLY || task.recurrenceType == RecurrenceType.SPECIFIC_DAY_OF_MONTH -> {
                val targetDay = task.recurrenceDayOfMonth ?: startDate.dayOfMonth
                val effectiveDay = minOf(targetDay, date.lengthOfMonth())
                val monthsBetween = ChronoUnit.MONTHS.between(startDate.withDayOfMonth(1), date.withDayOfMonth(1))
                monthsBetween % interval == 0L && date.dayOfMonth == effectiveDay
            }
            task.recurrenceType == RecurrenceType.YEARLY -> {
                val targetMonth = task.recurrenceMonthOfYear ?: startDate.monthValue
                val targetDay = task.recurrenceDayOfMonth ?: startDate.dayOfMonth
                val effectiveDay = minOf(targetDay, YearMonth.of(date.year, targetMonth).lengthOfMonth())
                date.monthValue == targetMonth && date.dayOfMonth == effectiveDay
            }
            else -> ChronoUnit.DAYS.between(startDate, date) % interval == 0L
        }
    }

    fun parseStartDate(task: Task): LocalDate {
        if (task.startDate.isNotBlank()) {
            try {
                return LocalDate.parse(task.startDate)
            } catch (e: Exception) {
                // ignore
            }
        }
        return LocalDate.ofEpochDay(task.createdAt / (24 * 60 * 60 * 1000))
    }

    fun findConflicts(
        existingTasks: List<TaskWithOccurrence>,
        targetTask: Task,
        targetStartTime: Long?,
        targetEndTime: Long?,
        isAllDay: Boolean = targetTask.isAllDay
    ): List<ScheduleConflict> {
        if (isAllDay || targetStartTime == null) return emptyList()
        val end = targetEndTime ?: (targetStartTime + 30) // default 30 min if no end time

        return existingTasks.filter { item ->
            item.task.id != targetTask.id && !item.task.isAllDay && item.effectiveStartTime != null
        }.mapNotNull { item ->
            val otherStart = item.effectiveStartTime!!
            val otherEnd = item.effectiveEndTime ?: (otherStart + 30)

            // Overlap condition: max(start1, start2) < min(end1, end2)
            val overlapStart = maxOf(targetStartTime, otherStart)
            val overlapEnd = minOf(end, otherEnd)

            if (overlapStart < overlapEnd) {
                val overlapMinutes = (overlapEnd - overlapStart).toInt()
                ScheduleConflict(
                    conflictingTask = item.task,
                    overlapMinutes = overlapMinutes,
                    message = "Conflicts with '${item.task.name}' (${overlapMinutes} min overlap)"
                )
            } else {
                null
            }
        }
    }

    fun calculateNextOccurrence(
        task: Task,
        fromDateTime: LocalDateTime = LocalDateTime.now(),
        customWeekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    ): NextOccurrenceInfo? {
        val today = fromDateTime.toLocalDate()
        val currentMinutes = fromDateTime.hour * 60L + fromDateTime.minute

        // Check if today matches and time is in the future
        if (isTaskScheduledOnDate(task, today, customWeekend)) {
            val taskStart = task.startTime
            if (taskStart == null || taskStart > currentMinutes || task.isAllDay) {
                return NextOccurrenceInfo(
                    date = today,
                    timeMinutes = task.startTime,
                    formattedLabel = "Today" + (if (task.startTime != null && !task.isAllDay) " at ${formatTimeShort(task.startTime)}" else "")
                )
            }
        }

        // Look ahead up to 365 days
        for (i in 1..365) {
            val candidateDate = today.plusDays(i.toLong())
            if (isTaskScheduledOnDate(task, candidateDate, customWeekend)) {
                val dayLabel = when (i) {
                    1 -> "Tomorrow"
                    in 2..6 -> candidateDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
                    else -> "${candidateDate.month.name.take(3)} ${candidateDate.dayOfMonth}"
                }
                val timeLabel = if (task.startTime != null && !task.isAllDay) " at ${formatTimeShort(task.startTime)}" else ""
                return NextOccurrenceInfo(
                    date = candidateDate,
                    timeMinutes = task.startTime,
                    formattedLabel = "$dayLabel$timeLabel"
                )
            }
        }
        return null
    }

    fun calculateProductivityInsights(
        tasks: List<Task>,
        allOccurrences: List<TaskOccurrence>,
        customWeekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    ): ProductivityInsights {
        if (tasks.isEmpty() || allOccurrences.isEmpty()) {
            return ProductivityInsights()
        }

        // Most completed task
        val completedByTask = allOccurrences.filter { it.state == TaskState.COMPLETED }
            .groupBy { it.taskId }
        val mostCompletedEntry = completedByTask.maxByOrNull { it.value.size }
        val mostCompletedTask = tasks.find { it.id == mostCompletedEntry?.key }
        val mostCompletedCount = mostCompletedEntry?.value?.size ?: 0

        // Most skipped task
        val skippedByTask = allOccurrences.filter { it.state == TaskState.SKIPPED }
            .groupBy { it.taskId }
        val mostSkippedEntry = skippedByTask.maxByOrNull { it.value.size }
        val mostSkippedTask = tasks.find { it.id == mostSkippedEntry?.key }
        val mostSkippedCount = mostSkippedEntry?.value?.size ?: 0

        // Day of week completion rates (last 90 days)
        val today = LocalDate.now()
        val occurrencesMap = allOccurrences.groupBy { it.date }
        val weekdayTotal = mutableMapOf<DayOfWeek, Int>()
        val weekdayCompleted = mutableMapOf<DayOfWeek, Int>()

        (0..90).forEach { offset ->
            val d = today.minusDays(offset.toLong())
            val dateTasks = getTasksForDate(tasks, occurrencesMap[d.toString()] ?: emptyList(), d, customWeekend)
            if (dateTasks.isNotEmpty()) {
                val day = d.dayOfWeek
                weekdayTotal[day] = (weekdayTotal[day] ?: 0) + dateTasks.size
                weekdayCompleted[day] = (weekdayCompleted[day] ?: 0) + dateTasks.count { it.occurrence.state == TaskState.COMPLETED }
            }
        }

        val weekdayRates = DayOfWeek.values().mapNotNull { day ->
            val total = weekdayTotal[day] ?: 0
            val comp = weekdayCompleted[day] ?: 0
            if (total > 0) {
                day to (comp.toFloat() / total)
            } else null
        }

        val bestDay = weekdayRates.maxByOrNull { it.second }
        val lowestDay = weekdayRates.minByOrNull { it.second }

        // Streaks & Longest Streak Calculation over the last 365 days
        var currentStreak = 0
        var longestStreak = 0
        var runningStreak = 0

        // Calculate current streak backward from today / yesterday
        var checkDate = today
        val todayTasks = getTasksForDate(tasks, occurrencesMap[today.toString()] ?: emptyList(), today, customWeekend)
        val todayComp = todayTasks.count { it.occurrence.state == TaskState.COMPLETED }
        if (todayTasks.isNotEmpty() && todayComp == 0) {
            checkDate = today.minusDays(1)
        }

        while (true) {
            val dTasks = getTasksForDate(tasks, occurrencesMap[checkDate.toString()] ?: emptyList(), checkDate, customWeekend)
            if (dTasks.isEmpty()) {
                if (customWeekend.contains(checkDate.dayOfWeek)) {
                    checkDate = checkDate.minusDays(1)
                    if (checkDate.isBefore(today.minusDays(365))) break
                    continue
                } else {
                    break
                }
            }
            val comp = dTasks.count { it.occurrence.state == TaskState.COMPLETED }
            if (comp > 0 && (comp.toFloat() / dTasks.size) >= 0.5f) {
                currentStreak++
                checkDate = checkDate.minusDays(1)
                if (checkDate.isBefore(today.minusDays(365))) break
            } else {
                break
            }
        }

        // Calculate overall longest streak forward over last 365 days
        (365 downTo 0).forEach { offset ->
            val d = today.minusDays(offset.toLong())
            val dTasks = getTasksForDate(tasks, occurrencesMap[d.toString()] ?: emptyList(), d, customWeekend)
            if (dTasks.isNotEmpty()) {
                val comp = dTasks.count { it.occurrence.state == TaskState.COMPLETED }
                if (comp > 0 && (comp.toFloat() / dTasks.size) >= 0.5f) {
                    runningStreak++
                    if (runningStreak > longestStreak) {
                        longestStreak = runningStreak
                    }
                } else {
                    runningStreak = 0
                }
            } else if (!customWeekend.contains(d.dayOfWeek)) {
                runningStreak = 0
            }
        }

        return ProductivityInsights(
            mostCompletedTaskName = mostCompletedTask?.name ?: "—",
            mostCompletedTaskCount = mostCompletedCount,
            mostSkippedTaskName = mostSkippedTask?.name ?: "—",
            mostSkippedTaskCount = mostSkippedCount,
            bestCompletionWeekday = bestDay?.first?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—",
            bestCompletionWeekdayRate = bestDay?.second ?: 0f,
            lowestCompletionWeekday = lowestDay?.first?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—",
            lowestCompletionWeekdayRate = lowestDay?.second ?: 0f,
            currentStreak = currentStreak,
            longestRecurringStreak = maxOf(currentStreak, longestStreak)
        )
    }

    private fun formatTimeShort(minutes: Long): String {
        val normalized = (minutes % 1440).toInt()
        val hours = normalized / 60
        val mins = normalized % 60
        val period = if (hours < 12) "AM" else "PM"
        val displayHour = when (hours) {
            0 -> 12
            in 1..12 -> hours
            else -> hours - 12
        }
        return String.format(java.util.Locale.getDefault(), "%d:%02d %s", displayHour, mins, period)
    }
}
