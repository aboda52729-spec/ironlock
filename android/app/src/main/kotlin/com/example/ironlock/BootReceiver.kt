package com.example.ironlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action || Intent.ACTION_LOCKED_BOOT_COMPLETED == intent.action) {
            val sessionManager = SessionManager(context)
            Log.d("IronLockBoot", "Device Booted. Checking active session...")
            
            if (sessionManager.isSessionActive()) {
                Log.d("IronLockBoot", "Active session found! Restarting Foreground Services.")
                val serviceIntent = Intent(context, IronLockForegroundService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d("IronLockBoot", "No active session.")
            }
        }
    }
}
