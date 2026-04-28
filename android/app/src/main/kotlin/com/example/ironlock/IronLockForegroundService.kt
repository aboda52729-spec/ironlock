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
import androidx.core.app.NotificationCompat

class IronLockForegroundService : Service() {
    private val CHANNEL_ID = "IronLockServiceChannel"
    private lateinit var sessionManager: SessionManager
    private var screenUnlockReceiver: ScreenUnlockReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            sessionManager.updateRemainingTime()
            if (!sessionManager.isSessionActive()) {
                Log.d("IronLockService", "Session expired. Stopping foreground service.")
                unregisterScreenReceiver()
                stopForeground(true)
                stopSelf()
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IronLock Active")
            .setContentText("Focus session is running. Do not disturb.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        
        // Register screen unlock receiver for Full Lock mode
        if (sessionManager.isFullLockMode()) {
            registerScreenReceiver()
        }
        
        handler.post(checkRunnable)
        
        return START_STICKY
    }

    private fun registerScreenReceiver() {
        if (screenUnlockReceiver == null) {
            screenUnlockReceiver = ScreenUnlockReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenUnlockReceiver, filter)
            Log.d("IronLockService", "ScreenUnlockReceiver registered")
        }
    }

    private fun unregisterScreenReceiver() {
        screenUnlockReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d("IronLockService", "ScreenUnlockReceiver unregistered")
            } catch (e: Exception) {
                Log.w("IronLockService", "Receiver already unregistered")
            }
            screenUnlockReceiver = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        unregisterScreenReceiver()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "IronLock Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
