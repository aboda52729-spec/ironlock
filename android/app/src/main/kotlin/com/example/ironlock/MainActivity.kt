package com.example.ironlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.os.Build
import android.util.Log

class MainActivity: FlutterActivity() {
    private val CHANNEL = "ironlock_channel"
    private val TAG = "IronLockMainActivity"

    override fun onResume() {
        super.onResume()
        val sessionManager = SessionManager(this)
        if (sessionManager.isSessionActive()) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            Log.d(TAG, "Session active - FLAG_SECURE enabled")
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startSession" -> {
                    try {
                        // Dart int maps to Java Integer, NOT Long. Use Number to handle both.
                        val durationNumber = call.argument<Number>("durationMillis")
                        val durationMillis = durationNumber?.toLong() ?: 0L
                        val isFullLockMode = call.argument<Boolean>("isFullLockMode") ?: true
                        val selectedApps = call.argument<List<String>>("selectedApps") ?: emptyList()
                        val emergencyContact = call.argument<String>("emergencyContact")

                        if (durationMillis <= 0L) {
                            Log.w(TAG, "Invalid duration: $durationMillis")
                            result.success(false)
                            return@setMethodCallHandler
                        }

                        val sessionManager = SessionManager(this)
                        sessionManager.startSession(durationMillis, isFullLockMode, selectedApps, emergencyContact)
                        
                        val serviceIntent = Intent(this, IronLockForegroundService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                        
                        if (isFullLockMode) {
                            lockScreenNow()
                        }
                        
                        Log.d(TAG, "Session started: duration=$durationMillis, fullLock=$isFullLockMode")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start session", e)
                        result.success(false)
                    }
                }
                "getEmergencyContact" -> {
                    val sessionManager = SessionManager(this)
                    result.success(sessionManager.getEmergencyContact())
                }
                "makeEmergencyCall" -> {
                   makeEmergencyCall()
                   result.success(null)
                }
                "checkAccessibilityPermission" -> {
                    val enabled = checkAccessibilityPermission()
                    result.success(enabled)
                }
                "requestAccessibilityPermission" -> {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    result.success(null)
                }
                "checkOverlayPermission" -> {
                    val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(this)
                    } else {
                        true
                    }
                    result.success(enabled)
                }
                "requestOverlayPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        startActivity(intent)
                    }
                    result.success(null)
                }
                "isSessionActive" -> {
                    val sessionManager = SessionManager(this)
                    if (sessionManager.isSessionActive()) {
                        result.success(sessionManager.getRemainingTime())
                    } else {
                        result.success(0L)
                    }
                }
                // ===== Device Admin Methods =====
                "isDeviceAdminEnabled" -> {
                    result.success(isDeviceAdminEnabled())
                }
                "requestDeviceAdmin" -> {
                    val componentName = ComponentName(this, IronLockDeviceAdminReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "IronLock يحتاج صلاحية مسؤول الجهاز لإطفاء الشاشة وقفل هاتفك بشكل حقيقي.")
                    }
                    startActivity(intent)
                    result.success(null)
                }
                "lockScreen" -> {
                    lockScreenNow()
                    result.success(true)
                }
                "getSessionInfo" -> {
                    val sessionManager = SessionManager(this)
                    val info = sessionManager.getSessionInfo()
                    result.success(mapOf(
                        "remainingTime" to info["remainingTime"] as? Long ?: 0,
                        "isFullLockMode" to info["isFullLockMode"] as? Boolean ?: false,
                        "emergencyContact" to info["emergencyContact"] as? String ?: "",
                        "sessionStartTime" to info["sessionStartTime"] as? Long ?: 0,
                        "lastUpdateTime" to info["lastUpdateTime"] as? Long ?: 0,
                        "currentTime" to info["currentTime"] as? Long ?: 0
                    ))
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun isDeviceAdminEnabled(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, IronLockDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    private fun lockScreenNow() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, IronLockDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(componentName)) {
            dpm.lockNow()
            Log.d(TAG, "Screen locked successfully")
        } else {
            Log.w(TAG, "Cannot lock screen - Device Admin not active")
        }
    }

    private fun makeEmergencyCall() {
        val sessionManager = SessionManager(this)
        val contact = sessionManager.getEmergencyContact()
        if (contact != null && contact.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:$contact")
            }
            startActivity(intent)
            Log.d(TAG, "Emergency call initiated to: $contact")
        } else {
            Log.w(TAG, "No emergency contact set")
        }
    }

    private fun checkAccessibilityPermission(): Boolean {
        val serviceName = ComponentName(this, IronLockAccessibilityService::class.java).flattenToString()
        val settingValue = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        if (settingValue == null) return false
        
        val splitter = android.text.TextUtils.SimpleStringSplitter(':')
        splitter.setString(settingValue)
        
        while (splitter.hasNext()) {
            val component = splitter.next()
            if (component.equals(serviceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}

