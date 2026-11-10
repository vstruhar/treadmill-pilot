package com.threadmill.connect.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.threadmill.connect.TreadmillApp
import com.threadmill.connect.run.RunPhase
import com.threadmill.connect.run.RunUiState

@Composable
fun RunScreen(app: TreadmillApp, nav: NavHostController) {
    val state by app.runController.ui.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { nav.popBackStack() }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(formatClock(state.totalRemainingMs), color = Paper, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(state.workoutName, color = Mist, modifier = Modifier.padding(bottom = 8.dp))
        val progress = intervalProgress(state)
        SegmentBar(state.intervals, state.index, progress)
        Text(
            state.nextStage?.let { "NEXT ${it.label}" } ?: "Last interval",
            color = Accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        state.message?.let { Text(it, color = Coral, modifier = Modifier.padding(bottom = 8.dp)) }
        Ring(
            label = when (state.phase) {
                RunPhase.Countdown -> formatClock(state.countdownMs)
                RunPhase.Paused -> "Paused"
                RunPhase.Reconnecting -> "Link"
                RunPhase.Preparing -> "…"
                else -> formatClock(state.intervalRemainingMs)
            },
            caption = state.stage?.label ?: "",
            fraction = if (state.phase == RunPhase.Countdown) state.countdownMs / 3000f else progress,
            foot = if (state.count == 0) "" else "${state.index + 1}/${state.count}",
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SpeedCard("NOW", formatSpeed(state.targetSpeedKmh), Modifier.weight(1f))
            SpeedCard("NEXT", state.nextSpeedKmh?.let(::formatSpeed) ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SpeedCard("Speed", formatSpeed(state.liveSpeedKmh ?: state.targetSpeedKmh), Modifier.weight(1f))
            SpeedCard("Distance", formatDistance(state.distanceMeters), Modifier.weight(1f))
            SpeedCard("Time", formatClock(state.elapsedMs), Modifier.weight(1f))
        }
        state.heartRate?.let {
            Text("$it bpm", color = Coral, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        ProfileChart(
            intervals = state.intervals,
            progress = if (state.intervals.isEmpty()) null else {
                val total = state.intervals.sumOf { it.durationMs }.toFloat().coerceAtLeast(1f)
                (state.elapsedMs / total).coerceIn(0f, 1f)
            },
            modifier = Modifier.weight(1f),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = app.runController::skip, modifier = Modifier.weight(1f), enabled = state.phase == RunPhase.Running || state.phase == RunPhase.Countdown) {
                Text("Skip", color = Paper)
            }
            OutlinedButton(
                onClick = { if (state.phase == RunPhase.Paused) app.runController.resume() else app.runController.pause() },
                modifier = Modifier.weight(1f),
                enabled = state.phase == RunPhase.Running || state.phase == RunPhase.Countdown || state.phase == RunPhase.Paused,
            ) {
                Text(if (state.phase == RunPhase.Paused) "Resume" else "Pause", color = Paper)
            }
            Button(
                onClick = { confirmStop = true },
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink),
            ) { Text("Stop") }
        }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop this run?") },
            text = { Text("The belt will stop and the run will be saved.") },
            confirmButton = {
                Button(onClick = {
                    confirmStop = false
                    app.runController.stop()
                }) { Text("Stop") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmStop = false }) { Text("Keep going") }
            },
        )
    }
}

private fun intervalProgress(state: RunUiState): Float {
    val duration = state.intervals.getOrNull(state.index)?.durationMs?.toFloat() ?: return 0f
    if (duration <= 0f) return 0f
    return (1f - state.intervalRemainingMs / duration).coerceIn(0f, 1f)
}

@Composable
private fun SpeedCard(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(CardInk).padding(12.dp)) {
        Text(label, color = Mist, fontSize = 12.sp)
        Text(value, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    }
}

@Composable
private fun Ring(label: String, caption: String, fraction: Float, foot: String, modifier: Modifier = Modifier) {
    Box(modifier.height(210.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = CardInk,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = Accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(caption, color = Mist)
            Text(label, color = Paper, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
            Text(foot, color = Mist)
        }
    }
}
