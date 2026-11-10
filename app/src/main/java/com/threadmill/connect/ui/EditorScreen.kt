package com.threadmill.connect.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.threadmill.connect.TreadmillApp
import com.threadmill.connect.ble.SpeedRange
import com.threadmill.connect.model.Stage
import com.threadmill.connect.model.Workout
import com.threadmill.connect.model.WorkoutInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

class EditorViewModel(app: Application, private val workoutId: String?) : AndroidViewModel(app) {
    private val treadmill = app as TreadmillApp
    private val repository = treadmill.repository
    private val link = treadmill.link
    val range = link.state.map { it.metrics.speedRange }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val state = MutableStateFlow<Draft?>(null)
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            state.value = if (workoutId == null) {
                fresh()
            } else {
                repository.observeWorkout(workoutId).first()?.toDraft() ?: fresh()
            }
        }
    }

    fun rename(name: String) {
        state.value = state.value?.copy(name = name)
    }

    fun add() {
        val current = state.value ?: return
        val speed = current.intervals.lastOrNull()?.speedKmh ?: 5.0
        state.value = current.copy(
            intervals = current.intervals + DraftInterval(UUID.randomUUID().toString(), Stage.Training, 60, speed),
        )
    }

    fun update(interval: DraftInterval) {
        val current = state.value ?: return
        state.value = current.copy(intervals = current.intervals.map { if (it.id == interval.id) interval else it })
    }

    fun move(id: String, delta: Int) {
        val current = state.value ?: return
        val next = current.intervals.reordered(id, delta)
        if (next !== current.intervals) state.value = current.copy(intervals = next)
    }

    fun duplicate(id: String) {
        val current = state.value ?: return
        val index = current.intervals.indexOfFirst { it.id == id }
        if (index < 0) return
        val copy = current.intervals[index].copy(id = UUID.randomUUID().toString())
        val next = current.intervals.toMutableList()
        next.add(index + 1, copy)
        state.value = current.copy(intervals = next)
    }

    fun delete(id: String) {
        val current = state.value ?: return
        if (current.intervals.size <= 1) return
        state.value = current.copy(intervals = current.intervals.filterNot { it.id == id })
    }

    fun save(onDone: () -> Unit) {
        val current = state.value ?: return
        if (current.name.isBlank()) {
            error = "Name the workout."
            return
        }
        viewModelScope.launch {
            repository.saveWorkout(
                Workout(
                    id = current.id,
                    name = current.name.trim(),
                    createdAt = current.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    intervals = current.intervals.map {
                        WorkoutInterval(it.id, it.stage, it.durationSec, it.speedKmh)
                    },
                ),
            )
            onDone()
        }
    }

    private fun fresh(): Draft = Draft(
        id = UUID.randomUUID().toString(),
        name = "",
        createdAt = System.currentTimeMillis(),
        intervals = listOf(DraftInterval(UUID.randomUUID().toString(), Stage.WarmUp, 60, 3.0)),
    )

    companion object {
        fun factory(app: Application, workoutId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return EditorViewModel(app, workoutId) as T
                }
            }
    }
}

data class Draft(
    val id: String,
    val name: String,
    val createdAt: Long,
    val intervals: List<DraftInterval>,
)

data class DraftInterval(
    val id: String,
    val stage: Stage,
    val durationSec: Int,
    val speedKmh: Double,
)

private fun Workout.toDraft() = Draft(
    id = id,
    name = name,
    createdAt = createdAt,
    intervals = intervals.map { DraftInterval(it.id, it.stage, it.durationSec, it.speedKmh) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(workoutId: String?, nav: NavHostController) {
    val app = LocalContext.current.applicationContext as TreadmillApp
    val model: EditorViewModel = viewModel(factory = EditorViewModel.factory(app, workoutId))
    val draft by model.state.collectAsState()
    val range by model.range.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) }
    val current = draft
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        BackButton(nav, "Back")
        if (current == null) {
            Text("Loading…", color = Mist)
            return
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDuration(current.intervals.sumOf { it.durationSec }),
                color = Paper,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { model.save { nav.popBackStack() } }) { Text("Save", color = Accent) }
        }
        OutlinedTextField(
            value = current.name,
            onValueChange = model::rename,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        model.error?.let { Text(it, color = Coral, modifier = Modifier.padding(bottom = 8.dp)) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(IntervalListSpacing)) {
            items(current.intervals, key = { it.id }) { interval ->
                IntervalRow(interval, onClick = { editing = interval.id }, onMove = { delta -> model.move(interval.id, delta) })
            }
        }
        Button(
            onClick = model::add,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add Interval")
        }
    }
    val selected = current?.intervals?.firstOrNull { it.id == editing }
    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = CardInk) {
            IntervalSheet(
                interval = selected,
                range = range,
                canDelete = current.intervals.size > 1,
                onChange = model::update,
                onDuplicate = { model.duplicate(selected.id) },
                onDelete = {
                    model.delete(selected.id)
                    editing = null
                },
                onMove = { delta -> model.move(selected.id, delta) },
            )
        }
    }
}

