package com.criticalbatteryoverlay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.criticalbatteryoverlay.MainActivity
import com.criticalbatteryoverlay.R
import com.criticalbatteryoverlay.data.BatteryPreferences
import com.criticalbatteryoverlay.util.PermissionUtils
import com.criticalbatteryoverlay.util.RedOverlayManager

class BatteryForegroundService : Service() {

    private lateinit var preferences: BatteryPreferences
    private var batteryReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        preferences = BatteryPreferences(this)
        createNotificationChannel()
        startServiceForeground()
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!preferences.isServiceEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Read sticky battery intent immediately on start
        checkStickyBatteryState()

        return START_STICKY
    }

    private fun registerBatteryReceiver() {
        if (isReceiverRegistered) return

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null || intent.action != Intent.ACTION_BATTERY_CHANGED) return
                evaluateBatteryState(intent)
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        isReceiverRegistered = true
    }

    private fun checkStickyBatteryState() {
        val stickyIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (stickyIntent != null) {
            evaluateBatteryState(stickyIntent)
        }
    }

    private fun evaluateBatteryState(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return

        val percentage = (level * 100) / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val threshold = preferences.batteryThreshold
        val opacity = preferences.overlayOpacity

        if (percentage <= threshold && !isCharging && preferences.isServiceEnabled) {
            if (PermissionUtils.canDrawOverlays(this)) {
                RedOverlayManager.showOverlay(this, opacity)
            }
        } else {
            RedOverlayManager.hideOverlay(this)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startServiceForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CriticalBattery Service Active")
            .setContentText("Monitoring battery status for critical low-battery overlay")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        if (isReceiverRegistered && batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        RedOverlayManager.hideOverlay(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "red_battery_channel"
        private const val NOTIFICATION_ID = 8801

        fun startService(context: Context) {
            val intent = Intent(context, BatteryForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BatteryForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
