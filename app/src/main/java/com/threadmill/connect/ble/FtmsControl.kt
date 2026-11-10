package com.threadmill.connect.ble

import kotlin.math.roundToInt

/**
 * Fitness Machine control point, limited to the opcodes in ADR 0001.
 * Speed is a little-endian uint16 in units of 0.01 km/h.
 */
object FtmsControl {
    const val REQUEST_CONTROL = 0x00
    const val SET_TARGET_SPEED = 0x02
    const val START_OR_RESUME = 0x07
    const val STOP_OR_PAUSE = 0x08
    const val RESPONSE = 0x80
    const val SUCCESS = 0x01
    private const val PARAM_STOP = 0x01
    private const val PARAM_PAUSE = 0x02

    val allowedOpcodes: Set<Int> = setOf(
        REQUEST_CONTROL,
        SET_TARGET_SPEED,
        START_OR_RESUME,
        STOP_OR_PAUSE,
    )

    fun isAllowed(payload: ByteArray): Boolean {
        if (payload.isEmpty()) return false
        return (payload[0].toInt() and 0xFF) in allowedOpcodes
    }

    fun requestControl(): ByteArray = byteArrayOf(REQUEST_CONTROL.toByte())

    fun setTargetSpeed(kmh: Double, range: SpeedRange? = null): ByteArray {
        val clamped = if (range != null) kmh.coerceIn(range.minKmh, range.maxKmh) else kmh
        val units = (clamped * 100.0).roundToInt().coerceIn(0, 0xFFFF)
        return byteArrayOf(
            SET_TARGET_SPEED.toByte(),
            (units and 0xFF).toByte(),
            ((units shr 8) and 0xFF).toByte(),
        )
    }

    fun startOrResume(): ByteArray = byteArrayOf(START_OR_RESUME.toByte())

    fun pause(): ByteArray = byteArrayOf(STOP_OR_PAUSE.toByte(), PARAM_PAUSE.toByte())

    fun stop(): ByteArray = byteArrayOf(STOP_OR_PAUSE.toByte(), PARAM_STOP.toByte())

    data class Response(val opcode: Int, val result: Int) {
        val success: Boolean
            get() = result == SUCCESS
    }

    fun parseResponse(value: ByteArray): Response? {
        if (value.size < 3) return null
        if ((value[0].toInt() and 0xFF) != RESPONSE) return null
        return Response(value[1].toInt() and 0xFF, value[2].toInt() and 0xFF)
    }

    fun resultMessage(result: Int): String = when (result) {
        SUCCESS -> "Success"
        0x02 -> "The treadmill does not support that command."
        0x03 -> "The treadmill rejected that speed."
        0x04 -> "The treadmill could not do that."
        0x05 -> "The treadmill did not grant control. Stop any workout on its console and try again."
        else -> "The treadmill refused the command ($result)."
    }

    sealed interface MachineEvent {
        data class StoppedOrPaused(val paused: Boolean) : MachineEvent
        data object SafetyStop : MachineEvent
        data object StartedOrResumed : MachineEvent
        data class TargetSpeedChanged(val kmh: Double) : MachineEvent
    }

    fun parseMachineStatus(value: ByteArray): MachineEvent? {
        if (value.isEmpty()) return null
        return when (value[0].toInt() and 0xFF) {
            0x02 -> MachineEvent.StoppedOrPaused(paused = value.getOrNull(1)?.let { it.toInt() and 0xFF } == PARAM_PAUSE)
            0x03 -> MachineEvent.SafetyStop
            0x04 -> MachineEvent.StartedOrResumed
            0x05 -> {
                if (value.size < 3) return null
                val units = (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
                MachineEvent.TargetSpeedChanged(units / 100.0)
            }
            else -> null
        }
    }
}
