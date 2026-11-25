package com.example.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.OAuthTokenState
import com.example.data.model.DevicePreferences
import com.example.data.network.GmailMessageSummary
import com.example.data.network.GoogleOAuthManager
import com.example.ui.shell.GoogleServicesUiState
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.OsPaletteKey
import com.example.ui.theme.OsTheme

// --- 13. GMAIL APP (Real OAuth 2.0 PKCE + Gmail REST v1) ---
@Composable
fun GmailApp(
  oauthClientId: String,
  tokenState: OAuthTokenState,
  googleState: GoogleServicesUiState,
  onConnectOAuth: () -> Unit,
  onRefreshFolder: (String) -> Unit,
  onSendEmail: (String, String, String) -> Unit,
  onCreateGoogleAccountInBrowser: () -> Unit,
  onOpenSettings: () -> Unit,
  onCloseApp: () -> Unit,
) {
  var showCompose by remember { mutableStateOf(false) }
  var composeInitialTo by remember { mutableStateOf("") }
  var composeInitialSubject by remember { mutableStateOf("") }
  var composeInitialBody by remember { mutableStateOf("") }
  var viewingEmail by remember { mutableStateOf<GmailMessageSummary?>(null) }

  LaunchedEffect(tokenState.isConnected) {
    if (tokenState.isConnected && googleState.gmailMessages.isEmpty()) {
      onRefreshFolder(googleState.selectedGmailLabel)
    }
  }

  Scaffold(
    topBar = {
      VirtualAppTopBar("Gmail", "Google OAuth 2.0 + Gmail API v1", onBack = onCloseApp) {
        if (tokenState.isConnected) {
          IconButton(onClick = { onRefreshFolder(googleState.selectedGmailLabel) }) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Gmail")
          }
        }
      }
    },
    floatingActionButton = {
      if (tokenState.isConnected) {
        FloatingActionButton(
          onClick = {
            composeInitialTo = ""
            composeInitialSubject = ""
            composeInitialBody = ""
            showCompose = true
          }
        ) {
          Icon(Icons.Rounded.Edit, contentDescription = "Compose email")
        }
      }
    },
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      if (!tokenState.isConnected) {
        OAuthNotConnectedCard(
          serviceName = "Gmail",
          description =
            "Gmail requires a real Google Cloud OAuth 2.0 Client ID (with Gmail API enabled). Never faked with mock emails.",
          oauthClientId = oauthClientId,
          lastError = tokenState.lastError ?: googleState.oauthFeedback,
          onConnectOAuth = onConnectOAuth,
          onCreateGoogleAccountInBrowser = onCreateGoogleAccountInBrowser,
          onOpenSettings = onOpenSettings,
        )
      } else {
        Column(modifier = Modifier.fillMaxSize()) {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
          ) {
            items(listOf("INBOX" to "Inbox", "SENT" to "Sent", "DRAFT" to "Drafts", "TRASH" to "Trash")) {
              (labelId, title) ->
              FilterChip(
                selected = googleState.selectedGmailLabel == labelId,
                onClick = { onRefreshFolder(labelId) },
                label = { Text(title) },
              )
            }
          }

          if (!googleState.gmailSendStatus.isNullOrBlank()) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
              Text(googleState.gmailSendStatus!!, modifier = Modifier.padding(12.dp))
            }
          }
          if (!googleState.gmailError.isNullOrBlank()) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
              Text(
                googleState.gmailError!!,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          if (googleState.isGmailLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
          } else if (googleState.gmailMessages.isEmpty()) {
            EmptyStatePanel(
              Icons.Rounded.Mail,
              "Folder empty",
              "Tap Refresh above to fetch messages in ${googleState.selectedGmailLabel}.",
            )
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(googleState.gmailMessages, key = { it.id }) { msg ->
                Card(
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth().clickable { viewingEmail = msg },
                ) {
                  Column(modifier = Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                      Text(
                        text = msg.from,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                      )
                      Text(msg.date, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      msg.subject,
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = msg.snippet,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  viewingEmail?.let { msg ->
    AlertDialog(
      onDismissRequest = { viewingEmail = null },
      title = { Text(msg.subject) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("From: ${msg.from}", style = MaterialTheme.typography.labelLarge)
          Text("Date: ${msg.date}", style = MonoCodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
          HorizontalDivider()
          Text(msg.snippet.ifBlank { "(No preview content)" }, style = MaterialTheme.typography.bodyMedium)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            composeInitialTo = msg.from.substringAfter("<").substringBefore(">").ifBlank { msg.from }
            composeInitialSubject = if (msg.subject.startsWith("Re:", true)) msg.subject else "Re: ${msg.subject}"
            composeInitialBody = "\n\n--- Original ---\n${msg.snippet}"
            viewingEmail = null
            showCompose = true
          }
        ) {
          Icon(Icons.AutoMirrored.Rounded.Reply, null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Reply")
        }
      },
      dismissButton = {
        Row {
          TextButton(
            onClick = {
              composeInitialTo = ""
              composeInitialSubject = if (msg.subject.startsWith("Fwd:", true)) msg.subject else "Fwd: ${msg.subject}"
              composeInitialBody = "\n\n--- Forwarded from ${msg.from} ---\n${msg.snippet}"
              viewingEmail = null
              showCompose = true
            }
          ) {
            Icon(Icons.AutoMirrored.Rounded.Forward, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Forward")
          }
          TextButton(onClick = { viewingEmail = null }) { Text("Close") }
        }
      },
    )
  }

  if (showCompose) {
    var to by remember(composeInitialTo) { mutableStateOf(composeInitialTo) }
    var subject by remember(composeInitialSubject) { mutableStateOf(composeInitialSubject) }
    var body by remember(composeInitialBody) { mutableStateOf(composeInitialBody) }
    AlertDialog(
      onDismissRequest = { showCompose = false },
      title = { Text("Compose Email") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("To") }, singleLine = true)
          OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true)
          OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, minLines = 3)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSendEmail(to.trim(), subject.trim(), body.trim())
            showCompose = false
          },
          enabled = to.isNotBlank() && subject.isNotBlank(),
        ) {
          Text("Send")
        }
      },
      dismissButton = { TextButton(onClick = { showCompose = false }) { Text("Cancel") } },
    )
  }
}

