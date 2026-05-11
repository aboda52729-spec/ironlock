package com.example.ironlock

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.KeyEvent

class IronLockAccessibilityService : AccessibilityService() {
    private lateinit var sessionManager: SessionManager
    private lateinit var overlayController: BlockOverlayController
    private val TAG = "IronLockService"
    private var consecutiveBlockCount = 0
    private var lastBlockedPackage: String = ""
    private var blockTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        sessionManager = SessionManager(this)
        overlayController = BlockOverlayController(this)
        
        // Configure for maximum protection
        serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityService.FEEDBACK_GENERIC
            flags = (AccessibilityService.FLAG_DEFAULT or
                    AccessibilityService.FLAG_REPORT_VIEW_IDS or
                    AccessibilityService.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityService.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityService.FLAG_REQUEST_FILTER_KEY_EVENTS)
            notificationTimeout = 0
        }
        
        Log.d(TAG, "Accessibility Service Connected with enhanced protection")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (!sessionManager.isSessionActive()) {
            overlayController.hide()
            return
        }

        val eventType = event.eventType
        val packageName = event.packageName?.toString() ?: return
        
        // Handle different event types
        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowChange(packageName, event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                handleViewInteraction(packageName, event)
            }
        }
    }
    
    private fun handleWindowChange(packageName: String, event: AccessibilityEvent) {
        if (!sessionManager.isSessionActive()) {
            overlayController.hide()
            return
        }

        // Critical Security Checks - Block attempts to bypass the lock
        if (sessionManager.isSessionActive()) {
            // 1. Block Package Installers to prevent uninstallation
            if (packageName.contains("packageinstaller", ignoreCase = true) ||
                packageName.contains("com.android.pm") ||
                packageName.contains("com.google.android.packageinstaller")) {
                Log.w(TAG, "🚫 BLOCKED: Package installer access attempt")
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBlockingOverlay("لا يمكن إلغاء التثبيت أثناء الجلسة!")
                return
            }

            // 2. Block Settings app entirely during active session
            if (packageName == "com.android.settings") {
                val className = event.className?.toString() ?: ""
                
                // Allow only emergency-related settings
                if (!className.contains("emergency", ignoreCase = true) &&
                    !className.contains("dialer", ignoreCase = true)) {
                    Log.w(TAG, "🚫 BLOCKED: Settings app access during session")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    showBlockingOverlay("الإعدادات مقفلة أثناء الجلسة!")
                    return
                }
            }

            // 3. Block accessibility settings changes
            if (packageName == "com.android.settings" && 
                (event.className?.toString()?.contains("accessibility", ignoreCase = true) == true ||
                 event.text?.any { it.toString().contains("إمكانية الوصول", ignoreCase = true) } == true ||
                 event.text?.any { it.toString().contains("accessibility", ignoreCase = true) } == true)) {
                Log.w(TAG, "🚫 BLOCKED: Accessibility settings modification attempt")
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBlockingOverlay("لا يمكن تعديل إمكانية الوصول!")
                return
            }

            // 4. Block Developer Options and USB Debugging
            if (event.className?.toString()?.contains("development", ignoreCase = true) == true ||
                event.className?.toString()?.contains("developer", ignoreCase = true) == true ||
                event.text?.any { it.toString().contains("USB debugging", ignoreCase = true) } == true ||
                event.text?.any { it.toString().contains("تصحيح USB", ignoreCase = true) } == true) {
                Log.w(TAG, "🚫 BLOCKED: Developer options access attempt")
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBlockingOverlay("خيارات المطور مقفلة!")
                return
            }

            // 5. Block Date & Time settings to prevent time manipulation
            if (event.className?.toString()?.let { 
                it.contains("date", ignoreCase = true) || 
                it.contains("time", ignoreCase = true) 
            } == true ||
                event.text?.any { 
                    it.toString().contains("التاريخ", ignoreCase = true) ||
                    it.toString().contains("الوقت", ignoreCase = true) ||
                    it.toString().contains("Date", ignoreCase = true) ||
                    it.toString().contains("Time", ignoreCase = true)
                } == true) {
                Log.w(TAG, "🚫 BLOCKED: Date/Time settings access attempt")
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBlockingOverlay("لا يمكن تغيير الوقت!")
                return
            }

            // 6. Block App Info pages for IronLock itself
            if (packageName == "com.android.settings" && 
                (event.text?.any { 
                    it.toString().contains("IronLock", ignoreCase = true) ||
                    it.toString().contains("com.example.ironlock", ignoreCase = true)
                } == true)) {
                Log.w(TAG, "🚫 BLOCKED: IronLock app info page access")
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBlockingOverlay("لا يمكن الوصول لمعلومات التطبيق!")
                return
            }

            // 7. Block Security settings
            if (packageName == "com.android.settings" && 
                (event.className?.toString()?.contains("security", ignoreCase = true) == true ||
                 event.text?.any { it.toString().contains("أمان", ignoreCase = true) } == true)) {
                Log.w(TAG, "🚫 BLOCKED: Security settings access")
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBlockingOverlay("إعدادات الأمان مقفلة!")
                return
            }

            // 8. Block Battery optimization settings
            if (packageName == "com.android.settings" && 
                (event.text?.any { 
                    it.toString().contains("battery", ignoreCase = true) ||
                    it.toString().contains("بطارية", ignoreCase = true)
                } == true)) {
                Log.w(TAG, "🚫 BLOCKED: Battery settings access")
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        checkAndBlock(packageName)
    }
    
    private fun handleViewInteraction(packageName: String, event: AccessibilityEvent) {
        // Monitor for rapid repeated attempts to access blocked content
        if (!sessionManager.isSessionActive()) return
        
        if (packageName != "com.example.ironlock" && 
            !packageName.contains("systemui") &&
            sessionManager.isFullLockMode()) {
            
            val currentTime = System.currentTimeMillis()
            if (packageName == lastBlockedPackage && currentTime - blockTimestamp < 1000) {
                consecutiveBlockCount++
                if (consecutiveBlockCount >= 3) {
                    // User is repeatedly trying to access blocked apps - reinforce lock
                    Log.w(TAG, "⚠️ Repeated access attempts detected ($consecutiveBlockCount)")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    overlayController.show()
                }
            } else {
                consecutiveBlockCount = 0
            }
            blockTimestamp = currentTime
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
                "com.samsung.android.dialer",
                "com.android.emergency"
            )
            if (emergencyPackages.contains(packageName)) {
                overlayController.hide()
                return
            }

            Log.d(TAG, "🔒 Full Lock active. Blocking: $packageName")
            overlayController.show()
            
            if (packageName != "com.android.systemui" && packageName != lastBlockedPackage) {
                lastBlockedPackage = packageName
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            return
        }

        if (sessionManager.shouldBlockApp(packageName)) {
            Log.d(TAG, "🔒 Blocking app: $packageName")
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
    
    private fun showBlockingOverlay(message: String) {
        overlayController.show()
        // Optionally update overlay text with the blocking reason
    }

    override fun onInterrupt() {}
    
    override fun onDestroy() {
        super.onDestroy()
        overlayController.hide()
        Log.d(TAG, "Accessibility Service Destroyed")
    }
    
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // Intercept key events during active session
        if (!sessionManager.isSessionActive()) return false
        
        // Block home button, recent apps, and back button during full lock
        if (sessionManager.isFullLockMode()) {
            when (event?.keyCode) {
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_APP_SWITCH,
                KeyEvent.KEYCODE_BACK -> {
                    Log.d(TAG, "🚫 Blocked key event: ${event.keyCode}")
                    return true // Consume the event
                }
            }
        }
        return false
    }
}
