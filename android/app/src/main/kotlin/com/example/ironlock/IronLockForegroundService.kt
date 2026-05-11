package com.example.ironlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat

class IronLockForegroundService : Service() {
    private val CHANNEL_ID = "IronLockServiceChannel"
    private lateinit var sessionManager: SessionManager
    private var screenUnlockReceiver: ScreenUnlockReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Ultra-fast initial check and frequent updates for maximum accuracy
    private val UPDATE_INTERVAL_MS = 250L // Update every 250ms for precision
    private val TAG = "IronLockForegroundService"
    
    private val checkRunnable = object : Runnable {
        override fun run() {
            try {
                sessionManager.updateRemainingTime()
                
                if (!sessionManager.isSessionActive()) {
                    Log.d(TAG, "✅ Session expired naturally. Stopping foreground service.")
                    unregisterScreenReceiver()
                    releaseWakeLock()
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                    stopSelf()
                } else {
                    // Continue checking with high frequency
                    handler.postDelayed(this, UPDATE_INTERVAL_MS)
                    
                    // Update notification every 5 seconds to show accurate time
                    val currentTime = System.currentTimeMillis()
                    if (currentTime % 5000 < UPDATE_INTERVAL_MS) {
                        updateNotification()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in checkRunnable: ${e.message}", e)
                // Continue anyway to prevent service crash
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        createNotificationChannel()
        acquireWakeLock()
        
        // Increment boot count if this is after a reboot
        if (sessionManager.isSessionActive()) {
            sessionManager.incrementBootCount()
            Log.d(TAG, "📊 Boot count incremented: ${sessionManager.getBootCount()}")
        }
        
        Log.d(TAG, "✅ Foreground Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🔒 Service started - FullLockMode=${sessionManager.isFullLockMode()}")
        
        val notification: Notification = buildNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        
        // Register screen unlock receiver for Full Lock mode
        if (sessionManager.isFullLockMode()) {
            registerScreenReceiver()
        }
        
        // Start the update loop immediately
        handler.post(checkRunnable)
        
        // Return START_STICKY to ensure service restarts if killed by system
        return START_STICKY
    }
    
    private fun buildNotification(): Notification {
        val remainingTime = sessionManager.getRemainingTime()
        val timeText = formatTime(remainingTime)
        val isFullLock = sessionManager.isFullLockMode()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 IronLock - جلسة نشطة")
            .setContentText(if (isFullLock) 
                "⏱️ الوقت المتبقي: $timeText | قفل كامل مفعل" 
                else "⏱️ الوقت المتبقي: $timeText | تطبيقات محددة مقفلة")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOnlyAlertOnce(true)
            .setTicker("IronLock: جلسة التركيز نشطة")
            .build()
    }
    
    private fun updateNotification() {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, buildNotification())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification: ${e.message}")
        }
    }
    
    private fun formatTime(millis: Long): String {
        if (millis <= 0) return "00:00:00"
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun registerScreenReceiver() {
        if (screenUnlockReceiver == null) {
            screenUnlockReceiver = ScreenUnlockReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenUnlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenUnlockReceiver, filter)
            }
            Log.d(TAG, "📡 ScreenUnlockReceiver registered")
        }
    }

    private fun unregisterScreenReceiver() {
        screenUnlockReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "📡 ScreenUnlockReceiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Receiver already unregistered or error: ${e.message}")
            }
            screenUnlockReceiver = null
        }
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "IronLock::SessionWakeLock"
            ).apply {
                acquire(10*60*1000L /*10 minutes*/) // Timeout to prevent battery drain
            }
            Log.d(TAG, "⚡ WakeLock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "⚡ WakeLock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WakeLock: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        unregisterScreenReceiver()
        releaseWakeLock()
        Log.d(TAG, "🛑 Service destroyed")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Service should continue running even if app is removed from recents
        Log.d(TAG, "📱 Task removed but service continues running")
        
        // Restart service to ensure it keeps running
        try {
            val restartServiceIntent = Intent(applicationContext, this.javaClass)
            restartServiceIntent.setPackage(packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartServiceIntent)
            } else {
                startService(restartServiceIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restart service: ${e.message}")
        }
        
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "خدمة IronLock",
                NotificationManager.IMPORTANCE_LOW // Low importance to minimize disruption
            ).apply {
                description = "خدمة تعمل في الخلفية للحفاظ على جلسة التركيز ومنع التلاعب"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
