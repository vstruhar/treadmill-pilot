package com.threadmill.connect.ble

object FtmsParser {
    fun parseTreadmill(value: ByteArray): LiveMetrics? {
        if (value.size < 2) return null
        val flags = u16(value, 0)
        var offset = 2
        var speed: Double? = null
        var averageSpeed: Double? = null
        var distance: Int? = null
        var incline: Double? = null
        var heartRate: Int? = null
        var elapsed: Int? = null
        var calories: Int? = null

        fun take(size: Int): Boolean {
            if (offset + size > value.size) return false
            offset += size
            return true
        }

        fun current(start: Int): ByteArray = value.copyOfRange(start, offset)

        if (flags and 0x0001 == 0) {
            val start = offset
            if (!take(2)) return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
            speed = u16(current(start), 0) / 100.0
        }
        if (flags and 0x0002 != 0) {
            val start = offset
            if (!take(2)) return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
            averageSpeed = u16(current(start), 0) / 100.0
        }
        if (flags and 0x0004 != 0) {
            val start = offset
            if (!take(3)) return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
            distance = u24(current(start), 0)
        }
        if (flags and 0x0008 != 0) {
            val start = offset
            if (!take(4)) return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
            incline = s16(current(start), 0) / 10.0
        }
        if (flags and 0x0010 != 0 && !take(4)) {
            return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
        }
        if (flags and 0x0020 != 0 && !take(2)) {
            return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
        }
        if (flags and 0x0040 != 0 && !take(2)) {
            return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
        }
        if (flags and 0x0080 != 0) {
            val start = offset
            if (!take(5)) return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
            calories = u16(current(start), 0)
        }
        if (flags and 0x0100 != 0) {
            val start = offset
            if (!take(1)) return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
            heartRate = current(start)[0].toInt() and 0xff
        }
        if (flags and 0x0200 != 0 && !take(1)) {
            return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
        }
        if (flags and 0x0400 != 0) {
            val start = offset
            if (!take(2)) return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
            elapsed = u16(current(start), 0)
        }
        return packed(speed, averageSpeed, distance, incline, heartRate, elapsed, calories)
    }

    fun parseHeartRate(value: ByteArray): Int? {
        if (value.isEmpty()) return null
        val flags = value[0].toInt() and 0xff
        val wide = flags and 0x01 != 0
        return when {
            !wide && value.size >= 2 -> value[1].toInt() and 0xff
            wide && value.size >= 3 -> u16(value, 1)
            else -> null
        }
    }

    fun parseSpeedRange(value: ByteArray): SpeedRange? {
        if (value.size < 6) return null
        return SpeedRange(
            minKmh = u16(value, 0) / 100.0,
            maxKmh = u16(value, 2) / 100.0,
            stepKmh = u16(value, 4) / 100.0,
        )
    }

    fun parseInclineRange(value: ByteArray): InclineRange? {
        if (value.size < 6) return null
        return InclineRange(
            minPercent = s16(value, 0) / 10.0,
            maxPercent = s16(value, 2) / 10.0,
            stepPercent = s16(value, 4) / 10.0,
        )
    }

    fun parseFeatures(value: ByteArray): String? {
        if (value.size < 4) return null
        val bits = u32(value, 0)
        val names = listOf(
            0 to "average speed",
            2 to "distance",
            3 to "incline",
            4 to "elevation",
            5 to "pace",
            9 to "energy",
            10 to "heart rate",
            12 to "elapsed time",
            13 to "remaining time",
            14 to "power",
        )
        val present = names.filter { (bit, _) -> bits and (1 shl bit) != 0 }.map { it.second }
        return if (present.isEmpty()) "0x%08X".format(bits) else present.joinToString(", ")
    }

    private fun packed(
        speed: Double?,
        averageSpeed: Double?,
        distance: Int?,
        incline: Double?,
        heartRate: Int?,
        elapsed: Int?,
        calories: Int?,
    ): LiveMetrics = LiveMetrics(
        speedKmh = speed,
        averageSpeedKmh = averageSpeed,
        distanceMeters = distance,
        inclinePercent = incline,
        heartRate = heartRate,
        elapsedSeconds = elapsed,
        calories = calories,
    )
}

internal fun u16(value: ByteArray, offset: Int): Int =
    (value[offset].toInt() and 0xff) or ((value[offset + 1].toInt() and 0xff) shl 8)

internal fun s16(value: ByteArray, offset: Int): Int =
    u16(value, offset).let { if (it and 0x8000 != 0) it - 0x10000 else it }

internal fun u24(value: ByteArray, offset: Int): Int =
    (value[offset].toInt() and 0xff) or
        ((value[offset + 1].toInt() and 0xff) shl 8) or
        ((value[offset + 2].toInt() and 0xff) shl 16)

internal fun u32(value: ByteArray, offset: Int): Int =
    (value[offset].toInt() and 0xff) or
        ((value[offset + 1].toInt() and 0xff) shl 8) or
        ((value[offset + 2].toInt() and 0xff) shl 16) or
        ((value[offset + 3].toInt() and 0xff) shl 24)
