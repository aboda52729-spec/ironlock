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

class MainActivity: FlutterActivity() {
    private val CHANNEL = "ironlock_channel"

    override fun onResume() {
        super.onResume()
        val sessionManager = SessionManager(this)
        if (sessionManager.isSessionActive()) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
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
                        
                        result.success(true)
                    } catch (e: Exception) {
                        android.util.Log.e("IronLock", "Failed to start session", e)
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
        }
    }

    private fun makeEmergencyCall() {
        val sessionManager = SessionManager(this)
        val contact = sessionManager.getEmergencyContact()
        if (contact != null) {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:$contact")
            }
            startActivity(intent)
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

