package com.example.ironlock

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

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

                // 2. Block Settings app entirely during active session
                if (packageName == "com.android.settings") {
                    Log.w(TAG, "Settings app blocked during active session.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }

                // 3. Block any attempt to access accessibility settings
                if (packageName == "com.android.settings" || 
                    event.className?.toString()?.contains("accessibility", ignoreCase = true) == true) {
                    Log.w(TAG, "Attempted access to accessibility settings. Redirecting home.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }

                // 4. Block Developer Options and USB Debugging
                if (event.className?.toString()?.contains("development", ignoreCase = true) == true ||
                    event.text?.any { it.toString().contains("USB debugging", ignoreCase = true) } == true) {
                    Log.w(TAG, "Developer options access blocked.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }

                // 5. Block Date & Time settings to prevent time manipulation
                if (event.className?.toString()?.contains("date", ignoreCase = true) == true ||
                    event.className?.toString()?.contains("time", ignoreCase = true) == true) {
                    Log.w(TAG, "Date/Time settings access blocked.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }

                // 6. Block App Info pages for IronLock itself
                if (packageName == "com.android.settings" && 
                    (event.text?.any { it.toString().contains("IronLock", ignoreCase = true) } == true ||
                     event.text?.any { it.toString().contains("com.example.ironlock", ignoreCase = true) } == true)) {
                    Log.w(TAG, "IronLock app info page blocked.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }

                // 7. Block notification panel expansion attempts
                if (packageName == "com.android.systemui" && 
                    event.className?.toString()?.contains("StatusBar", ignoreCase = true) == true) {
                    // Allow status bar but monitor closely
                    return
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
