package com.threadmill.connect.ble

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

data class LinkSnapshot(
    val bluetoothEnabled: Boolean,
    val scanning: Boolean = false,
    val devices: List<SeenDevice> = emptyList(),
    val address: String? = null,
    val ready: Boolean = false,
    val hasControlPoint: Boolean = false,
    val identity: DeviceIdentity = DeviceIdentity(),
    val metrics: LiveMetrics = LiveMetrics(),
    val message: String? = null,
)

interface RunLinkListener {
    fun onReady()
    fun onLinkEnded(failure: String?)
    fun onControlResponse(response: FtmsControl.Response)
    fun onControlWriteFinished(success: Boolean)
    fun onMachineEvent(event: FtmsControl.MachineEvent)
    fun onMetrics(metrics: LiveMetrics)
}

class TreadmillLink(context: Context) : BleClient.Listener {
    private val appContext = context.applicationContext
    private val devices = linkedMapOf<String, SeenDevice>()
    private val ble = BleClient(appContext, this)
    private val _state = MutableStateFlow(LinkSnapshot(bluetoothEnabled = BlePermissions.bluetoothEnabled(appContext)))
    val state: StateFlow<LinkSnapshot> = _state.asStateFlow()
    var runListener: RunLinkListener? = null

    val isReady: Boolean
        get() = _state.value.ready

    val connectedAddress: String?
        get() = if (_state.value.ready) _state.value.address else null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val enabled = BlePermissions.bluetoothEnabled(appContext)
            _state.update { it.copy(bluetoothEnabled = enabled, scanning = if (enabled) it.scanning else false) }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            bluetoothReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun startScan() {
        if (!_state.value.bluetoothEnabled) return
        ble.startScan()
    }

    fun stopScan() = ble.stopScan()

    fun connect(address: String, name: String?) {
        ble.connect(
            SeenDevice(
                address = address,
                name = name,
                rssi = 0,
                serviceUuids = emptyList(),
                companyId = null,
            ),
        )
    }

    fun writeControl(payload: ByteArray) = ble.writeControl(payload)

    override fun onDevice(device: SeenDevice) {
        val previous = devices[device.address]
        val merged = device.copy(
            name = device.name ?: previous?.name,
            serviceUuids = device.serviceUuids.ifEmpty { previous?.serviceUuids.orEmpty() },
            companyId = device.companyId ?: previous?.companyId,
        )
        if (
            previous != null &&
            previous.name == merged.name &&
            previous.serviceUuids == merged.serviceUuids &&
            previous.companyId == merged.companyId &&
            abs(previous.rssi - merged.rssi) < 8
        ) {
            return
        }
        devices[device.address] = merged
        publishDevices()
    }

    override fun onScanState(scanning: Boolean) {
        if (scanning) devices.clear()
        _state.update { it.copy(scanning = scanning, devices = if (scanning) emptyList() else it.devices) }
        if (scanning) publishDevices()
    }

    override fun onScanMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    override fun onBluetoothOff() {
        _state.update { it.copy(bluetoothEnabled = false, scanning = false) }
    }

    override fun onConnecting(device: SeenDevice) {
        _state.update {
            it.copy(
                address = device.address,
                ready = false,
                hasControlPoint = false,
                identity = DeviceIdentity(advertisedName = device.name),
                message = null,
            )
        }
    }

    override fun onIdentity(identity: DeviceIdentity) {
        _state.update { it.copy(identity = identity) }
    }

    override fun onServices(services: List<ServiceInfo>) {
        val control = GattUuids.controlPoint.toString()
        val hasControl = services.any { service ->
            service.characteristics.any { characteristic ->
                characteristic.uuid.equals(control, ignoreCase = true) && characteristic.properties.contains("write")
            }
        }
        _state.update { it.copy(hasControlPoint = hasControl) }
    }

    override fun onMetrics(metrics: LiveMetrics) {
        _state.update { it.copy(metrics = it.metrics.merge(metrics)) }
        runListener?.onMetrics(metrics)
    }

    override fun onPacket(packet: PacketLine) = Unit

    override fun onListening() = Unit

    override fun onReady() {
        _state.update { it.copy(ready = true, message = null) }
        runListener?.onReady()
    }

    override fun onControlResponse(response: FtmsControl.Response) {
        runListener?.onControlResponse(response)
    }

    override fun onControlWriteFinished(success: Boolean) {
        runListener?.onControlWriteFinished(success)
    }

    override fun onMachineEvent(event: FtmsControl.MachineEvent) {
        runListener?.onMachineEvent(event)
    }

    override fun onLinkEnded(failure: String?) {
        _state.update { it.copy(ready = false, hasControlPoint = false, message = failure) }
        runListener?.onLinkEnded(failure)
    }

    private fun publishDevices() {
        val sorted = devices.values.sortedWith(
            compareByDescending<SeenDevice> { it.likely }.thenByDescending { it.rssi },
        )
        _state.update { it.copy(devices = sorted) }
    }

}
