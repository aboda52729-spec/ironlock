package com.example.ironlock

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.content.Intent

class IronLockAccessibilityService : AccessibilityService() {
    private lateinit var sessionManager: SessionManager
    private lateinit var overlayController: BlockOverlayController
    private val TAG = "IronLockService"

    override fun onServiceConnected() {
        super.onServiceConnected()
        sessionManager = SessionManager(this)
        overlayController = BlockOverlayController(this)
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val packageName = event.packageName?.toString() ?: return
            
            // Critical Check: Prevent user from entering Settings to disable Accessibility or Device Admin
            if (sessionManager.isSessionActive()) {
                if (packageName == "com.android.settings") {
                    // Check if they are trying to reach our specific settings page
                    val className = event.className?.toString() ?: ""
                    if (className.contains("AccessibilitySettings") || className.contains("DeviceAdminSettings")) {
                        Log.w(TAG, "User trying to bypass security. Redirecting home.")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return
                    }
                }
            }

            checkAndBlock(packageName)
        }
    }
    
    private var lastBlockedPackage: String = ""

    private fun checkAndBlock(packageName: String) {
        if (!sessionManager.isSessionActive()) {
            lastBlockedPackage = ""
            overlayController.hide()
            return
        }

        // Ignore our own app's events
        if (packageName == "com.example.ironlock") {
            overlayController.hide()
            return
        }

        // SystemUI and LockScreen handling
        if (packageName == "com.android.systemui") {
            // We usually don't block SystemUI to allow notifications/status bar view,
            // but we ensure overlay doesn't cover it if we are just blocking specific apps.
            return
        }

        if (sessionManager.isFullLockMode()) {
            // Emergency call exceptions
            val emergencyPackages = setOf(
                "com.android.phone",
                "com.android.server.telecom",
                "com.android.dialer",
                "com.google.android.dialer",
                "com.samsung.android.dialer"
            )
            if (emergencyPackages.contains(packageName)) {
                overlayController.hide()
                return
            }

            Log.d(TAG, "Full Lock Mode active. Enforcing...")
            
            // Ensure screen stays locked if unlocked
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(applicationContext, IronLockDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(componentName)) {
                // We let the ScreenUnlockReceiver handle the hard lock.
                // Here we just show the overlay as backup.
                overlayController.show()
            } else {
                overlayController.show()
                if (packageName != "com.android.systemui") {
                   performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
            return
        }

        // ===== Specific Apps Mode =====
        if (sessionManager.shouldBlockApp(packageName)) {
            Log.d(TAG, "Blocking app: $packageName")
            overlayController.show()
            
            if (packageName != lastBlockedPackage) {
                lastBlockedPackage = packageName
                // Instead of just HOME, we can also show a "Blocked" screen
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        } else {
            // Allow this app
            Log.d(TAG, "App allowed: $packageName")
            lastBlockedPackage = ""
            overlayController.hide()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
        overlayController.hide()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        overlayController.hide()
    }
}
