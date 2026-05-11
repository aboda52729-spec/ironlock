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
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat

class IronLockForegroundService : Service() {
    private val CHANNEL_ID = "IronLockServiceChannel"
    private lateinit var sessionManager: SessionManager
    private var screenUnlockReceiver: ScreenUnlockReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // Faster initial check and more frequent updates for better accuracy
    private val UPDATE_INTERVAL_MS = 500L // Update every 500ms for precision
    
    private val checkRunnable = object : Runnable {
        override fun run() {
            try {
                sessionManager.updateRemainingTime()
                
                if (!sessionManager.isSessionActive()) {
                    Log.d("IronLockService", "Session expired. Stopping foreground service.")
                    unregisterScreenReceiver()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                    stopSelf()
                } else {
                    // Continue checking
                    handler.postDelayed(this, UPDATE_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e("IronLockService", "Error in checkRunnable", e)
                // Continue anyway to prevent service crash
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        createNotificationChannel()
        Log.d("IronLockService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("IronLockService", "Service started with isFullLockMode=${sessionManager.isFullLockMode()}")
        
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
        
        // Return START_STICKY to ensure service restarts if killed
        return START_STICKY
    }
    
    private fun buildNotification(): Notification {
        val remainingTime = sessionManager.getRemainingTime()
        val timeText = formatTime(remainingTime)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IronLock - جلسة نشطة")
            .setContentText("الوقت المتبقي: $timeText | لا يمكن إيقاف القفل")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOnlyAlertOnce(true)
            .build()
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
            Log.d("IronLockService", "ScreenUnlockReceiver registered")
        }
    }

    private fun unregisterScreenReceiver() {
        screenUnlockReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d("IronLockService", "ScreenUnlockReceiver unregistered")
            } catch (e: Exception) {
                Log.w("IronLockService", "Receiver already unregistered or error: ${e.message}")
            }
            screenUnlockReceiver = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        unregisterScreenReceiver()
        Log.d("IronLockService", "Service destroyed")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Service should continue running even if app is removed from recents
        Log.d("IronLockService", "Task removed but service continues")
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
                description = "خدمة تعمل في الخلفية للحفاظ على جلسة التركيز"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
