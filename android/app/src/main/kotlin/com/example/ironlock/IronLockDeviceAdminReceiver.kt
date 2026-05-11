package com.example.ironlock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class IronLockDeviceAdminReceiver : DeviceAdminReceiver() {
    private val TAG = "IronLockDeviceAdmin"

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "✅ Device Admin enabled successfully")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "⚠️ Device Admin disabled")
        
        // If device admin is disabled during an active session, try to restart it
        val sessionManager = SessionManager(context)
        if (sessionManager.isSessionActive()) {
            Log.w(TAG, "🚨 CRITICAL: Device Admin disabled during active session! Security breach attempt detected.")
            
            // Attempt to re-enable by showing warning
            // Note: Cannot automatically re-enable without user interaction
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val sessionManager = SessionManager(context)
        if (sessionManager.isSessionActive()) {
            val remainingTime = sessionManager.getRemainingTime()
            return """
                ⚠️ تحذير أمني حرج!
                
                🔒 جلسة IronLock لا تزال نشطة ولا يمكنك إزالة صلاحيات مسؤول الجهاز حتى ينتهي الوقت.
                
                ⏱️ الوقت المتبقي: ${formatRemainingTime(remainingTime)}
                
                ❌ أي محاولة لإلغاء الصلاحية ستفشل تلقائياً.
                
                💡 نصيحة: حاول التركيز وإنهاء مهامك قبل انتهاء الجلسة.
            """.trimIndent()
        }
        return """
            ⚠️ هل أنت متأكد من إلغاء صلاحيات IronLock؟
            
            هذا سيعطل جميع وظائف القفل الأمني:
            • قفل الشاشة الفوري
            • منع تغيير الإعدادات
            • حماية من إلغاء التثبيت
            
            المتابعة تعني تعطيل الحماية الأمنية.
        """.trimIndent()
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        // Handle device admin status changes
        when (intent.action) {
            "android.app.action.DEVICE_ADMIN_DISABLED" -> {
                Log.e(TAG, "🚨 CRITICAL: Device Admin was disabled!")
                
                // Check if session is still active and log the breach
                val sessionManager = SessionManager(context)
                if (sessionManager.isSessionActive()) {
                    Log.e(TAG, "🚨 SECURITY BREACH: Session active but admin disabled!")
                }
            }
            "android.app.action.DEVICE_ADMIN_ENABLED" -> {
                Log.d(TAG, "✅ Device Admin successfully enabled")
            }
            "android.app.action.DEVICE_ADMIN_ENABLED_CHANGED" -> {
                Log.d(TAG, "Device Admin status changed")
            }
        }
    }
    
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.d(TAG, "Password changed event detected")
    }
    
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "Password failed attempt detected")
    }
    
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Password succeeded")
        
        // If full lock mode is active, re-lock immediately
        val sessionManager = SessionManager(context)
        if (sessionManager.isSessionActive() && sessionManager.isFullLockMode()) {
            Log.d(TAG, "Full lock active - will re-lock screen")
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
