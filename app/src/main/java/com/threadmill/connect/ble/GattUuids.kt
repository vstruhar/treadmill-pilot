package com.threadmill.connect.ble

import java.util.UUID

object GattUuids {
    fun uuid16(short: Int): UUID =
        UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(short))

    val genericAccess: UUID = uuid16(0x1800)
    val deviceName: UUID = uuid16(0x2A00)
    val deviceInfo: UUID = uuid16(0x180A)
    val modelNumber: UUID = uuid16(0x2A24)
    val serialNumber: UUID = uuid16(0x2A25)
    val hardwareRevision: UUID = uuid16(0x2A27)
    val firmwareRevision: UUID = uuid16(0x2A28)
    val manufacturerName: UUID = uuid16(0x2A29)
    val heartRate: UUID = uuid16(0x180D)
    val heartRateMeasurement: UUID = uuid16(0x2A37)
    val fitnessMachine: UUID = uuid16(0x1826)
    val fitnessFeature: UUID = uuid16(0x2ACC)
    val treadmillData: UUID = uuid16(0x2ACD)
    val supportedSpeed: UUID = uuid16(0x2AD4)
    val supportedIncline: UUID = uuid16(0x2AD5)
    val controlPoint: UUID = uuid16(0x2AD9)
    val machineStatus: UUID = uuid16(0x2ADA)
    val clientConfig: UUID = uuid16(0x2902)
    val serialFff0: UUID = uuid16(0xFFF0)
    val serialFfe0: UUID = uuid16(0xFFE0)
    val serialAe00: UUID = uuid16(0xAE00)
    val tiOta: UUID = UUID.fromString("f000ffc0-0451-4000-b000-000000000000")

    val listenServices: Set<UUID> = setOf(
        heartRate,
        fitnessMachine,
        serialFff0,
        serialFfe0,
        serialAe00,
        tiOta,
    )

    val readable: List<UUID> = listOf(
        deviceName,
        manufacturerName,
        modelNumber,
        serialNumber,
        hardwareRevision,
        firmwareRevision,
        fitnessFeature,
        supportedSpeed,
        supportedIncline,
    )

    private val names: Map<UUID, String> = mapOf(
        genericAccess to "Generic Access",
        deviceName to "Device name",
        deviceInfo to "Device Information",
        modelNumber to "Model number",
        serialNumber to "Serial number",
        hardwareRevision to "Hardware revision",
        firmwareRevision to "Firmware revision",
        manufacturerName to "Manufacturer",
        heartRate to "Heart Rate",
        heartRateMeasurement to "Heart rate measurement",
        fitnessMachine to "Fitness Machine",
        fitnessFeature to "Fitness Machine feature",
        treadmillData to "Treadmill data",
        supportedSpeed to "Supported speed range",
        supportedIncline to "Supported incline range",
        uuid16(0x2AD6) to "Supported resistance range",
        controlPoint to "Control point",
        machineStatus to "Machine status",
        clientConfig to "Client configuration",
        serialFff0 to "FitShow serial FFF0",
        uuid16(0xFFF1) to "FitShow FFF1",
        uuid16(0xFFF2) to "FitShow FFF2",
        serialFfe0 to "FitShow serial FFE0",
        uuid16(0xFFE1) to "FitShow FFE1",
        uuid16(0xFFE4) to "FitShow FFE4",
        serialAe00 to "FitShow serial AE00",
        uuid16(0xAE01) to "FitShow AE01",
        uuid16(0xAE02) to "FitShow AE02",
        tiOta to "JieLi OTA",
        UUID.fromString("f000ffc1-0451-4000-b000-000000000000") to "JieLi OTA data",
        UUID.fromString("f000ffc2-0451-4000-b000-000000000000") to "JieLi OTA control",
    )

    fun label(uuid: UUID): String = names[uuid] ?: shortLabel(uuid)

    fun shortLabel(uuid: UUID): String {
        val text = uuid.toString()
        val base = "-0000-1000-8000-00805f9b34fb"
        if (text.startsWith("0000") && text.endsWith(base)) {
            return text.substring(4, 8).uppercase()
        }
        return text
    }

    fun advertisementTags(uuids: Collection<String>): List<String> {
        val tags = linkedSetOf<String>()
        uuids.forEach { raw ->
            val uuid = runCatching { UUID.fromString(raw) }.getOrNull() ?: return@forEach
            when (uuid) {
                fitnessMachine -> tags += "FTMS"
                heartRate -> tags += "Heart rate"
                serialFff0, serialFfe0, serialAe00 -> tags += "FitShow"
                tiOta -> tags += "JieLi"
                deviceInfo -> tags += "Device info"
            }
        }
        return tags.toList()
    }
}
