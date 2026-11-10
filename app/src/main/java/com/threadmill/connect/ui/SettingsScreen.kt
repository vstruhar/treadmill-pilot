package com.threadmill.connect.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.threadmill.connect.TreadmillApp
import com.threadmill.connect.backup.BackupCodec
import com.threadmill.connect.ble.SeenDevice
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val treadmill = app as TreadmillApp
    val settings = treadmill.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.threadmill.connect.data.AppSettings())
    val link = treadmill.link.state
    var notice by mutableStateOf<String?>(null)
        private set

    fun setVoice(enabled: Boolean) {
        viewModelScope.launch { treadmill.settings.setVoiceEnabled(enabled) }
    }

    fun setAccent(name: String) {
        viewModelScope.launch { treadmill.settings.setAccent(name) }
    }

    fun remember(device: SeenDevice) {
        viewModelScope.launch {
            treadmill.settings.rememberTreadmill(device.address, device.label)
            notice = "Saved ${device.label}."
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = BackupCodec.encode(treadmill.repository.exportBackup())
                treadmill.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(text.toByteArray())
                } ?: error("Could not open the file.")
                notice = "Backup saved."
            } catch (error: Exception) {
                notice = error.message ?: "Could not save the backup."
            }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = treadmill.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: error("Could not open the file.")
                val counts = treadmill.repository.restore(BackupCodec.decode(text))
                notice = if (counts.workouts == 0 && counts.runs == 0) {
                    "Nothing new in that backup."
                } else {
                    "Added ${counts.workouts} workouts and ${counts.runs} runs."
                }
            } catch (error: Exception) {
                notice = error.message ?: "Could not restore that backup."
            }
        }
    }

    fun scan() = treadmill.link.startScan()
    fun stopScan() = treadmill.link.stopScan()
}

@Composable
fun SettingsScreen(nav: NavHostController, model: SettingsViewModel = viewModel()) {
    val settings by model.settings.collectAsState()
    val run by (LocalContext.current.applicationContext as TreadmillApp).runController.ui.collectAsState()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.export(uri)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.restore(uri)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackButton(nav, "Workouts")
        Text("Settings", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardInk).padding(16.dp)) {
            Text("Treadmill", color = Mist, fontSize = 13.sp)
            Text(settings.treadmillName ?: "None saved", color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            settings.treadmillAddress?.let {
                Text(it, color = Mist, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            if (run.active) {
                Text("Finish the run before changing the treadmill.", color = Mist, modifier = Modifier.padding(top = 8.dp))
            } else {
                Button(
                    onClick = { nav.navigate("pair") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                ) { Text(if (settings.treadmillAddress == null) "Choose treadmill" else "Change treadmill") }
            }
        }
        AccentPicker(settings.accent, model::setAccent)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardInk).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Spoken cues", color = Paper, fontWeight = FontWeight.SemiBold)
                Text("Beeps still play.", color = Mist, fontSize = 13.sp)
            }
            Switch(checked = settings.voiceEnabled, onCheckedChange = model::setVoice)
        }
        OutlinedButton(onClick = { export.launch("treadmill-backup.json") }, modifier = Modifier.fillMaxWidth()) {
            Text("Backup", color = Paper)
        }
        OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Restore", color = Paper)
        }
        model.notice?.let { Text(it, color = Accent) }
    }
}

@Composable
fun TreadmillPickerScreen(nav: NavHostController, model: SettingsViewModel = viewModel()) {
    val link by model.link.collectAsState()
    val settings by model.settings.collectAsState()
    DisposableEffect(Unit) {
        model.scan()
        onDispose { model.stopScan() }
    }
    val likely = link.devices.filter { it.likely }
    val others = link.devices.filterNot { it.likely }.take(40)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            BackButton(nav, "Settings")
            Text("Choose treadmill", color = Paper, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (link.scanning) "Looking nearby." else "Scan finished.",
                color = Mist,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            link.message?.let { Text(it, color = Coral, modifier = Modifier.padding(bottom = 8.dp)) }
        }
        if (likely.isNotEmpty()) {
            item { Text("Likely treadmills", color = Accent, fontWeight = FontWeight.SemiBold) }
            items(likely, key = { it.address }) { device ->
                DeviceRow(device, device.address == settings.treadmillAddress) {
                    model.remember(device)
                    nav.popBackStack()
                }
            }
        }
        if (others.isNotEmpty()) {
            item { Text(if (likely.isEmpty()) "Nearby devices" else "Other devices", color = Accent, fontWeight = FontWeight.SemiBold) }
            items(others, key = { it.address }) { device ->
                DeviceRow(device, device.address == settings.treadmillAddress) {
                    model.remember(device)
                    nav.popBackStack()
                }
            }
        }
        if (!link.scanning) {
            item {
                Button(
                    onClick = model::scan,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                ) { Text("Scan") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selectedName: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardInk).padding(16.dp)) {
        Text("Accent color", color = Paper, fontWeight = FontWeight.SemiBold)
        Text("Buttons, tabs, and charts use this color.", color = Mist, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentChoice.entries.forEach { choice ->
                val selected = choice.name == selectedName
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(choice.name) }
                        .semantics {
                            contentDescription = choice.label
                            role = Role.Button
                        }
                        .border(3.dp, if (selected) Paper else CardInk, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(choice.color),
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(device: SeenDevice, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardInk).clickable(onClick = onClick).padding(16.dp),
    ) {
        Text(device.label, color = if (selected) Accent else Paper, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text("${device.address} · ${device.rssi} dBm", color = Mist, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
