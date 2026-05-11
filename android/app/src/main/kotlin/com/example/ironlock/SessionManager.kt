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

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Starts a new session. 
     * Uses a decrementing timeRemaining counter to prevent bypass via time changes or reboots.
     */
    fun startSession(durationMillis: Long, isFullLockMode: Boolean, selectedApps: List<String>, emergencyContact: String?) {
        val jsonArray = JSONArray()
        for (app in selectedApps) {
            jsonArray.put(app)
        }

        prefs.edit()
            .putLong(KEY_TIME_REMAINING, durationMillis)
            .putLong(KEY_LAST_UPDATE_TIME, SystemClock.elapsedRealtime())
            .putBoolean(KEY_IS_FULL_LOCK_MODE, isFullLockMode)
            .putString(KEY_SELECTED_APPS, jsonArray.toString())
            .putString(KEY_EMERGENCY_CONTACT, emergencyContact)
            .commit()
    }

    fun getEmergencyContact(): String? {
        return prefs.getString(KEY_EMERGENCY_CONTACT, null)
    }


    fun clearSession() {
        prefs.edit().clear().commit()
    }

    fun isSessionActive(): Boolean {
        return getRemainingTime() > 0
    }

    fun getRemainingTime(): Long {
        return prefs.getLong(KEY_TIME_REMAINING, 0)
    }

    /**
     * Synchronizes and decrements the remaining time.
     * Called by the ForegroundService periodically.
     */
    fun updateRemainingTime() {
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE_TIME, 0)
        val now = SystemClock.elapsedRealtime()
        
        if (lastUpdate == 0L || now < lastUpdate) {
            // Handle reboot or first run
            prefs.edit().putLong(KEY_LAST_UPDATE_TIME, now).commit()
            return
        }

        val diff = now - lastUpdate
        val currentRemaining = prefs.getLong(KEY_TIME_REMAINING, 0)
        
        if (currentRemaining > 0) {
            val newRemaining = Math.max(0, currentRemaining - diff)
            prefs.edit()
                .putLong(KEY_TIME_REMAINING, newRemaining)
                .putLong(KEY_LAST_UPDATE_TIME, now)
                .commit()
        }
    }

    fun isFullLockMode(): Boolean {
        return prefs.getBoolean(KEY_IS_FULL_LOCK_MODE, true)
    }

    fun shouldBlockApp(packageName: String): Boolean {
        if (packageName == "com.example.ironlock") return false
        
        // Critical Whitelist: Never block these system components to prevent soft-bricks
        val systemWhitelist = setOf(
            "com.android.systemui",
            "com.android.settings", 
            "com.google.android.inputmethod.latin", // Gboard
            "com.android.phone",
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.samsung.android.dialer"
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
}
