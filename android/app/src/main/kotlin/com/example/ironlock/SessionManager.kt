package com.example.ironlock

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException

class SessionManager(context: Context) {
    private val PREF_NAME = "IronLockSession"
    private val KEY_END_TIME = "endTime"
    private val KEY_SELECTED_APPS = "selectedApps"
    private val KEY_IS_FULL_LOCK_MODE = "isFullLockMode"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun startSession(durationMillis: Long, isFullLockMode: Boolean, selectedApps: List<String>) {
        val endTime = System.currentTimeMillis() + durationMillis
        
        val jsonArray = JSONArray()
        for (app in selectedApps) {
            jsonArray.put(app)
        }

        // Use commit() instead of apply() to ensure data is written synchronously 
        // before the ForegroundService starts and checks the session state.
        prefs.edit()
            .putLong(KEY_END_TIME, endTime)
            .putBoolean(KEY_IS_FULL_LOCK_MODE, isFullLockMode)
            .putString(KEY_SELECTED_APPS, jsonArray.toString())
            .commit() 
    }

    fun clearSession() {
        prefs.edit().clear().commit()
    }

    fun isSessionActive(): Boolean {
        val endTime = prefs.getLong(KEY_END_TIME, 0)
        return System.currentTimeMillis() < endTime
    }

    fun getEndTime(): Long {
        return prefs.getLong(KEY_END_TIME, 0)
    }

    fun isFullLockMode(): Boolean {
        return prefs.getBoolean(KEY_IS_FULL_LOCK_MODE, true)
    }

    fun shouldBlockApp(packageName: String): Boolean {
        if (packageName == "com.example.ironlock") return false
        
        // Critical Whitelist: Never block these system components to prevent soft-bricks
        val systemWhitelist = setOf(
            "com.android.systemui",
            "com.android.settings", // Handled separately in Accessibility Service
            "com.google.android.inputmethod.latin", // Gboard
            "com.android.phone",
            "com.android.server.telecom"
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
