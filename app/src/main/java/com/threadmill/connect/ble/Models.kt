package com.threadmill.connect.ble

data class SeenDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<String>,
    val companyId: Int?,
) {
    val likely: Boolean
        get() = likelyTreadmill(name, serviceUuids)

    val label: String
        get() = name ?: "Unnamed device"
}

data class SpeedRange(
    val minKmh: Double,
    val maxKmh: Double,
    val stepKmh: Double,
)

data class InclineRange(
    val minPercent: Double,
    val maxPercent: Double,
    val stepPercent: Double,
)

data class LiveMetrics(
    val speedKmh: Double? = null,
    val averageSpeedKmh: Double? = null,
    val distanceMeters: Int? = null,
    val inclinePercent: Double? = null,
    val heartRate: Int? = null,
    val elapsedSeconds: Int? = null,
    val calories: Int? = null,
    val speedRange: SpeedRange? = null,
    val inclineRange: InclineRange? = null,
) {
    fun merge(next: LiveMetrics): LiveMetrics = copy(
        speedKmh = next.speedKmh ?: speedKmh,
        averageSpeedKmh = next.averageSpeedKmh ?: averageSpeedKmh,
        distanceMeters = next.distanceMeters ?: distanceMeters,
        inclinePercent = next.inclinePercent ?: inclinePercent,
        heartRate = next.heartRate ?: heartRate,
        elapsedSeconds = next.elapsedSeconds ?: elapsedSeconds,
        calories = next.calories ?: calories,
        speedRange = next.speedRange ?: speedRange,
        inclineRange = next.inclineRange ?: inclineRange,
    )

    val hasReading: Boolean
        get() = speedKmh != null || averageSpeedKmh != null || distanceMeters != null ||
            inclinePercent != null || heartRate != null || elapsedSeconds != null || calories != null
}

data class DeviceIdentity(
    val advertisedName: String? = null,
    val gattName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val serial: String? = null,
    val hardware: String? = null,
    val firmware: String? = null,
    val features: String? = null,
) {
    val title: String
        get() = gattName ?: advertisedName ?: "Unnamed device"
}

data class CharacteristicInfo(
    val name: String,
    val uuid: String,
    val properties: String,
)

data class ServiceInfo(
    val name: String,
    val uuid: String,
    val characteristics: List<CharacteristicInfo>,
)

data class PacketLine(
    val label: String,
    val hex: String,
    val detail: String?,
)

data class TreadmillSession(
    val address: String,
    val identity: DeviceIdentity = DeviceIdentity(),
    val services: List<ServiceInfo> = emptyList(),
    val metrics: LiveMetrics = LiveMetrics(),
    val packets: List<PacketLine> = emptyList(),
    val listening: Boolean = false,
    val status: String = "Connecting",
)

fun likelyTreadmill(name: String?, serviceUuids: Collection<String>): Boolean {
    val normalized = name?.lowercase().orEmpty()
    val nameHit = NAME_HINTS.any { normalized.contains(it) } ||
        Regex("""\bring\b""").containsMatchIn(normalized)
    if (nameHit) return true
    return serviceUuids.any { raw ->
        val uuid = runCatching { java.util.UUID.fromString(raw) }.getOrNull()
        uuid != null && uuid in KNOWN_SERVICE_UUIDS
    }
}

private val NAME_HINTS = listOf(
    "fs-",
    "ftms",
    "fitshow",
    "tread",
    "rx135",
    "rx-135",
    "walkpad",
    "walking pad",
    "walkingpad",
)

private val KNOWN_SERVICE_UUIDS = setOf(
    GattUuids.fitnessMachine,
    GattUuids.serialFff0,
    GattUuids.serialFfe0,
    GattUuids.serialAe00,
    GattUuids.tiOta,
)

fun ByteArray.toHex(limit: Int = 48): String {
    val shown = if (size <= limit) this else copyOf(limit)
    val body = shown.joinToString(" ") { "%02X".format(it) }
    return if (size <= limit) body else "$body …"
}

fun ByteArray.decodeText(): String =
    toString(Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
