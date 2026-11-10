package com.threadmill.connect.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.threadmill.connect.TreadmillApp
import com.threadmill.connect.model.RunOutcome
import com.threadmill.connect.model.RunSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as TreadmillApp).repository
    val runs = repository.observeRuns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totals = repository.observeTotals().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.threadmill.connect.model.Totals(0, 0, 0.0),
    )

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteRun(id) }
    }
}

@Composable
fun HistoryScreen(nav: NavHostController, model: HistoryViewModel = viewModel()) {
    val runs by model.runs.collectAsState()
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("History", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("Every run, newest first.", color = Mist, modifier = Modifier.padding(bottom = 16.dp))
        if (runs.isEmpty()) {
            Text("Finish a workout and it will show up here.", color = Mist)
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(runs, key = { it.id }) { run ->
                RunRow(
                    run,
                    onClick = { nav.navigate("summary/${run.id}") },
                    onDelete = { pendingDelete = run.id },
                )
            }
        }
    }
    DeleteRunDialog(pendingDelete != null, onDismiss = { pendingDelete = null }) {
        pendingDelete?.let(model::delete)
        pendingDelete = null
    }
}

@Composable
private fun RunRow(run: RunSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardInk).clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row {
                Text(run.workoutName, color = Paper, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (run.outcome == RunOutcome.Stopped) Text("Stopped", color = Coral, fontSize = 12.sp)
            }
            Text(formatWhen(run.startedAt), color = Mist, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Text(
                "${formatDuration(run.durationSec)} · ${formatDistance(run.distanceMeters)}",
                color = Paper,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete run", tint = Coral)
        }
    }
}

@Composable
fun StatsScreen(nav: NavHostController, model: HistoryViewModel = viewModel()) {
    val totals by model.totals.collectAsState()
    val runs by model.runs.collectAsState()
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Stats", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        if (runs.isEmpty()) {
            item { Text("Finish a run to see your totals.", color = Mist) }
            return@LazyColumn
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Runs", totals.runs.toString(), Modifier.weight(1f))
                Stat("Time", formatDuration(totals.durationSec), Modifier.weight(1f))
                Stat("Distance", formatDistance(totals.distanceMeters), Modifier.weight(1f))
            }
        }
        item {
            Text("Last 12 weeks", color = Mist)
            WeekChart(weeklyDistance(runs))
        }
        item { Text("Runs", color = Accent, fontWeight = FontWeight.SemiBold) }
        items(runs, key = { it.id }) { run ->
            RunRow(
                run,
                onClick = { nav.navigate("summary/${run.id}") },
                onDelete = { pendingDelete = run.id },
            )
        }
    }
    DeleteRunDialog(pendingDelete != null, onDismiss = { pendingDelete = null }) {
        pendingDelete?.let(model::delete)
        pendingDelete = null
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(CardInk).padding(12.dp)) {
        Text(label, color = Mist, fontSize = 12.sp)
        Text(value, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun SummaryScreen(runId: String, nav: NavHostController, model: HistoryViewModel = viewModel()) {
    val app = LocalContext.current.applicationContext as TreadmillApp
    val run by app.repository.observeRun(runId).collectAsState(initial = null)
    var confirmDelete by remember { mutableStateOf(false) }
    val detail = run
    if (detail == null) {
        Column(Modifier.padding(20.dp)) {
            BackButton(nav, "Back")
            Text("Saving…", color = Mist)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BackButton(nav, "Back") }
        item {
            Text(detail.workoutName, color = Paper, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Text("${detail.outcome.label} · ${formatWhen(detail.startedAt)}", color = Mist)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Time", formatDuration(detail.durationSec), Modifier.weight(1f))
                Stat("Distance", formatDistance(detail.distanceMeters), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Average", formatSpeed(detail.averageSpeedKmh), Modifier.weight(1f))
                Stat("Max", formatSpeed(detail.maxSpeedKmh), Modifier.weight(1f))
            }
        }
        detail.energyKcal?.let { kcal ->
            item { Stat("Energy", "$kcal kcal", Modifier.fillMaxWidth()) }
        }
        item {
            Text("Speed", color = Accent, fontWeight = FontWeight.SemiBold)
            SpeedChart(detail.samples)
        }
        item {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete run", color = Coral)
            }
        }
        item { Text("Intervals", color = Accent, fontWeight = FontWeight.SemiBold) }
        items(detail.intervals) { interval ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardInk).padding(12.dp)) {
                Text(interval.stage.label, color = stageColor(interval.stage), fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDuration(interval.actualDurationSec)} of ${formatDuration(interval.plannedDurationSec)} · ${formatSpeed(interval.plannedSpeedKmh)} · ${formatDistance(interval.distanceMeters)}",
                    color = Paper,
                    fontSize = 13.sp,
                )
            }
        }
    }
    DeleteRunDialog(confirmDelete, onDismiss = { confirmDelete = false }) {
        model.delete(runId)
        confirmDelete = false
        nav.popBackStack()
    }
}

@Composable
private fun DeleteRunDialog(visible: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this run?") },
        text = { Text("It is removed from History and from your totals.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
