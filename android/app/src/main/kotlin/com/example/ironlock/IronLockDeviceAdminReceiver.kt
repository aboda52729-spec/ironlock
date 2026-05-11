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
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val sessionManager = SessionManager(context)
        if (sessionManager.isSessionActive()) {
            return "تحذير: القفل لا يزال مفعلاً! لا يمكنك إزالة صلاحيات مسؤول الجهاز حتى ينتهي الوقت."
        }
        return "هل أنت متأكد من إلغاء صلاحيات IronLock؟"
    }
}
