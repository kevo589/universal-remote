package dev.kmedrano.remote.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.kmedrano.remote.app.R
import dev.kmedrano.remote.app.RemoteApplication
import dev.kmedrano.remote.core.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the app's device connections alive while backgrounded.
 *
 * Android suspends background sockets fairly aggressively once an app is no longer in the
 * foreground; without this service, "auto-connect and stay connected to all four devices"
 * would silently stop working a short while after the user leaves the app. The persistent
 * notification this shows is the (required) user-visible cost of that guarantee.
 */
class RemoteConnectionService : Service() {

    private val binder = LocalBinder()
    private var scope: CoroutineScope? = null

    inner class LocalBinder : Binder() {
        val service: RemoteConnectionService get() = this@RemoteConnectionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(connected = 0, total = 0))

        val container = (application as RemoteApplication).container
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = serviceScope
        serviceScope.launch {
            container.devices.collectLatest { devices ->
                val connected = devices.count { device ->
                    container.clientFor(device.id)?.connectionState?.value == ConnectionState.Connected
                }
                updateNotification(connected, devices.size)
            }
        }
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(connected: Int, total: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("$connected/$total connected")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun updateNotification(connected: Int, total: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(connected, total))
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "device_connections"

        fun start(context: Context) {
            val intent = Intent(context, RemoteConnectionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
