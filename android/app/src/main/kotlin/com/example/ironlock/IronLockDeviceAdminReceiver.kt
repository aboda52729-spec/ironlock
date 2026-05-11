package com.example.ironlock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class IronLockDeviceAdminReceiver : DeviceAdminReceiver() {
    private val TAG = "IronLockDeviceAdmin"

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device Admin disabled")
        
        // If device admin is disabled during an active session, try to restart it
        val sessionManager = SessionManager(context)
        if (sessionManager.isSessionActive()) {
            Log.w(TAG, "Device Admin disabled during active session! This is a security breach attempt.")
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val sessionManager = SessionManager(context)
        if (sessionManager.isSessionActive()) {
            return """
                ⚠️ تحذير أمني حرج!
                
                القفل الحديدي لا يزال مفعلاً ولا يمكنك إزالة صلاحيات مسؤول الجهاز حتى ينتهي الوقت.
                
                أي محاولة لإلغاء الصلاحية ستفشل تلقائياً.
                
                الوقت المتبقي: ${formatRemainingTime(sessionManager.getRemainingTime())}
            """.trimIndent()
        }
        return "هل أنت متأكد من إلغاء صلاحيات IronLock؟ هذا سيعطل جميع وظائف القفل."
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        // Handle device admin status changes
        when (intent.action) {
            "android.app.action.DEVICE_ADMIN_DISABLED" -> {
                Log.e(TAG, "CRITICAL: Device Admin was disabled!")
            }
            "android.app.action.DEVICE_ADMIN_ENABLED" -> {
                Log.d(TAG, "Device Admin successfully enabled")
            }
        }
    }
    
    private fun formatRemainingTime(millis: Long): String {
        if (millis <= 0) return "00:00:00"
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
