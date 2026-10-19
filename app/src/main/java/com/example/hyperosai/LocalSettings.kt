package com.example.hyperosai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class LocalSettings(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "hyperos_ai_secure_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    var apiKey: String
        get() = prefs.getString("gemini_key", "").orEmpty()
        set(value) { prefs.edit().putString("gemini_key", value).apply() }
    var hyperOsVersion: String
        get() = prefs.getString("hyperos_version", "HyperOS 2").orEmpty()
        set(value) { prefs.edit().putString("hyperos_version", value).apply() }
    var palette: String
        get() = prefs.getString("palette", "Violet").orEmpty()
        set(value) { prefs.edit().putString("palette", value).apply() }
    var amoled: Boolean
        get() = prefs.getBoolean("amoled", false)
        set(value) { prefs.edit().putBoolean("amoled", value).apply() }
}
