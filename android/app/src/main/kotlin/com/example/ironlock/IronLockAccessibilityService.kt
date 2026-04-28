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

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val packageName = event.packageName?.toString() ?: return
            
            // Critical Check: Prevent user from bypassing security
            if (sessionManager.isSessionActive()) {
                // 1. Block Package Installers to prevent uninstallation
                if (packageName == "com.android.packageinstaller" || 
                    packageName == "com.google.android.packageinstaller" ||
                    packageName.contains("packageinstaller")) {
                    Log.w(TAG, "User trying to uninstall app or change permissions. Blocking.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }

                // 2. Settings Protection
                if (packageName == "com.android.settings") {
                    if (sessionManager.isFullLockMode()) {
                        Log.w(TAG, "Full Lock Mode: All Settings blocked.")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return
                    }

                    val className = event.className?.toString() ?: ""
                    val text = event.text?.toString() ?: ""
                    
                    // Critical Settings Pages
                    val forbiddenClasses = setOf(
                        "AccessibilitySettings", 
                        "DeviceAdminSettings",
                        "ManageApplications",
                        "InstalledAppDetails",
                        "AppDetailsActivity",
                        "DevelopmentSettings",
                        "DateTimeSettings"
                    )

                    if (forbiddenClasses.any { className.contains(it) } || 
                        text.contains("IronLock", ignoreCase = true)) {
                        Log.w(TAG, "Attempted access to critical settings ($className). Redirecting home.")
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
            overlayController.hide()
            return
        }

        if (packageName == "com.example.ironlock") {
            overlayController.hide()
            return
        }

        if (packageName == "com.android.systemui") {
            return
        }

        if (sessionManager.isFullLockMode()) {
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

            Log.d(TAG, "Full Lock active. Showing overlay.")
            overlayController.show()
            
            if (packageName != "com.android.systemui" && packageName != lastBlockedPackage) {
                lastBlockedPackage = packageName
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        if (sessionManager.shouldBlockApp(packageName)) {
            Log.d(TAG, "Blocking app: $packageName")
            overlayController.show()
            
            if (packageName != lastBlockedPackage) {
                lastBlockedPackage = packageName
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        } else {
            lastBlockedPackage = ""
            overlayController.hide()
        }
    }

    override fun onInterrupt() {}
    
    override fun onDestroy() {
        super.onDestroy()
        overlayController.hide()
    }
}