// --- 14. YOUTUBE APP (Real OAuth 2.0 PKCE + YouTube Data API v3) ---
@Composable
fun YouTubeApp(
  oauthClientId: String,
  tokenState: OAuthTokenState,
  googleState: GoogleServicesUiState,
  onConnectOAuth: () -> Unit,
  onSearchYouTube: (String) -> Unit,
  onLoadLiked: () -> Unit,
  onOpenVideoInBrowser: (String) -> Unit,
  onCreateGoogleAccountInBrowser: () -> Unit,
  onOpenSettings: () -> Unit,
  onCloseApp: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("USA Tech News") }

  LaunchedEffect(tokenState.isConnected) {
    if (tokenState.isConnected && googleState.youtubeVideos.isEmpty()) {
      onSearchYouTube(searchQuery)
    }
  }

  Scaffold(
    topBar = {
      VirtualAppTopBar("YouTube", "YouTube Data API v3", onBack = onCloseApp) {
        if (tokenState.isConnected) {
          TextButton(onClick = onLoadLiked) { Text("Liked") }
        }
      }
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      if (!tokenState.isConnected) {
        OAuthNotConnectedCard(
          serviceName = "YouTube",
          description =
            "YouTube Data API v3 uses your Google Cloud OAuth 2.0 Client ID for live video search and liked playlists.",
          oauthClientId = oauthClientId,
          lastError = tokenState.lastError ?: googleState.oauthFeedback,
          onConnectOAuth = onConnectOAuth,
          onCreateGoogleAccountInBrowser = onCreateGoogleAccountInBrowser,
          onOpenSettings = onOpenSettings,
        )
      } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search YouTube US…") },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(onClick = { onSearchYouTube(searchQuery) }) { Text("Search") }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (!googleState.youtubeError.isNullOrBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
          ) {
            Text(googleState.youtubeError!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
          }
        }

        if (googleState.isYouTubeLoading) {
          Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else if (googleState.youtubeVideos.isEmpty()) {
          EmptyStatePanel(Icons.Rounded.PlayCircle, "No videos loaded", "Search above or tap Liked.")
        } else {
          LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(googleState.youtubeVideos, key = { it.videoId }) { video ->
              Card(
                shape = RoundedCornerShape(18.dp),
                modifier =
                  Modifier.fillMaxWidth().clickable {
                    onOpenVideoInBrowser("https://m.youtube.com/watch?v=${video.videoId}")
                  },
              ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                  AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 112.dp, height = 68.dp).clip(RoundedCornerShape(12.dp)),
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${video.channelTitle} • ${video.publishedAt}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Icon(Icons.Rounded.PlayArrow, contentDescription = "Watch", tint = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OAuthNotConnectedCard(
  serviceName: String,
  description: String,
  oauthClientId: String,
  lastError: String?,
  onConnectOAuth: () -> Unit,
  onCreateGoogleAccountInBrowser: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(10.dp))
        Text("$serviceName OAuth 2.0 Required", style = MaterialTheme.typography.titleLarge)
      }
      Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text("Redirect URI for Google Cloud Console:", style = MaterialTheme.typography.labelMedium)
          Text(GoogleOAuthManager.REDIRECT_URI, style = MonoCodeStyle)
        }
      }

      if (!lastError.isNullOrBlank()) {
        Text(lastError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
      }

      if (oauthClientId.isBlank()) {
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
          Icon(Icons.Rounded.Settings, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Configure OAuth Client ID in Settings")
        }
      } else {
        Button(onClick = onConnectOAuth, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
          Text("Connect Google Account (PKCE)")
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
          Text("Edit Client ID in Settings")
        }
      }

      OutlinedButton(
        onClick = onCreateGoogleAccountInBrowser,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
      ) {
        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Create New Google Account (Browser)")
      }
      Text(
        text = "Opens Google's official signup page in Chrome. Standard phone/CAPTCHA verification applies and cannot be bypassed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

// --- 15. SETTINGS APP ---
@Composable
fun SettingsApp(
  userEmail: String?,
  userName: String?,
  isCloudSignedIn: Boolean,
  preferences: DevicePreferences,
  tokenState: OAuthTokenState,
  googleFeedback: String?,
  onUpdatePreferences: ((DevicePreferences) -> DevicePreferences) -> Unit,
  onConnectOAuth: () -> Unit,
  onDisconnectOAuth: () -> Unit,
  onCreateGoogleAccountInBrowser: () -> Unit,
  onSignOut: () -> Unit,
  onOpenDiagnostics: () -> Unit,
  onCloseApp: () -> Unit,
) {
  var clientIdDraft by remember(preferences.oauthClientId) { mutableStateOf(preferences.oauthClientId) }

  Scaffold(
    topBar = { VirtualAppTopBar("Settings", "Region, Accounts & Material You", onBack = onCloseApp) }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // 1. Account / Local Mode Status
      item {
        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(userName ?: "Signed-In User", style = MaterialTheme.typography.titleMedium)
                Text(userEmail ?: "Google Account", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).testTag("settings_sign_out_btn"),
              ) {
                Text(if (isCloudSignedIn) stringResource(R.string.sign_out) else "Switch Account")
              }
              OutlinedButton(
                onClick = onCreateGoogleAccountInBrowser,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
              ) {
                Text("New Google Acct")
              }
            }
          }
        }
      }

      // 2. Dedicated Device Region Settings Section
      item {
        DeviceRegionSettingsSection(
          preferences = preferences,
          onUpdatePreferences = onUpdatePreferences,
          onOpenDiagnostics = onOpenDiagnostics,
        )
      }

      // 3. Google Cloud OAuth 2.0 Client ID Section
      item {
        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.oauth_setup_title), style = MaterialTheme.typography.titleMedium)
            Text(
              "To connect real Gmail & YouTube data, create an OAuth 2.0 Client ID in Google Cloud Console (enable Gmail API & YouTube Data API v3) and add the redirect URI below:",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
              Text(
                text = "Redirect URI: ${GoogleOAuthManager.REDIRECT_URI}",
                style = MonoCodeStyle,
                modifier = Modifier.padding(10.dp),
              )
            }
            OutlinedTextField(
              value = clientIdDraft,
              onValueChange = { clientIdDraft = it },
              label = { Text("Google Cloud OAuth Client ID") },
              placeholder = { Text("Paste your Google Cloud OAuth Client ID") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("oauth_client_id_input"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              Button(
                onClick = { onUpdatePreferences { it.copy(oauthClientId = clientIdDraft.trim()) } },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
              ) {
                Text("Save Client ID")
              }
              if (tokenState.isConnected) {
                OutlinedButton(
                  onClick = onDisconnectOAuth,
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(16.dp),
                ) {
                  Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Disconnect")
                }
              } else {
                Button(
                  onClick = {
                    onUpdatePreferences { it.copy(oauthClientId = clientIdDraft.trim()) }
                    onConnectOAuth()
                  },
                  enabled = clientIdDraft.isNotBlank(),
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(16.dp),
                ) {
                  Text("Authorize")
                }
              }
            }
            if (!googleFeedback.isNullOrBlank()) {
              Text(googleFeedback, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (tokenState.isConnected) {
              Text(
                text = "OAuth Connected (${tokenState.grantedScopes.split(" ").size} scopes active)",
                style = MaterialTheme.typography.labelLarge,
                color = OsTheme.extended.verifiedEmerald,
              )
            }
          }
        }
      }

      // 4. Material You Theme
      item {
        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Material You Personalization", style = MaterialTheme.typography.titleMedium)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
              Switch(
                checked = preferences.darkMode,
                onCheckedChange = { dark -> onUpdatePreferences { it.copy(darkMode = dark) } },
              )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(OsPaletteKey.entries) { palette ->
                FilterChip(
                  selected = preferences.themePalette == palette.name,
                  onClick = { onUpdatePreferences { it.copy(themePalette = palette.name) } },
                  label = { Text(palette.label) },
                )
              }
            }
          }
        }
      }

      // 5. Device Info, Storage & Permissions Summary
      item {
        DeviceInfoAndPermissionsCard(isCloudSignedIn = isCloudSignedIn)
      }
    }
  }
}
