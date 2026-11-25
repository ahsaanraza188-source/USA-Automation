package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.oauthDataStore by preferencesDataStore(name = "virtual_os_oauth_tokens")

data class OAuthTokenState(
  val accessToken: String = "",
  val refreshToken: String = "",
  val expiresAtMillis: Long = 0L,
  val grantedScopes: String = "",
  val lastError: String? = null,
) {
  val isConnected: Boolean
    get() = accessToken.isNotBlank()
}

class OAuthTokenStore(private val context: Context) {

  private object Keys {
    val ACCESS_TOKEN = stringPreferencesKey("access_token")
    val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    val EXPIRES_AT = longPreferencesKey("expires_at_millis")
    val GRANTED_SCOPES = stringPreferencesKey("granted_scopes")
    val PENDING_VERIFIER = stringPreferencesKey("pending_verifier")
    val PENDING_STATE = stringPreferencesKey("pending_state")
    val PENDING_CLIENT_ID = stringPreferencesKey("pending_client_id")
    val LAST_ERROR = stringPreferencesKey("last_error")
  }

  val tokenState: Flow<OAuthTokenState> =
    context.oauthDataStore.data.map { prefs ->
      OAuthTokenState(
        accessToken = prefs[Keys.ACCESS_TOKEN].orEmpty(),
        refreshToken = prefs[Keys.REFRESH_TOKEN].orEmpty(),
        expiresAtMillis = prefs[Keys.EXPIRES_AT] ?: 0L,
        grantedScopes = prefs[Keys.GRANTED_SCOPES].orEmpty(),
        lastError = prefs[Keys.LAST_ERROR],
      )
    }

  suspend fun savePendingPkce(verifier: String, state: String, clientId: String) {
    context.oauthDataStore.edit { prefs ->
      prefs[Keys.PENDING_VERIFIER] = verifier
      prefs[Keys.PENDING_STATE] = state
      prefs[Keys.PENDING_CLIENT_ID] = clientId
      prefs.remove(Keys.LAST_ERROR)
    }
  }

  suspend fun readPendingPkce(): Triple<String, String, String>? {
    val prefs = context.oauthDataStore.data.first()
    val verifier = prefs[Keys.PENDING_VERIFIER].orEmpty()
    val state = prefs[Keys.PENDING_STATE].orEmpty()
    val clientId = prefs[Keys.PENDING_CLIENT_ID].orEmpty()
    return if (verifier.isNotBlank() && state.isNotBlank() && clientId.isNotBlank()) {
      Triple(verifier, state, clientId)
    } else {
      null
    }
  }

  suspend fun saveTokens(
    accessToken: String,
    refreshToken: String,
    expiresAtMillis: Long,
    scopes: String,
  ) {
    context.oauthDataStore.edit { prefs ->
      prefs[Keys.ACCESS_TOKEN] = accessToken
      if (refreshToken.isNotBlank()) {
        prefs[Keys.REFRESH_TOKEN] = refreshToken
      }
      prefs[Keys.EXPIRES_AT] = expiresAtMillis
      prefs[Keys.GRANTED_SCOPES] = scopes
      prefs.remove(Keys.PENDING_VERIFIER)
      prefs.remove(Keys.PENDING_STATE)
      prefs.remove(Keys.PENDING_CLIENT_ID)
      prefs.remove(Keys.LAST_ERROR)
    }
  }

  suspend fun saveError(message: String) {
    context.oauthDataStore.edit { prefs -> prefs[Keys.LAST_ERROR] = message }
  }

  suspend fun clearTokens() {
    context.oauthDataStore.edit { it.clear() }
  }
}
