package com.example.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.theme.MonoCodeStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

@Composable
fun LockAuthScreen(
  isFirebaseConfigured: Boolean = true,
  onAuthSuccess: () -> Unit,
  onContinueWithoutGmail: () -> Unit = {},
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var isBrowserLoading by remember { mutableStateOf(false) }
  var showBrowserFallback by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var clockNowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

  LaunchedEffect(isFirebaseConfigured) {
    if (isFirebaseConfigured) {
      GoogleAuthHelper.attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = onAuthSuccess,
        onUnauthenticated = {},
        scope = scope,
      )
    }
    while (true) {
      clockNowMillis = System.currentTimeMillis()
      delay(10_000L)
    }
  }

  val timeStr = remember(clockNowMillis) {
    SimpleDateFormat("h:mm", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("America/New_York")
    }.format(Date(clockNowMillis))
  }
  val dateStr = remember(clockNowMillis) {
    SimpleDateFormat("EEE, MMM d • z", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("America/New_York")
    }.format(Date(clockNowMillis))
  }

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Image(
      painter = painterResource(id = R.drawable.img_wallpaper_cobalt),
      contentDescription = null,
      modifier = Modifier.fillMaxSize().alpha(0.35f),
      contentScale = ContentScale.Crop,
    )

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                  MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                )
            )
          )
    )

    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(start = 24.dp, end = 64.dp, top = 20.dp, bottom = 20.dp)
          .widthIn(max = 520.dp)
          .align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
      // Lock Screen Clock Header
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = "Locked",
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "VIRTUAL OS LOCKED",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
          text = timeStr,
          style = MaterialTheme.typography.displayLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = dateStr,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Main Pixel Welcome Card
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
          ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(22.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.PhoneAndroid,
                contentDescription = "USA Virtual Android Pro",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(R.string.lock_screen_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            HighlightPill(
              icon = Icons.Rounded.Security,
              label = "Zero-Trust Sync",
              modifier = Modifier.weight(1f),
            )
            HighlightPill(
              icon = Icons.Rounded.Public,
              label = "Honest IP Check",
              modifier = Modifier.weight(1f),
            )
          }

          if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          Button(
            onClick = {
              errorMessage = null
              isLoading = true
              GoogleAuthHelper.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  showBrowserFallback = true
                  errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = {
                  isLoading = false
                  showBrowserFallback = true
                  errorMessage =
                    "Google Sign-In did not complete. Tap 'Continue Without Gmail' below to open Virtual OS directly."
                },
              )
            },
            enabled = isFirebaseConfigured && !isLoading && !isBrowserLoading,
            shape = RoundedCornerShape(24.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("Unlocking Virtual OS…")
            } else {
              Text(
                text = stringResource(R.string.sign_in_google),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          FilledTonalButton(
            onClick = onContinueWithoutGmail,
            enabled = !isLoading && !isBrowserLoading,
            shape = RoundedCornerShape(24.dp),
            modifier =
              Modifier.fillMaxWidth().height(50.dp).testTag("continue_without_gmail_button"),
          ) {
            Text(
              text = "Continue Without Gmail (Local Mode)",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
          }

          if (showBrowserFallback && isFirebaseConfigured) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
              onClick = {
                errorMessage = null
                isBrowserLoading = true
                GoogleAuthHelper.signInWithGoogleBrowserFallback(
                  context = context,
                  onAuthSuccess = {
                    isBrowserLoading = false
                    onAuthSuccess()
                  },
                  onAuthError = { msg ->
                    isBrowserLoading = false
                    errorMessage = msg
                  },
                  scope = scope,
                )
              },
              enabled = !isLoading && !isBrowserLoading,
              shape = RoundedCornerShape(24.dp),
              modifier =
                Modifier.fillMaxWidth().height(48.dp).testTag("google_browser_sign_in_button"),
            ) {
              if (isBrowserLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Opening Google Sign-In…")
              } else {
                Text("Browser Google Sign-In")
              }
            }
          }
        }
      }

      Text(
        text = "16 Apps • Local or Cloud Sync • Real OAuth PKCE & Diagnostics",
        style = MonoCodeStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun HighlightPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

