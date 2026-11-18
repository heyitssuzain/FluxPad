package dev.fluxpad.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.fluxpad.app.FluxpadApplication
import dev.fluxpad.app.MainActivity
import dev.fluxpad.app.R
import dev.fluxpad.core.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the process (and therefore the Bluetooth HID registration) alive while a
 * real link is active. It is started only after the OS reports Connected.
 */
class SessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var started = false

    private val container get() = (application as FluxpadApplication).container

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                container.coordinator.disconnect()
                stopSelf()
            }
            return START_NOT_STICKY
        }
        val name = intent?.getStringExtra(EXTRA_NAME) ?: "Computer"
        startAsForeground(name)
        observeConnection()
        return START_NOT_STICKY
    }

    private fun startAsForeground(name: String) {
        val notification = buildNotification(name)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            started = true
        } catch (e: Exception) {
            // Android 12+ can reject a background FGS start; Android 14 can reject
            // the type if the required permission is missing. The link itself is
            // still valid while the app is alive, so we fail safe and stop.
            stopSelf()
        }
    }

    private fun observeConnection() {
        observer?.cancel()
        observer = scope.launch {
            container.coordinator.connectionState.collectLatest { state ->
                if (state is ConnectionState.Connected) {
                    val notification = buildNotification(state.deviceName)
                    val promoted = runCatching {
                        ServiceCompat.startForeground(
                            this@SessionService,
                            NOTIFICATION_ID,
                            notification,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                            } else {
                                0
                            },
                        )
                    }.isSuccess
                    if (promoted) {
                        started = true
                    } else {
                        stopSelf()
                        return@collectLatest
                    }
                } else if (state is ConnectionState.Disconnected || state is ConnectionState.Error) {
                    if (started) {
                        ServiceCompat.stopForeground(this@SessionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        started = false
                    }
                    stopSelf()
                }
            }
        }
    }

    private fun buildNotification(name: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fluxpad)
            .setContentTitle("FLUXPAD link active")
            .setContentText("Sending real HID input to $name")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .addAction(0, "Disconnect", stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active input link",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while FLUXPAD is controlling a computer"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        observer?.cancel()
        // If the service is destroyed while a link is live (task swipe, system
        // reclaim), release held keys/buttons before the process can keep them
        // stuck on the computer. A failed start leaves started=false and does
        // not tear down a session the visible activity still owns.
        if (started) {
            started = false
            val container = container
            container.applicationScope.launch {
                runCatching { container.coordinator.disconnect() }
                runCatching { container.bluetoothTransport.shutdown() }
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "fluxpad_session"
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_NAME = "device_name"
        private const val ACTION_STOP = "dev.fluxpad.app.STOP_SESSION"

        fun start(context: Context, deviceName: String) {
            runCatching {
                val intent = Intent(context, SessionService::class.java)
                    .putExtra(EXTRA_NAME, deviceName)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, SessionService::class.java)) }
        }
    }
}
