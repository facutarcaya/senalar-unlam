package com.example.senalar.helpers

import android.content.Context

class PreferencesHelper(context: Context) {
    private val sharedPreferences = context.applicationContext.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)

    fun getBooleanPreference(preferenceName: String): Boolean {
        return sharedPreferences.getBoolean(preferenceName, false)
    }

    fun setBooleanPreference(preferenceName: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(preferenceName, value).apply()
    }

    companion object {
        // General
        const val SHARED_PREF_NAME = "SENALAR_PREFERENCES"

        // Individual Preferences
        const val SOUND_ON_PREF = "SOUND_ON_PREF"
    }
}