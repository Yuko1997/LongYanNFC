package com.longyannfc.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure preferences wrapper for sensitive data like tokens and keys.
 */
@Singleton
class SecurePreferences @Inject constructor(
    private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("longyan_secure", Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        prefs.edit { putString(KEY_TOKEN, token) }
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit { remove(KEY_TOKEN) }
    }

    fun saveOpenId(openId: String) {
        prefs.edit { putString(KEY_OPENID, openId) }
    }

    fun getOpenId(): String? {
        return prefs.getString(KEY_OPENID, null)
    }

    fun saveUserPhone(phone: String) {
        prefs.edit { putString(KEY_PHONE, phone) }
    }

    fun getUserPhone(): String? {
        return prefs.getString(KEY_PHONE, null)
    }

    fun clearAll() {
        prefs.edit { clear() }
    }

    companion object {
        private const val KEY_TOKEN = "user_token"
        private const val KEY_OPENID = "user_openid"
        private const val KEY_PHONE = "user_phone"
    }
}
