package com.zebra.batteryalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class BatteryAlertService : Service() {

    private val CHANNEL_ID_PERSISTENT = "hc50_battery_monitor_channel"
    private val CHANNEL_ID_ALARM = "hc50_critical_battery_alarm_channel"
    private val NOTIFICATION_ID = 1001
    private val ALERT_NOTIFICATION_ID = 1002

    private var hasTriggeredAlert = false
    private var mediaPlayer: MediaPlayer? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL
                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

                updatePersistentNotification(batteryPct, isCharging)

                if (batteryPct in 1..10 && !isCharging) {
                    if (!hasTriggeredAlert) {
                        triggerCriticalAlert(batteryPct)
                        hasTriggeredAlert = true
                    }
                } else if (batteryPct > 10 || isCharging) {
                    hasTriggeredAlert = false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildPersistentNotification(100, false))

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TEST_ALERT) {
            triggerCriticalAlert(10)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun triggerCriticalAlert(currentLevel: Int) {
        val vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
        }

        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setContentTitle("⚠️ Critical Battery Alert: $currentLevel%")
            .setContentText("Battery is at $currentLevel%. Please dock or swap battery now!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotification)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val persistentChannel = NotificationChannel(
            CHANNEL_ID_PERSISTENT,
            "Battery Monitoring Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains active battery level tracking on HC50"
            setShowBadge(false)
        }

        val alarmChannel = NotificationChannel(
            CHANNEL_ID_ALARM,
            "Critical Battery Warnings",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts user when battery level hits 10%"
            enableVibration(true)
        }

        manager.createNotificationChannel(persistentChannel)
        manager.createNotificationChannel(alarmChannel)
    }

    private fun buildPersistentNotification(level: Int, isCharging: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isCharging) "Charging ($level%)" else "Monitoring active ($level%)"

        return NotificationCompat.Builder(this, CHANNEL_ID_PERSISTENT)
            .setContentTitle("HC50 Battery Guard")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updatePersistentNotification(level: Int, isCharging: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildPersistentNotification(level, isCharging))
    }

    companion object {
        const val ACTION_TEST_ALERT = "com.zebra.batteryalert.ACTION_TEST_ALERT"
    }
}
