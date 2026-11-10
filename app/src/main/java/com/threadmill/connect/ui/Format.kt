package com.threadmill.connect.ui

import com.threadmill.connect.model.RunSummary
import com.threadmill.connect.model.WeekDistance
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

fun formatClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun formatDuration(seconds: Int): String = formatClock(seconds * 1000L)

fun formatSpeed(kmh: Double): String = "%.1f km/h".format(Locale.US, kmh)

fun formatDistance(meters: Double): String = if (meters >= 1000.0) {
    "%.2f km".format(Locale.US, meters / 1000.0)
} else {
    "%.0f m".format(Locale.US, meters)
}

fun formatWhen(epochMs: Long): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return date.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.US))
}

fun weeklyDistance(runs: List<RunSummary>, today: LocalDate = LocalDate.now()): List<WeekDistance> {
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11)
    return (0 until 12).map { offset ->
        val week = start.plusWeeks(offset.toLong())
        val next = week.plusWeeks(1)
        val distance = runs.filter { run ->
            val day = Instant.ofEpochMilli(run.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            !day.isBefore(week) && day.isBefore(next)
        }.sumOf { it.distanceMeters }
        WeekDistance(week.toEpochDay(), distance)
    }
}
