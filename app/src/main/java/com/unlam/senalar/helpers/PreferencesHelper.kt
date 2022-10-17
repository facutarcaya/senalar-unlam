package com.unlam.senalar.helpers

import android.content.Context

class PreferencesHelper(context: Context) {
    private val sharedPreferences = context.applicationContext.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)

    fun getBooleanPreference(preferenceName: String): Boolean {
        return sharedPreferences.getBoolean(preferenceName, false)
    }

    fun setBooleanPreference(preferenceName: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(preferenceName, value).apply()
    }

    fun getStringPreference(preferenceName: String): String? {
        return sharedPreferences.getString(preferenceName, null)
    }

    fun setStringPreference(preferenceName: String, value: String) {
        sharedPreferences.edit().putString(preferenceName, value).apply()
    }

    companion object {
        // General
        const val SHARED_PREF_NAME = "SENALAR_PREFERENCES"

        // Individual Preferences
        const val SOUND_ON_PREF = "SOUND_ON_PREF"
        const val LANGUAGE_TRANSLATION = "LANG_TRANSLATION"
        const val COUNTRY_TRANSLATION = "COUNTRY_TRANSLATION"
        const val CREDIT_CARD_SUBSCRIPTION = "CREDIT_CARD_SUBSCRIPTION"
        const val EMAIL_SUBSCRIPTION = "EMAIL_SUBSCRIPTION"
        const val IS_USER_SUBSCRIBED = "IS_USER_SUBSCRIBED"
    }
}