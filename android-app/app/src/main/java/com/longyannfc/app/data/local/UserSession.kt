package com.longyannfc.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UserProfile(
    val username: String = "",
    val session: String = "",
    val openid: String = "",
    val nickname: String = "",
    val vip: Boolean = false
)

@Singleton
class UserSession @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("longyan_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _user = MutableStateFlow(loadUser())
    val user: StateFlow<UserProfile> = _user

    var deviceUuid: String = prefs.getString(KEY_DEVICE_UUID, "") ?: ""
        set(value) {
            field = value
            prefs.edit { putString(KEY_DEVICE_UUID, value) }
        }

    var userKeys: MutableList<String> = loadStringList(KEY_USER_KEYS).toMutableList()
    var historyKeys: MutableList<String> = loadStringList(KEY_HISTORY_KEYS).toMutableList()
    var publicKeys: MutableList<String> = loadStringList(KEY_PUBLIC_KEYS).toMutableList()
    var debugMode: Boolean = prefs.getBoolean(KEY_DEBUG, false)
        set(value) {
            field = value
            prefs.edit { putBoolean(KEY_DEBUG, value) }
        }

    val username: String get() = _user.value.username
    val session: String get() = _user.value.session
    val isLoggedIn: Boolean get() = session.isNotBlank() && username.isNotBlank()

    fun saveUser(profile: UserProfile) {
        _user.value = profile
        prefs.edit { putString(KEY_USER, gson.toJson(profile)) }
    }

    fun clearUser() {
        saveUser(UserProfile())
    }

    fun persistKeys() {
        prefs.edit {
            putString(KEY_USER_KEYS, gson.toJson(userKeys))
            putString(KEY_HISTORY_KEYS, gson.toJson(historyKeys))
            putString(KEY_PUBLIC_KEYS, gson.toJson(publicKeys))
        }
    }

    private fun loadUser(): UserProfile {
        val raw = prefs.getString(KEY_USER, null) ?: return UserProfile()
        return runCatching { gson.fromJson(raw, UserProfile::class.java) }.getOrDefault(UserProfile())
    }

    private fun loadStringList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return runCatching { gson.fromJson<List<String>>(raw, type) }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_USER = "user"
        private const val KEY_DEVICE_UUID = "device_uuid"
        private const val KEY_USER_KEYS = "user_keys"
        private const val KEY_HISTORY_KEYS = "history_keys"
        private const val KEY_PUBLIC_KEYS = "public_keys"
        private const val KEY_DEBUG = "debug"
    }
}
