package com.threadmill.connect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.threadmill.connect.model.IntervalPlan
import com.threadmill.connect.model.SpeedSample
import com.threadmill.connect.model.WeekDistance

@Composable
fun ProfileChart(
    intervals: List<IntervalPlan>,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(84.dp)) {
        if (intervals.isEmpty()) return@Canvas
        val total = intervals.sumOf { it.durationMs }.toFloat().coerceAtLeast(1f)
        val maxSpeed = intervals.maxOf { it.speedKmh }.toFloat().coerceAtLeast(0.1f)
        var x = 0f
        intervals.forEach { interval ->
            val width = size.width * interval.durationMs / total
            val height = size.height * (interval.speedKmh.toFloat() / maxSpeed)
            drawRoundRect(
                color = stageColor(interval.stage),
                topLeft = Offset(x + 1f, size.height - height),
                size = Size((width - 2f).coerceAtLeast(1f), height),
                cornerRadius = CornerRadius(6f, 6f),
            )
            x += width
        }
        if (progress != null) {
            val marker = size.width * progress.coerceIn(0f, 1f)
            drawRect(color = Paper, topLeft = Offset(marker - 1.5f, 0f), size = Size(3f, size.height))
        }
    }
}

@Composable
fun SegmentBar(
    intervals: List<IntervalPlan>,
    index: Int,
    intervalProgress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        if (intervals.isEmpty()) return@Canvas
        val total = intervals.sumOf { it.durationMs }.toFloat().coerceAtLeast(1f)
        var x = 0f
        intervals.forEachIndexed { itemIndex, interval ->
            val width = size.width * interval.durationMs / total
            drawRoundRect(
                color = CardInk,
                topLeft = Offset(x + 1f, 0f),
                size = Size((width - 2f).coerceAtLeast(1f), size.height),
                cornerRadius = CornerRadius(8f, 8f),
            )
            val fill = when {
                itemIndex < index -> 1f
                itemIndex == index -> intervalProgress.coerceIn(0f, 1f)
                else -> 0f
            }
            if (fill > 0f) {
                drawRoundRect(
                    color = stageColor(interval.stage),
                    topLeft = Offset(x + 1f, 0f),
                    size = Size(((width - 2f) * fill).coerceAtLeast(1f), size.height),
                    cornerRadius = CornerRadius(8f, 8f),
                )
            }
            x += width
        }
    }
}

@Composable
fun SpeedChart(samples: List<SpeedSample>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (samples.size < 2) return@Canvas
        val maxSpeed = samples.maxOf { it.speedKmh }.toFloat().coerceAtLeast(0.1f)
        val maxTime = samples.maxOf { it.offsetSec }.toFloat().coerceAtLeast(1f)
        val points = samples.map { sample ->
            Offset(
                x = size.width * sample.offsetSec / maxTime,
                y = size.height - size.height * (sample.speedKmh.toFloat() / maxSpeed),
            )
        }
        for (index in 0 until points.lastIndex) {
            drawLine(Accent, points[index], points[index + 1], strokeWidth = 4f)
        }
    }
}

@Composable
fun WeekChart(weeks: List<WeekDistance>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (weeks.isEmpty()) return@Canvas
        val maxDistance = weeks.maxOf { it.distanceMeters }.toFloat().coerceAtLeast(1f)
        val gap = 8f
        val bar = (size.width - gap * (weeks.size - 1)) / weeks.size
        weeks.forEachIndexed { index, week ->
            val height = size.height * (week.distanceMeters.toFloat() / maxDistance)
            drawRoundRect(
                color = if (index == weeks.lastIndex) Accent else Teal,
                topLeft = Offset(index * (bar + gap), size.height - height),
                size = Size(bar, height.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(8f, 8f),
            )
        }
    }
}
