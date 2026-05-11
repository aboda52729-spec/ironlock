package com.example.ironlock

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONException

class SessionManager(context: Context) {
    private val PREF_NAME = "IronLockSession"
    private val KEY_SELECTED_APPS = "selectedApps"
    private val KEY_IS_FULL_LOCK_MODE = "isFullLockMode"
    private val KEY_TIME_REMAINING = "timeRemaining"
    private val KEY_LAST_UPDATE_TIME = "lastUpdateTime"
    private val KEY_EMERGENCY_CONTACT = "emergencyContact"
    private val KEY_SESSION_START_TIME = "sessionStartTime"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Starts a new session. 
     * Uses a decrementing timeRemaining counter to prevent bypass via time changes or reboots.
     * SystemClock.elapsedRealtime() is used because it continues counting during sleep and is not affected by system time changes.
     */
    fun startSession(durationMillis: Long, isFullLockMode: Boolean, selectedApps: List<String>, emergencyContact: String?) {
        val jsonArray = JSONArray()
        for (app in selectedApps) {
            jsonArray.put(app)
        }

        val currentTime = SystemClock.elapsedRealtime()
        
        prefs.edit()
            .putLong(KEY_TIME_REMAINING, durationMillis)
            .putLong(KEY_LAST_UPDATE_TIME, currentTime)
            .putLong(KEY_SESSION_START_TIME, currentTime)
            .putBoolean(KEY_IS_FULL_LOCK_MODE, isFullLockMode)
            .putString(KEY_SELECTED_APPS, jsonArray.toString())
            .putString(KEY_EMERGENCY_CONTACT, emergencyContact)
            .apply()
    }

    fun getEmergencyContact(): String? {
        return prefs.getString(KEY_EMERGENCY_CONTACT, null)
    }


    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isSessionActive(): Boolean {
        return getRemainingTime() > 0
    }

    fun getRemainingTime(): Long {
        // Always calculate remaining time based on elapsed realtime
        val storedRemaining = prefs.getLong(KEY_TIME_REMAINING, 0)
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE_TIME, 0)
        val now = SystemClock.elapsedRealtime()
        
        if (lastUpdate == 0L || storedRemaining <= 0) {
            return storedRemaining
        }
        
        // Calculate how much time has passed since last update
        val elapsedSinceLastUpdate = now - lastUpdate
        
        // Return the remaining time after subtracting elapsed time
        return maxOf(0, storedRemaining - elapsedSinceLastUpdate)
    }

    /**
     * Synchronizes and decrements the remaining time.
     * Called by the ForegroundService periodically.
     * This method is idempotent and safe against time manipulation attacks.
     */
    fun updateRemainingTime() {
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE_TIME, 0)
        val now = SystemClock.elapsedRealtime()
        
        if (lastUpdate == 0L) {
            // First run or reset - just update the timestamp
            prefs.edit().putLong(KEY_LAST_UPDATE_TIME, now).apply()
            return
        }
        
        if (now < lastUpdate) {
            // This should never happen with SystemClock.elapsedRealtime(), but handle it anyway
            // It could indicate a device reboot or edge case
            prefs.edit().putLong(KEY_LAST_UPDATE_TIME, now).apply()
            return
        }

        val diff = now - lastUpdate
        val currentRemaining = prefs.getLong(KEY_TIME_REMAINING, 0)
        
        if (currentRemaining > 0 && diff > 0) {
            val newRemaining = maxOf(0, currentRemaining - diff)
            prefs.edit()
                .putLong(KEY_TIME_REMAINING, newRemaining)
                .putLong(KEY_LAST_UPDATE_TIME, now)
                .apply()
            
            if (newRemaining == 0) {
                // Session expired - clean up
                clearSession()
            }
        }
    }

    fun isFullLockMode(): Boolean {
        return prefs.getBoolean(KEY_IS_FULL_LOCK_MODE, true)
    }

    fun shouldBlockApp(packageName: String): Boolean {
        // Never block IronLock itself
        if (packageName == "com.example.ironlock") return false
        
        // Critical Whitelist: Never block these system components to prevent soft-bricks
        // These are essential for phone functionality and emergency calls
        val systemWhitelist = setOf(
            "com.android.systemui",           // System UI (status bar, notifications)
            "com.android.settings",           // Settings (needed for emergency access)
            "com.google.android.inputmethod.latin", // Gboard
            "com.android.phone",              // Phone dialer
            "com.android.server.telecom",     // Telecom service
            "com.google.android.dialer",      // Google Dialer
            "com.samsung.android.dialer",     // Samsung Dialer
            "com.android.incallui",           // In-call UI
            "com.google.android.apps.messaging", // Messages (for emergency SMS)
            "com.samsung.android.messaging"   // Samsung Messages
        )
        
        if (systemWhitelist.contains(packageName)) return false

        if (isFullLockMode()) {
            return true
        } else {
            val selectedAppsJson = prefs.getString(KEY_SELECTED_APPS, "[]")
            try {
                val jsonArray = JSONArray(selectedAppsJson)
                for (i in 0 until jsonArray.length()) {
                    if (jsonArray.getString(i) == packageName) {
                        return true
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return false
        }
    }
    
    /**
     * Get session info for debugging
     */
    fun getSessionInfo(): Map<String, Any> {
        return mapOf(
            "remainingTime" to getRemainingTime(),
            "isFullLockMode" to isFullLockMode(),
            "emergencyContact" to getEmergencyContact().orEmpty(),
            "sessionStartTime" to prefs.getLong(KEY_SESSION_START_TIME, 0),
            "lastUpdateTime" to prefs.getLong(KEY_LAST_UPDATE_TIME, 0),
            "currentTime" to SystemClock.elapsedRealtime()
        )
    }
}
