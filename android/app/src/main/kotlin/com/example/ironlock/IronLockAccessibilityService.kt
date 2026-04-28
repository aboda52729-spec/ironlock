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

        if (!sessionManager.isSessionActive()) {
            overlayController.hide()
            return
        }

        // Monitoring window changes & content changes for bypass attempts
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val packageName = event.packageName?.toString() ?: return
            
            // SYSTEM SETTINGS PROTECTION: The number one bypass vector
            if (packageName == "com.android.settings") {
                val className = event.className?.toString() ?: ""
                
                // Block access to critical system configuration during session
                val criticalSettingsKeywords = setOf(
                    "AccessibilitySettings", 
                    "DeviceAdminSettings",
                    "ManageApplications", // Apps list (to force stop)
                    "InstalledAppDetails", // Specific App info
                    "Date", "Time", // Date/Time settings (just in case)
                    "Developer" // Developer options
                )

                if (criticalSettingsKeywords.any { className.contains(it) }) {
                    Log.w(TAG, "Security violation: Attempted access to critical settings.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
                
                // If they are in App Info for IronLock itself, definitely block
                if (event.text.toString().contains("ironlock", ignoreCase = true)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
            }

            checkAndBlock(packageName)
        }
    }
    
    private var lastBlockedPackage: String = ""

    private fun checkAndBlock(packageName: String) {
        // Defensive check: if session ended between events
        if (!sessionManager.isSessionActive()) {
            overlayController.hide()
            return
        }

        // Ignore our own app
        if (packageName == "com.example.ironlock") {
            overlayController.hide()
            return
        }

        // White-listed system components
        val systemSafe = setOf("com.android.systemui")
        if (systemSafe.contains(packageName)) {
            // Usually don't block SystemUI to keep phone alive, 
            // but we might hide overlay if specifically allowed.
            return
        }

        if (sessionManager.isFullLockMode()) {
            // Allow emergency calling
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

            Log.d(TAG, "Full Lock: Enforcing...")
            overlayController.show()
            
            // Force the user home if they try to navigate away in Full Lock
            if (packageName != "com.android.systemui" && packageName != lastBlockedPackage) {
                lastBlockedPackage = packageName
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        // ===== Specific Apps Mode =====
        if (sessionManager.shouldBlockApp(packageName)) {
            Log.d(TAG, "Blocking app: $packageName")
            overlayController.show()
            
            if (packageName != lastBlockedPackage) {
                lastBlockedPackage = packageName
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        } else {
            overlayController.hide()
            lastBlockedPackage = ""
        }
    }

    override fun onInterrupt() {}
    
    override fun onDestroy() {
        super.onDestroy()
        overlayController.hide()
    }
}

