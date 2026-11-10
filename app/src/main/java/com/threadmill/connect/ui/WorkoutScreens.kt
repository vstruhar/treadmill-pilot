package com.threadmill.connect.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.threadmill.connect.TreadmillApp
import com.threadmill.connect.data.AppRepository
import com.threadmill.connect.model.Workout
import com.threadmill.connect.model.toPlan
import com.threadmill.connect.run.RunPhase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: AppRepository = (app as TreadmillApp).repository
    val workouts = repository.observeWorkouts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteWorkout(id) }
    }
}

@Composable
fun WorkoutsScreen(nav: NavHostController, model: WorkoutsViewModel = viewModel()) {
    val workouts by model.workouts.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Workouts", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { nav.navigate("settings") }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Paper)
            }
        }
        Text("Plans the treadmill can run for you.", color = Mist, modifier = Modifier.padding(bottom = 16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(workouts, key = { it.id }) { workout ->
                WorkoutCard(workout) { nav.navigate("workout/${workout.id}") }
            }
        }
        FloatingActionButton(
            onClick = { nav.navigate("editor/new") },
            containerColor = Accent,
            contentColor = Ink,
            modifier = Modifier.align(Alignment.End).padding(top = 12.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New workout")
        }
    }
}

@Composable
private fun WorkoutCard(workout: Workout, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardInk)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(workout.name, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text(
            "${formatDuration(workout.durationSec)} · ${workout.intervals.size} intervals",
            color = Mist,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    nav: NavHostController,
    model: WorkoutsViewModel = viewModel(),
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TreadmillApp
    val workout by app.repository.observeWorkout(workoutId).collectAsState(initial = null)
    val run by app.runController.ui.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var armRun by remember { mutableStateOf(false) }
    LaunchedEffect(run.phase, run.workoutId) {
        if (run.phase == RunPhase.Preparing && run.workoutId == workoutId) armRun = true
        if (armRun && run.workoutId == workoutId && (run.phase == RunPhase.Countdown || run.phase == RunPhase.Running)) {
            armRun = false
            nav.navigate("run") { launchSingleTop = true }
        }
    }
    val item = workout
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        BackButton(nav, "Workouts")
        if (item == null) {
            Text("That workout is gone.", color = Mist)
            return@Column
        }
        Text(item.name, color = Paper, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Text(formatDuration(item.durationSec), color = Mist, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        ProfileChart(item.intervals.map { it.toPlan() }, progress = null)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(item.intervals, key = { it.id }) { interval ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardInk).padding(14.dp)) {
                    Text(interval.stage.label, color = stageColor(interval.stage), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(formatDuration(interval.durationSec), color = Paper, modifier = Modifier.padding(end = 12.dp))
                    Text(formatSpeed(interval.speedKmh), color = Paper)
                }
            }
        }
        run.message?.takeIf { run.phase == RunPhase.Idle }?.let {
            Text(it, color = Coral, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            onClick = { app.runController.start(item) },
            enabled = !run.active,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
        ) {
            Text(
                if (run.phase == RunPhase.Preparing) "Connecting…" else "Start Workout",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "The treadmill's stop key and safety clip still work if the phone loses the link.",
            color = Mist,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nav.navigate("editor/${item.id}") }, modifier = Modifier.weight(1f)) {
                Text("Edit", color = Paper)
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                Text("Delete", color = Coral)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this workout?") },
            text = { Text("Runs you already finished stay in History.") },
            confirmButton = {
                Button(onClick = {
                    model.delete(workoutId)
                    confirmDelete = false
                    nav.popBackStack()
                }) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