@Composable
private fun IntervalRow(interval: DraftInterval, onClick: () -> Unit, onMove: (Int) -> Unit) {
    val offset = remember(interval.id) { mutableFloatStateOf(0f) }
    val rowHeight = remember(interval.id) { mutableFloatStateOf(0f) }
    val onMoveLatest = rememberUpdatedState(onMove)
    val gapPx = with(LocalDensity.current) { IntervalListSpacing.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .zIndex(if (offset.floatValue != 0f) 1f else 0f)
            .graphicsLayer { translationY = offset.floatValue }
            .onSizeChanged { rowHeight.floatValue = it.height.toFloat() }
            .clip(RoundedCornerShape(12.dp))
            .background(CardInk)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Reorder",
            tint = Mist,
            modifier = Modifier
                .pointerInput(interval.id) {
                    detectReorderDrag(
                        onDrag = { delta -> offset.floatValue += delta },
                        onDragEnd = {
                            val pitch = rowHeight.floatValue + gapPx
                            val steps = if (pitch > 0f) (offset.floatValue / pitch).roundToInt() else 0
                            offset.floatValue = 0f
                            if (steps != 0) onMoveLatest.value(steps)
                        },
                        onDragCancel = { offset.floatValue = 0f },
                    )
                }
                .padding(8.dp),
        )
        Text(interval.stage.label, color = stageColor(interval.stage), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(formatDuration(interval.durationSec), color = Paper, modifier = Modifier.padding(end = 12.dp))
        Text(formatSpeed(interval.speedKmh), color = Paper, modifier = Modifier.padding(end = 8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalSheet(
    interval: DraftInterval,
    range: SpeedRange?,
    canDelete: Boolean,
    onChange: (DraftInterval) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(interval.stage.label, color = Paper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stage.entries.forEach { stage ->
                FilterChip(
                    selected = interval.stage == stage,
                    onClick = { onChange(interval.copy(stage = stage)) },
                    label = { Text(stage.label) },
                )
            }
        }
        Stepper("Time", formatDuration(interval.durationSec)) {
            onChange(interval.copy(durationSec = (interval.durationSec + it * 5).coerceIn(5, 3 * 60 * 60)))
        }
        Stepper("Speed", formatSpeed(interval.speedKmh)) { delta ->
            onChange(interval.copy(speedKmh = stepSpeed(interval.speedKmh, delta, range)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMove(-1) }, modifier = Modifier.weight(1f)) { Text("Earlier", color = Paper) }
            OutlinedButton(onClick = { onMove(1) }, modifier = Modifier.weight(1f)) { Text("Later", color = Paper) }
        }
        OutlinedButton(onClick = onDuplicate, modifier = Modifier.fillMaxWidth()) { Text("Duplicate", color = Paper) }
        if (canDelete) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete", color = Coral) }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Mist, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onStep(-1) }) { Text("−", color = Paper) }
        Text(value, color = Paper, modifier = Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { onStep(1) }) { Text("+", color = Paper) }
    }
}

private val IntervalListSpacing = 8.dp

internal fun List<DraftInterval>.reordered(id: String, delta: Int): List<DraftInterval> {
    if (delta == 0) return this
    val from = indexOfFirst { it.id == id }
    if (from < 0) return this
    val to = (from + delta).coerceIn(0, lastIndex)
    if (from == to) return this
    val next = toMutableList()
    next.add(to, next.removeAt(from))
    return next
}

// The handle claims the pointer on the initial pass. The list scroller only sees the
// gesture afterwards, so a drag reorders instead of scrolling.
private suspend fun PointerInputScope.detectReorderDrag(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        var pastSlop = false
        var pending = 0f
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.changedToUpIgnoreConsumed() || (!pastSlop && change.isConsumed)) {
                    if (pastSlop) {
                        if (change != null) {
                            val delta = change.positionChangeIgnoreConsumed().y
                            if (delta != 0f) onDrag(delta)
                            change.consume()
                        }
                        onDragEnd()
                    }
                    break
                }
                val delta = change.positionChangeIgnoreConsumed().y
                if (!pastSlop) {
                    pending += delta
                    if (abs(pending) > slop) {
                        pastSlop = true
                        change.consume()
                        onDrag(pending)
                        pending = 0f
                    }
                } else {
                    change.consume()
                    if (delta != 0f) onDrag(delta)
                }
            }
        } catch (cancelled: CancellationException) {
            if (pastSlop) onDragCancel()
            throw cancelled
        }
    }
}

private fun stepSpeed(current: Double, direction: Int, range: SpeedRange?): Double {
    val min = range?.minKmh ?: 0.5
    val max = range?.maxKmh ?: 20.0
    val stepped = ((current + direction * 0.1) * 10).roundToInt() / 10.0
    return stepped.coerceIn(min, max)
}
