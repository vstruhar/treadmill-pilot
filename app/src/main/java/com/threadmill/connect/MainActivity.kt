package com.threadmill.connect

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threadmill.connect.ble.BlePermissions
import com.threadmill.connect.ui.AppNav
import com.threadmill.connect.ui.CardInk
import com.threadmill.connect.ui.Ink
import com.threadmill.connect.ui.Accent
import com.threadmill.connect.ui.AccentChoice
import com.threadmill.connect.ui.applyAccent
import com.threadmill.connect.ui.Mist
import com.threadmill.connect.ui.Paper
import com.threadmill.connect.ui.TreadmillTheme

class MainActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh += 1 }

    private val enableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh += 1 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TreadmillApp
        setContent {
            val tick = refresh
            val granted = BlePermissions.granted(this) || tick == Int.MIN_VALUE
            val bluetooth = BlePermissions.bluetoothEnabled(this)
            val run by app.runController.ui.collectAsStateWithLifecycle()
            val settings by app.settings.settings.collectAsStateWithLifecycle(
                initialValue = com.threadmill.connect.data.AppSettings(),
            )
            LaunchedEffect(settings.accent) {
                val choice = AccentChoice.entries.find { it.name == settings.accent } ?: AccentChoice.Lime
                applyAccent(choice)
            }
            TreadmillTheme {
                when {
                    !granted && !run.active -> PermissionGate(
                        title = "Allow Bluetooth",
                        body = "The app finds your treadmill over Bluetooth and keeps the run going while the screen is locked.",
                        action = "Continue",
                        onAction = { permissions.launch(BlePermissions.required()) },
                        secondary = "App settings",
                        onSecondary = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null),
                                ),
                            )
                        },
                    )
                    !bluetooth && !run.active -> PermissionGate(
                        title = "Turn Bluetooth on",
                        body = "The treadmill has to be awake and Bluetooth has to be on.",
                        action = "Turn on Bluetooth",
                        onAction = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    )
                    else -> AppNav(app)
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = Paper, fontWeight = FontWeight.SemiBold, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(body, color = Mist)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
        ) { Text(action) }
        if (secondary != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                Text(secondary, color = Paper)
            }
        }
    }
}
