package com.threadmill.connect.run

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.threadmill.connect.MainActivity
import com.threadmill.connect.R
import java.util.Locale

object RunNotification {
    const val ID = 42
    private const val CHANNEL = "runs"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Runs", NotificationManager.IMPORTANCE_LOW)
        channel.setSound(null, null)
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, text: String): Notification {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
    }

    fun update(context: Context, text: String) {
        try {
            NotificationManagerCompat.from(context).notify(ID, build(context, text))
        } catch (_: SecurityException) {
            // The run still continues when notifications are blocked.
        }
    }

    fun line(state: RunUiState): String {
        val stage = when (state.phase) {
            RunPhase.Countdown -> "Get ready"
            RunPhase.Paused -> "Paused"
            RunPhase.Reconnecting -> "Reconnecting"
            RunPhase.Preparing -> "Connecting"
            else -> state.stage?.label ?: "Run"
        }
        val speed = "%.1f km/h".format(Locale.US, state.liveSpeedKmh ?: state.targetSpeedKmh)
        val left = clock(state.totalRemainingMs)
        return "$stage · $speed · $left left"
    }

    private fun clock(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(total / 60, total % 60)
    }
}
