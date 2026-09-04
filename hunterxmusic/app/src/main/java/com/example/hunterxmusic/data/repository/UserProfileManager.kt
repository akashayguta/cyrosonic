package com.example.hunterxmusic.data.repository

import android.content.Context

/**
 * Persisted identity for personalization: first name and country/region.
 * Completely free of age fields.
 */
class UserProfileManager(context: Context) {
    private val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    var firstName: String
        get() = prefs.getString(KEY_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_NAME, value.trim()).apply()

    var country: String
        get() = prefs.getString(KEY_COUNTRY, "Global").orEmpty()
        set(value) = prefs.edit().putString(KEY_COUNTRY, value.trim()).apply()

    val isOnboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false) || firstName.isNotBlank()

    val displayName: String
        get() = firstName.ifBlank { "Listener" }

    /**
     * Atomically completes onboarding and writes all profile preferences.
     */
    fun completeOnboarding(name: String, country: String) {
        val cleanName = name.trim().ifBlank { "Listener" }
        val cleanCountry = country.trim().ifBlank { "Global" }
        prefs.edit()
            .putString(KEY_NAME, cleanName)
            .putString(KEY_COUNTRY, cleanCountry)
            .putBoolean(KEY_ONBOARDED, true)
            .commit()
    }

    companion object {
        private const val KEY_NAME = "first_name"
        private const val KEY_COUNTRY = "country"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
