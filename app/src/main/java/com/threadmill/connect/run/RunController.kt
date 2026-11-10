package com.threadmill.connect.run

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.threadmill.connect.ble.FtmsControl
import com.threadmill.connect.ble.LiveMetrics
import com.threadmill.connect.ble.RunLinkListener
import com.threadmill.connect.ble.SpeedRange
import com.threadmill.connect.ble.TreadmillLink
import com.threadmill.connect.data.AppRepository
import com.threadmill.connect.data.SettingsStore
import com.threadmill.connect.model.RunOutcome
import com.threadmill.connect.model.Workout
import com.threadmill.connect.model.toPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class RunController(
    private val app: Application,
    private val repository: AppRepository,
    private val settings: SettingsStore,
    private val link: TreadmillLink,
    private val cues: CuePlayer,
) : RunLinkListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val _ui = MutableStateFlow(RunUiState())
    val ui: StateFlow<RunUiState> = _ui.asStateFlow()

    private var workout: Workout? = null
    private var engine: RunEngine? = null
    private var recorder: RunRecorder? = null
    private var targetAddress: String? = null
    private var phase = RunPhase.Idle
    private var message: String? = null
    private var voice = true
    private var controlled = false
    private var saved = false
    private var savedRunId: String? = null
    private var serviceRunning = false
    private var startedAt = 0L
    private var runElapsedMs = 0L
    private var lastTick = 0L
    private var speedRange: SpeedRange? = null
    private var liveSpeed: Double? = null
    private var heartRate: Int? = null
    private val controlQueue: ArrayDeque<ByteArray> = ArrayDeque()
    private var inflight: Inflight? = null

    init {
        link.runListener = this
        scope.launch {
            settings.settings.collect { voice = it.voiceEnabled }
        }
    }

    fun start(workout: Workout) {
        if (_ui.value.active) return
        phase = RunPhase.Preparing
        this.workout = workout
        publish()
        scope.launch {
            val remembered = settings.settings.first()
            val address = remembered?.treadmillAddress
            if (address.isNullOrBlank()) {
                message = "Choose your treadmill in Settings first."
                phase = RunPhase.Idle
                publish()
                return@launch
            }
            if (!link.state.value.hasControlPoint && link.isReady && link.connectedAddress == address) {
                message = "This treadmill does not accept speed commands."
                phase = RunPhase.Idle
                publish()
                return@launch
            }
            reset(workout)
            phase = RunPhase.Preparing
            targetAddress = address
            phase = RunPhase.Preparing
            message = null
            publish()
            startService()
            if (link.isReady && link.connectedAddress == address) onReady() else link.connect(address, remembered.treadmillName)
        }
    }

    fun skip() {
        val engine = engine ?: return
        dispatch(engine.skip())
        afterEngineChange()
    }

    fun pause() {
        val engine = engine ?: return
        dispatch(engine.pause())
        syncPhase()
        stopTicking()
        publish()
    }

    fun resume() {
        val engine = engine ?: return
        dispatch(engine.resume())
        syncPhase()
        startTickingIfNeeded()
        publish()
    }

    fun stop() {
        val engine = engine ?: return
        if (phase == RunPhase.Idle || phase == RunPhase.Finished) return
        dispatch(engine.stop())
        finish(RunOutcome.Stopped)
    }

    fun consumeSavedRun() {
        savedRunId = null
        _ui.value = _ui.value.copy(savedRunId = null)
    }

    fun onServiceDestroyed() {
        serviceRunning = false
        if (saved || phase == RunPhase.Idle || phase == RunPhase.Finished) return
        if (phase == RunPhase.Preparing) {
            phase = RunPhase.Idle
            message = "The run stopped."
            publish()
            return
        }
        engine?.let { dispatch(it.stop()) }
        finish(RunOutcome.Stopped, stopTheService = false)
    }

    override fun onReady() {
        speedRange = link.state.value.metrics.speedRange ?: speedRange
        if (!link.state.value.hasControlPoint) {
            failControl("This treadmill does not accept speed commands.")
            return
        }
        if (phase == RunPhase.Preparing || phase == RunPhase.Reconnecting) {
            val waiting = inflight?.opcode == FtmsControl.REQUEST_CONTROL ||
                controlQueue.any { it.firstOpcode() == FtmsControl.REQUEST_CONTROL }
            if (!waiting) enqueue(FtmsControl.requestControl())
        }
    }

    override fun onLinkEnded(failure: String?) {
        controlled = false
        inflight = null
        controlQueue.clear()
        handler.removeCallbacks(controlTimeout)
        when (phase) {
            RunPhase.Preparing -> {
                phase = RunPhase.Idle
                message = failure ?: "Could not connect to the treadmill."
                stopService()
                publish()
            }
            RunPhase.Countdown, RunPhase.Running, RunPhase.Paused -> {
                engine?.let { dispatch(it.linkLost()) }
                phase = RunPhase.Reconnecting
                message = failure ?: "Connection lost"
                stopTicking()
                publish()
                scheduleReconnect()
            }
            RunPhase.Reconnecting -> scheduleReconnect()
            else -> Unit
        }
    }

    override fun onControlResponse(response: FtmsControl.Response) {
        val current = inflight ?: return
        if (response.opcode != current.opcode) return
        current.response = response
        maybeAdvanceControl()
    }

    override fun onControlWriteFinished(success: Boolean) {
        val current = inflight ?: return
        if (!success) {
            inflight = null
            handler.removeCallbacks(controlTimeout)
            if (current.opcode == FtmsControl.REQUEST_CONTROL) {
                failControl("The treadmill did not accept control.")
            } else {
                message = "The treadmill did not accept the command."
                publish()
                pumpControl()
            }
            return
        }
        current.writeDone = true
        maybeAdvanceControl()
    }

    override fun onMachineEvent(event: FtmsControl.MachineEvent) {
        val engine = engine ?: return
        if (!phase.active()) return
        when (event) {
            is FtmsControl.MachineEvent.TargetSpeedChanged -> dispatch(engine.remoteSpeed(event.kmh))
            is FtmsControl.MachineEvent.StoppedOrPaused -> if (event.paused) {
                dispatch(engine.treadmillPaused())
                syncPhase()
                stopTicking()
                publish()
            } else {
                dispatch(engine.stop())
                finish(RunOutcome.Stopped)
            }
            FtmsControl.MachineEvent.SafetyStop -> {
                message = "Stopped by the safety key."
                dispatch(engine.stop())
                finish(RunOutcome.Stopped)
            }
            FtmsControl.MachineEvent.StartedOrResumed -> if (phase == RunPhase.Paused) {
                dispatch(engine.resume())
                syncPhase()
                startTickingIfNeeded()
                publish()
            }
        }
    }

    override fun onMetrics(metrics: LiveMetrics) {
        liveSpeed = metrics.speedKmh ?: liveSpeed
        heartRate = metrics.heartRate ?: heartRate
        if (metrics.speedRange != null) speedRange = metrics.speedRange
        val recorder = recorder ?: return
        if (phase.active() || phase == RunPhase.Finished) {
            recorder.onMetrics(metrics, runElapsedMs, currentIndex())
        }
        if (phase.active()) publish()
    }

    private fun reset(workout: Workout) {
        this.workout = workout
        engine = RunEngine(workout.intervals.map { it.toPlan() })
        recorder = RunRecorder(workout.id, workout.name, workout.intervals.map { it.toPlan() })
        controlled = false
        saved = false
        savedRunId = null
        startedAt = 0L
        runElapsedMs = 0L
        liveSpeed = null
        heartRate = null
        controlQueue.clear()
        inflight = null
        handler.removeCallbacks(tick)
        handler.removeCallbacks(reconnect)
        handler.removeCallbacks(controlTimeout)
    }

    private fun beginCountdown() {
        val engine = engine ?: return
        startedAt = System.currentTimeMillis()
        dispatch(engine.start())
        phase = RunPhase.Countdown
        startTicking()
        publish()
    }

    private fun restoreLink() {
        val engine = engine ?: return
        dispatch(engine.linkRestored())
        syncPhase()
        startTickingIfNeeded()
        publish()
    }

    private fun dispatch(commands: List<EngineCommand>) {
        commands.forEach { command ->
            when (command) {
                EngineCommand.StartBelt -> enqueue(FtmsControl.startOrResume())
                is EngineCommand.SetSpeed -> enqueue(FtmsControl.setTargetSpeed(command.kmh, speedRange))
                EngineCommand.PauseBelt -> enqueue(FtmsControl.pause())
                EngineCommand.StopBelt -> enqueue(FtmsControl.stop())
                is EngineCommand.Announce -> if (voice) cues.speak(command.text)
                EngineCommand.Beep -> cues.beep()
            }
        }
    }

    private fun afterEngineChange() {
        val state = engine?.state
        if (state is EngineState.Finished) {
            finish(state.outcome)
            return
        }
        recorder?.mark(runElapsedMs, currentIndex())
        syncPhase()
        startTickingIfNeeded()
        publish()
    }

    private fun enqueue(payload: ByteArray) {
        val opcode = payload.firstOpcode()
        val requestQueued = inflight?.opcode == FtmsControl.REQUEST_CONTROL ||
            controlQueue.any { it.firstOpcode() == FtmsControl.REQUEST_CONTROL }
        if (!controlled && opcode != FtmsControl.REQUEST_CONTROL && !requestQueued) {
            controlQueue.addLast(FtmsControl.requestControl())
        }
        controlQueue.addLast(payload)
        pumpControl()
    }

    private fun pumpControl() {
        if (inflight != null || !link.isReady) return
        val payload = controlQueue.pollFirst() ?: return
        inflight = Inflight(payload.firstOpcode())
        link.writeControl(payload)
        handler.postDelayed(controlTimeout, CONTROL_TIMEOUT_MS)
    }

    private fun maybeAdvanceControl() {
        val current = inflight ?: return
        val response = current.response ?: return
        if (!current.writeDone) return
        inflight = null
        handler.removeCallbacks(controlTimeout)
        if (!response.success) {
            if (current.opcode == FtmsControl.REQUEST_CONTROL) {
                failControl(FtmsControl.resultMessage(response.result))
            } else {
                message = FtmsControl.resultMessage(response.result)
                publish()
                pumpControl()
            }
            return
        }
        if (current.opcode == FtmsControl.REQUEST_CONTROL) {
            controlled = true
            when (phase) {
                RunPhase.Preparing -> beginCountdown()
                RunPhase.Reconnecting -> restoreLink()
                else -> pumpControl()
            }
        } else {
            pumpControl()
        }
    }

    private fun failControl(text: String) {
        controlQueue.clear()
        inflight = null
        message = text
        when (phase) {
            RunPhase.Preparing -> {
                phase = RunPhase.Idle
                stopService()
                publish()
            }
            RunPhase.Countdown, RunPhase.Running, RunPhase.Paused, RunPhase.Reconnecting -> {
                phase = RunPhase.Reconnecting
                stopTicking()
                publish()
                scheduleReconnect()
            }
            else -> publish()
        }
    }

    private fun scheduleReconnect() {
        handler.removeCallbacks(reconnect)
        handler.postDelayed(reconnect, RECONNECT_MS)
    }

    private val reconnect = Runnable {
        val address = targetAddress ?: return@Runnable
        if (phase != RunPhase.Reconnecting) return@Runnable
        link.connect(address, link.state.value.identity.title)
    }

    private val controlTimeout = Runnable {
        val current = inflight ?: return@Runnable
        inflight = null
        if (current.opcode == FtmsControl.REQUEST_CONTROL) {
            failControl("The treadmill did not answer.")
        } else {
            message = "The treadmill did not answer."
            publish()
            pumpControl()
        }
    }

    private fun startTicking() {
        handler.removeCallbacks(tick)
        lastTick = SystemClock.elapsedRealtime()
        handler.postDelayed(tick, TICK_MS)
    }

    private fun startTickingIfNeeded() {
        if (phase == RunPhase.Countdown || phase == RunPhase.Running) startTicking() else stopTicking()
    }

    private fun stopTicking() {
        handler.removeCallbacks(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            val engine = engine ?: return
            if (phase != RunPhase.Countdown && phase != RunPhase.Running) return
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastTick).coerceAtLeast(0L)
            lastTick = now
            if (phase == RunPhase.Running) runElapsedMs += delta
            if (delta > 0L) dispatch(engine.tick(delta))
            val state = engine.state
            if (state is EngineState.Finished) {
                finish(state.outcome)
                return
            }
            recorder?.mark(runElapsedMs, currentIndex())
            syncPhase()
            publish()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun finish(outcome: RunOutcome, stopTheService: Boolean = true) {
        if (saved) return
        saved = true
        stopTicking()
        handler.removeCallbacks(reconnect)
        val draft = recorder?.snapshot(
            startedAt = startedAt,
            endedAt = System.currentTimeMillis(),
            elapsedMs = runElapsedMs,
            outcome = outcome,
        )
        phase = RunPhase.Finished
        publish()
        if (draft == null) {
            if (stopTheService) stopService()
            return
        }
        scope.launch {
            savedRunId = try {
                repository.saveRun(draft)
            } catch (_: Exception) {
                message = "Could not save the run."
                null
            }
            publish()
            if (stopTheService) stopService()
        }
    }

    private fun syncPhase() {
        phase = when (val state = engine?.state) {
            is EngineState.Countdown -> RunPhase.Countdown
            is EngineState.Active -> RunPhase.Running
            is EngineState.Paused -> if (state.link) RunPhase.Reconnecting else RunPhase.Paused
            is EngineState.Finished -> RunPhase.Finished
            else -> phase
        }
    }

    private fun currentIndex(): Int = when (val state = engine?.state) {
        is EngineState.Active -> state.index
        is EngineState.Paused -> state.index
        else -> 0
    }

    private fun publish() {
        val workout = workout
        val plans = workout?.intervals?.map { it.toPlan() }.orEmpty()
        val state = engine?.state
        val index = when (state) {
            is EngineState.Active -> state.index
            is EngineState.Paused -> state.index
            else -> 0
        }.coerceIn(0, (plans.size - 1).coerceAtLeast(0))
        val current = plans.getOrNull(index)
        val next = plans.getOrNull(index + 1)
        val intervalRemaining = when (state) {
            is EngineState.Active -> state.remainingMs
            is EngineState.Paused -> if (state.countingDown) current?.durationMs ?: 0L else state.remainingMs
            else -> current?.durationMs ?: 0L
        }
        val countdown = when (state) {
            is EngineState.Countdown -> state.remainingMs
            is EngineState.Paused -> if (state.countingDown) state.remainingMs else 0L
            else -> 0L
        }
        val tail = plans.drop(index + 1).sumOf { it.durationMs }
        val totalRemaining = when (state) {
            is EngineState.Active -> state.remainingMs + tail
            is EngineState.Paused -> if (state.countingDown) plans.sumOf { it.durationMs } else state.remainingMs + tail
            is EngineState.Countdown -> plans.sumOf { it.durationMs }
            is EngineState.Finished -> 0L
            else -> plans.sumOf { it.durationMs }
        }
        _ui.value = RunUiState(
            phase = phase,
            workoutId = workout?.id,
            workoutName = workout?.name.orEmpty(),
            message = message,
            index = index,
            count = plans.size,
            stage = current?.stage,
            intervalRemainingMs = intervalRemaining,
            totalRemainingMs = totalRemaining,
            elapsedMs = runElapsedMs,
            countdownMs = countdown,
            targetSpeedKmh = current?.speedKmh ?: 0.0,
            liveSpeedKmh = liveSpeed,
            heartRate = heartRate,
            nextStage = next?.stage,
            nextSpeedKmh = next?.speedKmh,
            distanceMeters = recorder?.distanceMeters() ?: 0.0,
            energyKcal = null,
            intervals = plans,
            savedRunId = savedRunId,
        )
        if (serviceRunning) {
            RunNotification.update(app, RunNotification.line(_ui.value))
        }
    }

    private fun startService() {
        if (serviceRunning) return
        serviceRunning = true
        androidx.core.content.ContextCompat.startForegroundService(app, Intent(app, RunForegroundService::class.java))
    }

    private fun stopService() {
        if (!serviceRunning) return
        serviceRunning = false
        app.stopService(Intent(app, RunForegroundService::class.java))
    }

    private fun RunPhase.active(): Boolean = this == RunPhase.Preparing ||
        this == RunPhase.Countdown ||
        this == RunPhase.Running ||
        this == RunPhase.Paused ||
        this == RunPhase.Reconnecting

    private class Inflight(
        val opcode: Int,
        var writeDone: Boolean = false,
        var response: FtmsControl.Response? = null,
    )

    private companion object {
        const val TICK_MS = 200L
        const val RECONNECT_MS = 2_000L
        const val CONTROL_TIMEOUT_MS = 4_000L
    }
}

private fun ByteArray.firstOpcode(): Int = if (isEmpty()) -1 else this[0].toInt() and 0xFF
