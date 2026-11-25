package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.network.AppDiagnosticsLogger
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthHelper {
  private const val TAG = "GoogleAuthHelper"

  private tailrec fun Context.findActivity(): Activity? =
    when (this) {
      is Activity -> this
      is ContextWrapper -> baseContext.findActivity()
      else -> null
    }

  fun getSigningSha1(context: Context): String? =
    runCatching {
      val pm = context.packageManager
      val pkg = context.packageName
      val signatures =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
          val signingInfo = info.signingInfo ?: return null
          if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
          else signingInfo.signingCertificateHistory
        } else {
          @Suppress("DEPRECATION")
          pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
      val cert = signatures?.firstOrNull() ?: return null
      val digest = MessageDigest.getInstance("SHA-1").digest(cert.toByteArray())
      digest.joinToString(":") { "%02X".format(it) }
    }.getOrNull()

  fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
  ) {
    if (Firebase.auth.currentUser != null) {
      onAuthSuccess()
      return
    }
    val clientId =
      try {
        val resId =
          context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) context.getString(resId) else ""
      } catch (_: Exception) {
        ""
      }
    if (clientId.isBlank()) {
      onUnauthenticated()
      return
    }

    val googleIdOption =
      GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
    val hostContext = context.findActivity() ?: context

    scope.launch {
      try {
        val pending = Firebase.auth.pendingAuthResult
        if (pending != null) {
          pending.await()
          if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return@launch
          }
        }
        val result = credentialManager.getCredential(hostContext, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          Firebase.auth.signInWithCredential(authCredential).await()
          onAuthSuccess()
        } else {
          onUnauthenticated()
        }
      } catch (_: Exception) {
        onUnauthenticated()
      }
    }
  }

  fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
  ) {
    val clientId =
      try {
        val resId =
          context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) context.getString(resId) else ""
      } catch (_: Exception) {
        ""
      }
    if (clientId.isBlank()) {
      onAuthError("Google Sign-In configuration missing: google-services.json / default_web_client_id not found")
      return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
    val activityContext = context.findActivity() ?: context

    scope.launch {
      try {
        val result = credentialManager.getCredential(activityContext, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
          val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
          Firebase.auth.signInWithCredential(authCredential).await()
          if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
          } else {
            onAuthError("Firebase sign-in returned no user session.")
          }
        } else {
          onAuthError("Unexpected credential type returned.")
        }
      } catch (e: GetCredentialCancellationException) {
        val detail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: e.message.orEmpty()
        Log.w(TAG, "Google Sign-In flow cancelled or dismissed: $detail", e)
        val looksLikeConfigRejection =
          detail.contains("16", ignoreCase = true) ||
            detail.contains("10", ignoreCase = true) ||
            detail.contains("284", ignoreCase = true) ||
            detail.contains("developer", ignoreCase = true) ||
            detail.contains("mismatch", ignoreCase = true) ||
            detail.contains("blocked", ignoreCase = true) ||
            detail.contains("reauth", ignoreCase = true) ||
            detail.contains("failed", ignoreCase = true)
        if (looksLikeConfigRejection) {
          val sha1 = getSigningSha1(context)
          val shaNote = if (sha1 != null) "\nAPK SHA-1: $sha1" else ""
          AppDiagnosticsLogger.warn(
            component = "Google Auth",
            message = "Credential Manager rejected APK signature ($detail)",
            rootCause = sha1?.let { "APK SHA-1: $it" } ?: detail,
            recoveryHint = "Use Browser Google Sign-In or Continue Without Gmail (Local Mode).",
          )
          onAuthError("Google Play Services could not verify this APK ($detail). Use Browser Google Sign-In below.$shaNote")
        } else {
          onAuthCancelled()
        }
      } catch (e: Exception) {
        Log.e(TAG, "Google Sign-In failed", e)
        val sha1 = getSigningSha1(context)
        val shaNote = if (sha1 != null) "\nAPK SHA-1: $sha1" else ""
        val msg = e.localizedMessage ?: context.getString(R.string.auth_error_generic)
        AppDiagnosticsLogger.error(
          component = "Google Auth",
          message = "Google Sign-In failed",
          throwable = e,
          recoveryHint = "Try Browser Google Sign-In or Local Mode.",
        )
        onAuthError("$msg$shaNote")
      }
    }
  }

  fun signInWithGoogleBrowserFallback(
    context: Context,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
  ) {
    val activity = context.findActivity()
    if (activity == null) {
      onAuthError("Unable to open browser sign-in from this context.")
      return
    }

    val provider =
      OAuthProvider.newBuilder("google.com")
        .addCustomParameter("prompt", "select_account")
        .build()

    scope.launch {
      try {
        val pending = Firebase.auth.pendingAuthResult
        if (pending != null) {
          pending.await()
        } else {
          Firebase.auth.startActivityForSignInWithProvider(activity, provider).await()
        }
        if (Firebase.auth.currentUser != null) {
          onAuthSuccess()
        } else {
          onAuthError("Browser Google Sign-In completed without an active user session.")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Browser Google Sign-In fallback failed", e)
        AppDiagnosticsLogger.error(
          component = "Google Auth",
          message = "Browser Google Sign-In fallback failed",
          throwable = e,
          recoveryHint = "Ensure SHA-1 matches Firebase or use Local Mode without Gmail.",
        )
        onAuthError(e.localizedMessage ?: context.getString(R.string.auth_error_generic))
      }
    }
  }

  fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {},
  ) {
    scope.launch {
      withContext(NonCancellable) {
        try {
          credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
          Log.w(TAG, "Failed to clear credential state: ${e.message}")
        } finally {
          Firebase.auth.signOut()
          onSignOutComplete()
        }
      }
    }
  }
}
