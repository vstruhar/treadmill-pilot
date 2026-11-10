package com.threadmill.connect.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

/**
 * BLE client for one treadmill. Reads and notifications cover the advertised
 * name, device information, and live treadmill data. The only writes are the
 * FTMS control-point opcodes allowed by [FtmsControl]: request control, set
 * target speed, start or resume, and stop or pause. FitShow command pipes
 * are never written.
 */
@SuppressLint("MissingPermission")
class BleClient(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onDevice(device: SeenDevice)
        fun onScanState(scanning: Boolean)
        fun onScanMessage(message: String)
        fun onBluetoothOff()
        fun onConnecting(device: SeenDevice)
        fun onIdentity(identity: DeviceIdentity)
        fun onServices(services: List<ServiceInfo>)
        fun onMetrics(metrics: LiveMetrics)
        fun onPacket(packet: PacketLine)
        fun onListening()
        fun onReady()
        fun onControlResponse(response: FtmsControl.Response)
        fun onControlWriteFinished(success: Boolean)
        fun onMachineEvent(event: FtmsControl.MachineEvent)
        fun onLinkEnded(failure: String?)
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val queue: ArrayDeque<Op> = ArrayDeque()
    private var gatt: BluetoothGatt? = null
    private var link = Link.Idle
    private var busy = false
    private var activeGeneration = 0
    private var discoveryStarted = false
    private var opTimeout: Runnable? = null
    private var identity = DeviceIdentity()
    private var controlPoint: BluetoothGattCharacteristic? = null
    private var currentOp: Op? = null
    private var announcedReady = false

    fun startScan() {
        handler.post { startScanInternal() }
    }

    fun stopScan() {
        handler.post { stopScanInternal(announce = false) }
    }

    fun connect(device: SeenDevice) {
        handler.post { connectInternal(device) }
    }

    fun disconnect() {
        handler.post { endLink(null, notify = true) }
    }

    fun writeControl(payload: ByteArray) {
        handler.post { writeControlInternal(payload) }
    }

    fun close() {
        handler.post {
            stopScanInternal(announce = false)
            endLink(null)
        }
    }

    private fun startScanInternal() {
        if (link != Link.Idle) return
        val adapter = adapter()
        if (adapter == null || !adapter.isEnabled) {
            listener.onBluetoothOff()
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            listener.onScanMessage("This phone cannot scan for Bluetooth LE.")
            return
        }
        stopScanInternal(announce = false)
        try {
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback,
            )
            listener.onScanState(true)
            handler.postDelayed(scanStop, SCAN_WINDOW_MS)
        } catch (_: SecurityException) {
            listener.onScanMessage("Bluetooth permission was denied.")
        }
    }

    private fun stopScanInternal(announce: Boolean) {
        handler.removeCallbacks(scanStop)
        try {
            adapter()?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            // The scan is already stopped, or permission was revoked.
        }
        listener.onScanState(false)
        if (announce) listener.onScanMessage("Scan finished. Tap Scan to look again.")
    }

    @SuppressLint("MissingPermission")
    private fun connectInternal(device: SeenDevice) {
        val adapter = adapter()
        if (adapter == null || !adapter.isEnabled) {
            listener.onBluetoothOff()
            return
        }
        stopScanInternal(announce = false)
        endLink(null, notify = false)
        identity = DeviceIdentity(advertisedName = device.name)
        discoveryStarted = false
        announcedReady = false
        controlPoint = null
        link = Link.Connecting
        listener.onConnecting(device)
        try {
            gatt = adapter.getRemoteDevice(device.address)
                .connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        } catch (_: SecurityException) {
            link = Link.Idle
            gatt = null
            listener.onLinkEnded("Bluetooth permission was denied.")
        } catch (_: IllegalArgumentException) {
            link = Link.Idle
            gatt = null
            listener.onLinkEnded("That Bluetooth address is not valid.")
        }
    }

    private fun endLink(failure: String?, notify: Boolean = true) {
        val notifyListener = notify && link != Link.Idle
        link = Link.Idle
        busy = false
        currentOp = null
        activeGeneration += 1
        queue.clear()
        controlPoint = null
        clearTimers()
        if (notifyListener) listener.onLinkEnded(failure)
        shutdownGatt()
    }

    private fun writeControlInternal(payload: ByteArray) {
        if (!FtmsControl.isAllowed(payload)) return
        val characteristic = controlPoint ?: return
        if (link != Link.Ready) return
        queue.add(Op.Write(characteristic, payload.copyOf()))
        pump()
    }

    private fun shutdownGatt() {
        val current = gatt ?: return
        gatt = null
        try {
            current.disconnect()
        } catch (_: SecurityException) {
            // Already closing.
        }
        try {
            current.close()
        } catch (_: SecurityException) {
            // Already closing.
        }
    }

    private fun beginDiscovery() {
        if (discoveryStarted || link == Link.Idle) return
        discoveryStarted = true
        handler.removeCallbacks(discoverFallback)
        val started = try {
            gatt?.discoverServices() == true
        } catch (_: SecurityException) {
            false
        }
        if (!started) endLink("Could not read the treadmill services.")
    }

    private fun enqueueReadsAndSubscriptions(gatt: BluetoothGatt) {
        val services = gatt.services.orEmpty()
        listener.onServices(services.map { service ->
            ServiceInfo(
                name = GattUuids.label(service.uuid),
                uuid = service.uuid.toString(),
                characteristics = service.characteristics.map { characteristic ->
                    CharacteristicInfo(
                        name = GattUuids.label(characteristic.uuid),
                        uuid = characteristic.uuid.toString(),
                        properties = propertyText(characteristic.properties),
                    )
                },
            )
        })
        val reads = services.flatMap { it.characteristics }
            .filter { characteristic ->
                characteristic.uuid in GattUuids.readable &&
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
            }
            .sortedBy { characteristic ->
                val index = GattUuids.readable.indexOf(characteristic.uuid)
                if (index < 0) Int.MAX_VALUE else index
            }
        reads.forEach { queue.add(Op.Read(it)) }
        controlPoint = services.asSequence()
            .flatMap { it.characteristics.asSequence() }
            .firstOrNull { characteristic ->
                characteristic.uuid == GattUuids.controlPoint &&
                    characteristic.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
        var subscribed = false
        services.filter { it.uuid in GattUuids.listenServices }.forEach { service ->
            service.characteristics.forEach { characteristic ->
                val notify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                val indicate = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                if (!notify && !indicate) return@forEach
                val value = if (notify) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                }
                queue.add(Op.Subscribe(characteristic, value))
                subscribed = true
            }
        }
        if (subscribed) listener.onListening()
        if (queue.isEmpty()) announceReady() else pump()
    }

    private fun pump() {
        if (busy || link != Link.Ready) return
        val op = queue.pollFirst() ?: return
        val current = gatt ?: return
        busy = true
        currentOp = op
        val generation = ++activeGeneration
        armOpTimeout(generation, write = op is Op.Write)
        val started = try {
            when (op) {
                is Op.Read -> current.readCharacteristic(op.characteristic)
                is Op.Subscribe -> subscribe(current, op)
                is Op.Write -> write(current, op)
            }
        } catch (_: SecurityException) {
            false
        }
        val noResponse = op is Op.Write &&
            op.characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0
        if (!started) {
            if (op is Op.Write) listener.onControlWriteFinished(false)
            complete(generation)
        } else if (noResponse) {
            listener.onControlWriteFinished(true)
            complete(generation)
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribe(gatt: BluetoothGatt, op: Op.Subscribe): Boolean {
        if (!gatt.setCharacteristicNotification(op.characteristic, true)) return false
        val descriptor = op.characteristic.getDescriptor(GattUuids.clientConfig) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, op.value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = op.value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun write(gatt: BluetoothGatt, op: Op.Write): Boolean {
        val writeWithResponse = op.characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val type = if (writeWithResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(op.characteristic, op.value, type) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            op.characteristic.writeType = type
            @Suppress("DEPRECATION")
            op.characteristic.value = op.value
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(op.characteristic)
        }
    }

    private fun armOpTimeout(generation: Int, write: Boolean) {
        clearOpTimeout()
        val task = Runnable {
            if (generation == activeGeneration && busy) {
                if (write) {
                    listener.onControlWriteFinished(false)
                    complete(generation)
                } else {
                    endLink("The treadmill stopped answering. Try connecting again.")
                }
            }
        }
        opTimeout = task
        handler.postDelayed(task, OP_TIMEOUT_MS)
    }

    private fun complete(generation: Int) {
        if (generation != activeGeneration || !busy) return
        busy = false
        currentOp = null
        clearOpTimeout()
        pump()
        if (!busy && queue.isEmpty()) announceReady()
    }

    private fun announceReady() {
        if (announcedReady || link != Link.Ready) return
        announcedReady = true
        listener.onReady()
    }

    private fun clearOpTimeout() {
        opTimeout?.let(handler::removeCallbacks)
        opTimeout = null
    }

    private fun clearTimers() {
        handler.removeCallbacks(scanStop)
        handler.removeCallbacks(connectTimeout)
        handler.removeCallbacks(discoverFallback)
        clearOpTimeout()
    }

    private fun adapter(): BluetoothAdapter? {
        val manager = appContext.getSystemService(BluetoothManager::class.java)
        return manager?.adapter
    }

    private val scanStop = Runnable { stopScanInternal(announce = true) }
    private val connectTimeout = Runnable {
        if (link == Link.Connecting) endLink("Timed out waiting for the treadmill. Move closer and try again.")
    }
    private val discoverFallback = Runnable { beginDiscovery() }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post { listener.onDevice(result.toSeenDevice()) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            handler.post { results.forEach { listener.onDevice(it.toSeenDevice()) } }
        }

        override fun onScanFailed(errorCode: Int) {
            handler.post {
                stopScanInternal(announce = false)
                listener.onScanMessage(scanFailure(errorCode))
            }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (this@BleClient.gatt != gatt && this@BleClient.gatt != null) return@post
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            endLink(failureText(status))
                            return@post
                        }
                        handler.removeCallbacks(connectTimeout)
                        link = Link.Discovering
                        val requested = try {
                            gatt.requestMtu(185)
                        } catch (_: SecurityException) {
                            false
                        }
                        if (!requested) beginDiscovery() else handler.postDelayed(discoverFallback, 3_000)
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (link == Link.Idle) {
                            shutdownGatt()
                        } else {
                            val message = if (status == BluetoothGatt.GATT_SUCCESS && link == Link.Ready) {
                                "The treadmill disconnected."
                            } else if (status == BluetoothGatt.GATT_SUCCESS) {
                                "Could not connect to the treadmill."
                            } else {
                                failureText(status)
                            }
                            endLink(message)
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            handler.post { beginDiscovery() }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handler.post {
                if (link == Link.Idle) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    endLink("Could not read the treadmill services.")
                    return@post
                }
                link = Link.Ready
                enqueueReadsAndSubscriptions(gatt)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: byteArrayOf()
            deliverRead(characteristic, value, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            deliverRead(characteristic, value, status)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val generation = activeGeneration
            handler.post {
                if (link == Link.Ready && generation == activeGeneration) complete(generation)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val generation = activeGeneration
            val wasControl = characteristic.uuid == GattUuids.controlPoint
            handler.post {
                if (link != Link.Ready || generation != activeGeneration) return@post
                if (wasControl) listener.onControlWriteFinished(status == BluetoothGatt.GATT_SUCCESS)
                complete(generation)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handler.post {
                if (link == Link.Ready) handleNotification(characteristic, value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handler.post {
                if (link == Link.Ready) handleNotification(characteristic, value)
            }
        }
    }

    private fun deliverRead(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        val generation = activeGeneration
        handler.post {
            if (link != Link.Ready || generation != activeGeneration) return@post
            if (status == BluetoothGatt.GATT_SUCCESS) applyRead(characteristic, value)
            complete(generation)
        }
    }

    private fun applyRead(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        when (characteristic.uuid) {
            GattUuids.deviceName -> identity = identity.copy(gattName = value.decodeText().ifBlank { null })
            GattUuids.manufacturerName -> identity = identity.copy(manufacturer = value.decodeText().ifBlank { null })
            GattUuids.modelNumber -> identity = identity.copy(model = value.decodeText().ifBlank { null })
            GattUuids.serialNumber -> identity = identity.copy(serial = value.decodeText().ifBlank { null })
            GattUuids.hardwareRevision -> identity = identity.copy(hardware = value.decodeText().ifBlank { null })
            GattUuids.firmwareRevision -> identity = identity.copy(firmware = value.decodeText().ifBlank { null })
            GattUuids.fitnessFeature -> identity = identity.copy(features = FtmsParser.parseFeatures(value))
            GattUuids.supportedSpeed -> FtmsParser.parseSpeedRange(value)?.let {
                listener.onMetrics(LiveMetrics(speedRange = it))
            }
            GattUuids.supportedIncline -> FtmsParser.parseInclineRange(value)?.let {
                listener.onMetrics(LiveMetrics(inclineRange = it))
            }
        }
        listener.onIdentity(identity)
    }

    private fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        when (characteristic.uuid) {
            GattUuids.controlPoint -> FtmsControl.parseResponse(value)?.let(listener::onControlResponse)
            GattUuids.machineStatus -> FtmsControl.parseMachineStatus(value)?.let(listener::onMachineEvent)
        }
        val detail = when (characteristic.uuid) {
            GattUuids.treadmillData -> FtmsParser.parseTreadmill(value)?.also(listener::onMetrics)?.summary()
            GattUuids.heartRateMeasurement -> FtmsParser.parseHeartRate(value)?.let { bpm ->
                listener.onMetrics(LiveMetrics(heartRate = bpm))
                "$bpm bpm"
            }
            else -> null
        }
        listener.onPacket(
            PacketLine(
                label = GattUuids.label(characteristic.uuid),
                hex = value.toHex(),
                detail = detail,
            ),
        )
    }

    private fun LiveMetrics.summary(): String? {
        val parts = mutableListOf<String>()
        speedKmh?.let { parts += "%.2f km/h".format(java.util.Locale.US, it) }
        inclinePercent?.let { parts += "%.1f%%".format(java.util.Locale.US, it) }
        distanceMeters?.let { parts += "$it m" }
        heartRate?.let { parts += "$it bpm" }
        return parts.joinToString(" · ").ifBlank { null }
    }

    private fun failureText(status: Int): String = when (status) {
        8 -> "The connection timed out. Move closer to the treadmill and try again."
        133 -> "The phone dropped the link. Move closer and try again."
        else -> "Bluetooth error $status."
    }

    private enum class Link { Idle, Connecting, Discovering, Ready }

    private sealed interface Op {
        data class Read(val characteristic: BluetoothGattCharacteristic) : Op
        data class Subscribe(val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : Op
        data class Write(val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : Op
    }

    private companion object {
        const val SCAN_WINDOW_MS = 45_000L
        const val CONNECT_TIMEOUT_MS = 12_000L
        const val OP_TIMEOUT_MS = 5_000L

        fun scanFailure(code: Int): String = when (code) {
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                "The phone refused the scan. Toggle Bluetooth and try again."
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Bluetooth is unavailable."
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "This phone does not support the scan."
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Bluetooth is busy. Try again."
            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scanning too often. Wait a moment and try again."
            else -> "Scan failed ($code)."
        }

        fun propertyText(properties: Int): String {
            val parts = mutableListOf<String>()
            if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) parts += "read"
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) parts += "write"
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) parts += "write-no-response"
            if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) parts += "notify"
            if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) parts += "indicate"
            return parts.joinToString(" · ").ifEmpty { "—" }
        }

        fun ScanResult.toSeenDevice(): SeenDevice {
            val record = scanRecord
            val recordName = record?.deviceName?.takeIf { it.isNotBlank() }
            val deviceName = try {
                device.name?.takeIf { it.isNotBlank() }
            } catch (_: SecurityException) {
                null
            }
            val company = record?.manufacturerSpecificData
            return SeenDevice(
                address = device.address,
                name = recordName ?: deviceName,
                rssi = rssi,
                serviceUuids = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty(),
                companyId = if (company != null && company.size() > 0) company.keyAt(0) else null,
            )
        }
    }
}
