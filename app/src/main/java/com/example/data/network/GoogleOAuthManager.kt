package com.example.data.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.example.data.local.OAuthTokenStore
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class GoogleOAuthManager(
  private val context: Context,
  private val tokenStore: OAuthTokenStore,
) {
  private val httpClient = OkHttpClient()

  suspend fun launchPkceAuthorization(clientId: String): Result<Unit> =
    runCatching {
      val trimmedId = clientId.trim()
      require(trimmedId.isNotBlank()) {
        "Enter your Google Cloud OAuth 2.0 Client ID in Settings > Accounts & OAuth first."
      }
      val verifier = generateCodeVerifier()
      val challenge = generateCodeChallenge(verifier)
      val state = generateState()

      tokenStore.savePendingPkce(verifier, state, trimmedId)

      val authUri =
        Uri.parse(AUTH_ENDPOINT)
          .buildUpon()
          .appendQueryParameter("client_id", trimmedId)
          .appendQueryParameter("redirect_uri", REDIRECT_URI)
          .appendQueryParameter("response_type", "code")
          .appendQueryParameter("scope", REQUESTED_SCOPES)
          .appendQueryParameter("code_challenge", challenge)
          .appendQueryParameter("code_challenge_method", "S256")
          .appendQueryParameter("state", state)
          .appendQueryParameter("access_type", "offline")
          .appendQueryParameter("prompt", "consent")
          .build()

      val intent =
        Intent(Intent.ACTION_VIEW, authUri).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
    }

  suspend fun handleRedirectIntent(uri: Uri): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
          val msg = "OAuth denied: $error"
          tokenStore.saveError(msg)
          error(msg)
        }

        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")
        if (code.isNullOrBlank()) {
          error("Missing authorization code in redirect URI.")
        }

        val pending =
          tokenStore.readPendingPkce()
            ?: error("No pending OAuth PKCE session found. Please start authorization again.")
        val (verifier, expectedState, clientId) = pending

        if (returnedState != expectedState) {
          error("OAuth state mismatch. Aborting token exchange for security.")
        }

        val body =
          FormBody.Builder()
            .add("client_id", clientId)
            .add("code", code)
            .add("code_verifier", verifier)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .build()

        val request = Request.Builder().url(TOKEN_ENDPOINT).post(body).build()
        httpClient.newCall(request).execute().use { response ->
          val responseBody = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val detail =
              runCatching { JSONObject(responseBody).optString("error_description") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() } ?: responseBody.take(180)
            val errMsg = "Token exchange failed (${response.code}): $detail"
            tokenStore.saveError(errMsg)
            error(errMsg)
          }

          val json = JSONObject(responseBody)
          val accessToken = json.getString("access_token")
          val refreshToken = json.optString("refresh_token", "")
          val expiresIn = json.optLong("expires_in", 3600L)
          val scopes = json.optString("scope", REQUESTED_SCOPES)
          val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

          tokenStore.saveTokens(accessToken, refreshToken, expiresAt, scopes)
          accessToken
        }
      }
    }

  suspend fun disconnect() {
    tokenStore.clearTokens()
  }

  private fun generateCodeVerifier(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  }

  private fun generateCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  }

  private fun generateState(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  }

  companion object {
    const val REDIRECT_URI = "com.aistudio.usavirtualos.mrxpkv:/oauth2redirect"
    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val REQUESTED_SCOPES =
      "https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/youtube.readonly"
  }
}
