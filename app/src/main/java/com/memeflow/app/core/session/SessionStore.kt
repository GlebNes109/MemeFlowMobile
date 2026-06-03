package com.memeflow.app.core.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "memeflow_session")

data class StoredSession(
    val userId: String,
    val login: String,
    val displayName: String,
    val avatarUrl: String?,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Long
)

class SessionStore(private val context: Context) {
    private object Keys {
        val userId = stringPreferencesKey("user_id")
        val login = stringPreferencesKey("login")
        val displayName = stringPreferencesKey("display_name")
        val avatarUrl = stringPreferencesKey("avatar_url")
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val accessTokenExpiresAt = longPreferencesKey("access_token_expires_at")
    }

    suspend fun read(): StoredSession? {
        val preferences = context.sessionDataStore.data.first()
        val userId = preferences[Keys.userId] ?: return null
        val accessToken = preferences[Keys.accessToken] ?: return null
        val refreshToken = preferences[Keys.refreshToken] ?: return null
        val accessTokenExpiresAt = preferences[Keys.accessTokenExpiresAt] ?: 0L
        return StoredSession(
            userId = userId,
            login = preferences[Keys.login] ?: "",
            displayName = preferences[Keys.displayName] ?: "",
            avatarUrl = preferences[Keys.avatarUrl],
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = accessTokenExpiresAt
        )
    }

    suspend fun write(session: StoredSession) {
        context.sessionDataStore.edit { preferences ->
            preferences[Keys.userId] = session.userId
            preferences[Keys.login] = session.login
            preferences[Keys.displayName] = session.displayName
            if (session.avatarUrl != null) {
                preferences[Keys.avatarUrl] = session.avatarUrl
            } else {
                preferences.remove(Keys.avatarUrl)
            }
            preferences[Keys.accessToken] = session.accessToken
            preferences[Keys.refreshToken] = session.refreshToken
            preferences[Keys.accessTokenExpiresAt] = session.accessTokenExpiresAt
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
