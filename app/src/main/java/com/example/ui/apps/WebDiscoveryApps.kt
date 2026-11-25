package com.example.ui.apps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.VirtualContact
import com.example.data.model.VirtualFileRecord
import com.example.data.model.VirtualNote
import com.example.data.network.ProxyTunnelRuntimeState
import com.example.data.network.PublicIpReport
import com.example.domain.AppCategory
import com.example.domain.VirtualAppCatalog
import com.example.domain.VirtualAppId
import com.example.domain.VirtualAppSpec
import com.example.domain.VirtualLocationCatalog
import com.example.domain.VirtualLocationConfig
import com.example.domain.VirtualLocationMode
import com.example.ui.shell.GoogleServicesUiState
import com.example.ui.shell.UiState
import com.example.ui.theme.OsTheme
import java.net.URLEncoder

// --- 9. CHROME BROWSER APP (100% Persistent Cookies, Storage, Tabs, Proxy & Virtual GPS) ---
@Composable
fun ChromeBrowserApp(
  sessionManager: BrowserSessionManager,
  isActive: Boolean,
  initialUrl: String?,
  ipReport: PublicIpReport? = null,
  locationConfig: VirtualLocationConfig = VirtualLocationConfig(),
  proxyRuntime: ProxyTunnelRuntimeState? = null,
  onOpenVpnDiagnostics: () -> Unit = {},
  onConsumeInitialUrl: () -> Unit,
  onCloseApp: () -> Unit,
) {
  val browserState by sessionManager.uiState.collectAsStateWithLifecycle()
  val activeTab = browserState.activeTab
  var addressText by remember(activeTab.id, activeTab.url) { mutableStateOf(activeTab.url) }
  var showClearDataDialog by remember { mutableStateOf(false) }
  var showHistoryDialog by remember { mutableStateOf(false) }

  val animatedProgress by
    animateFloatAsState(
      targetValue = browserState.loadProgress / 100f,
      label = "chrome_load_progress",
    )

  LaunchedEffect(initialUrl, isActive) {
    if (isActive && !initialUrl.isNullOrBlank()) {
      sessionManager.openOrNavigate(initialUrl)
      onConsumeInitialUrl()
    }
  }

  DisposableEffect(Unit) {
    onDispose { sessionManager.persistImmediately() }
  }

  val isBookmarked =
    remember(browserState.bookmarks, activeTab.url) {
      browserState.bookmarks.any { it.url.equals(activeTab.url, ignoreCase = true) }
    }

  Scaffold(
    topBar = {
      VirtualAppTopBar(
        title = "Chrome",
        subtitle =
          if (browserState.syncedDomainsCount > 0)
            "${browserState.tabs.size} tab(s) • ${browserState.syncedDomainsCount} site session(s) synced"
          else "${browserState.tabs.size} tab(s) • Cloud Session Sync",
        onBack = {
          sessionManager.persistImmediately()
          onCloseApp()
        },
      ) {
        IconButton(
          onClick = { showHistoryDialog = true },
          modifier = Modifier.testTag("chrome_history_btn"),
        ) {
          Icon(Icons.Rounded.History, contentDescription = "Browsing history")
        }
        IconButton(
          onClick = { sessionManager.toggleBookmarkForCurrentPage() },
          modifier = Modifier.testTag("chrome_bookmark_btn"),
        ) {
          Icon(
            imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark current site",
            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = { showClearDataDialog = true },
          modifier = Modifier.testTag("chrome_clear_data_btn"),
        ) {
          Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear browsing data")
        }
        IconButton(
          onClick = {
            sessionManager.openNewTab(
              url = "https://duckduckgo.com/",
              title = "Private Tab",
              isIncognito = true,
            )
          },
          modifier = Modifier.testTag("chrome_incognito_tab_btn"),
        ) {
          Icon(Icons.Rounded.VisibilityOff, contentDescription = "New private tab")
        }
        IconButton(
          onClick = { sessionManager.openNewTab() },
          modifier = Modifier.testTag("chrome_new_tab_btn"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = "New browser tab")
        }
      }
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      if (activeTab.isIncognito) {
        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.VisibilityOff,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
              tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Private Tab — History & site data not saved",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
          }
        }
      }

      // Tab strip
      LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(browserState.tabs.size, key = { idx -> browserState.tabs[idx].id }) { index ->
          val tab = browserState.tabs[index]
          FilterChip(
            selected = index == browserState.activeTabIndex,
            onClick = { sessionManager.selectTab(index) },
            leadingIcon =
              if (tab.isIncognito) {
                {
                  Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = "Private tab",
                    modifier = Modifier.size(16.dp),
                  )
                }
              } else null,
            label = { Text(tab.title.take(16), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon =
              if (browserState.tabs.size > 1) {
                {
                  Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close tab",
                    modifier =
                      Modifier.size(16.dp).clickable { sessionManager.closeTab(index) },
                  )
                }
              } else null,
          )
        }
      }

      // Address bar + controls
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = { sessionManager.goBackInActiveTab() },
          enabled = browserState.canGoBack,
          modifier = Modifier.size(40.dp),
        ) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Browser back")
        }
        IconButton(
          onClick = { sessionManager.goForwardInActiveTab() },
          enabled = browserState.canGoForward,
          modifier = Modifier.size(40.dp),
        ) {
          Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Browser forward")
        }
        OutlinedTextField(
          value = addressText,
          onValueChange = { addressText = it },
          singleLine = true,
          shape = RoundedCornerShape(22.dp),
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = "Persistent cookies active",
              modifier = Modifier.size(16.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
          },
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
          keyboardActions = KeyboardActions(onGo = { sessionManager.openOrNavigate(addressText) }),
          modifier = Modifier.weight(1f).testTag("chrome_url_input"),
        )
        IconButton(
          onClick = { sessionManager.reloadActiveTab() },
          modifier = Modifier.size(40.dp),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Reload page")
        }
      }

      // Quick Bookmarks + Real IP / VPN / Virtual GPS indicators
      LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        item {
          FilterChip(
            selected = ipReport?.isVerifiedUsa == true || proxyRuntime?.isConnected == true,
            onClick = onOpenVpnDiagnostics,
            label = {
              Text(
                text =
                  when {
                    ipReport?.isVerifiedUsa == true -> "USA IP Active (${ipReport.ipAddress})"
                    proxyRuntime?.isConnected == true -> "Proxy (${proxyRuntime.activeEndpointLabel})"
                    ipReport != null -> "IP: ${ipReport.countryCode} • Setup US VPN"
                    else -> "Setup US VPN"
                  },
                maxLines = 1,
                color =
                  if (ipReport?.isVerifiedUsa == true) OsTheme.extended.verifiedEmerald
                  else MaterialTheme.colorScheme.onSurface,
              )
            },
          )
        }
        item {
          FilterChip(
            selected = locationConfig.enabled && locationConfig.mode == VirtualLocationMode.VIRTUAL_GPS,
            onClick = onOpenVpnDiagnostics,
            leadingIcon = {
              Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = {
              Text(
                text =
                  if (locationConfig.enabled && locationConfig.mode == VirtualLocationMode.VIRTUAL_GPS)
                    "GPS: ${locationConfig.city}, ${locationConfig.stateCode}"
                  else "GPS: ${locationConfig.mode.label}",
                maxLines = 1,
              )
            },
          )
        }
        item {
          FilterChip(
            selected = activeTab.url.contains("ipinfo.io", ignoreCase = true),
            onClick = { sessionManager.openOrNavigate("https://ipinfo.io/") },
            label = { Text("Check IP") },
          )
        }
        item {
          FilterChip(
            selected = activeTab.url.contains("browserleaks.com/geo", ignoreCase = true),
            onClick = { sessionManager.openOrNavigate("https://browserleaks.com/geo") },
            label = { Text("Test GPS") },
          )
        }
        items(
          browserState.bookmarks.filterNot {
            it.url == "https://ipinfo.io/" || it.url == "https://browserleaks.com/geo"
          },
          key = { it.url },
        ) { bookmark ->
          FilterChip(
            selected = activeTab.url.startsWith(bookmark.url, ignoreCase = true),
            onClick = { sessionManager.openOrNavigate(bookmark.url) },
            label = { Text(bookmark.title, maxLines = 1) },
          )
        }
      }

      // Interactive Browser Geolocation Permission Bar (ASK mode)
      browserState.pendingGeoPromptOrigin?.let { origin ->
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.fillMaxWidth().testTag("chrome_geo_prompt_bar"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "$origin requests location",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "Virtual GPS: ${locationConfig.city}, ${locationConfig.stateCode} (${locationConfig.formattedCoordinates})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              TextButton(onClick = { sessionManager.respondToPendingGeoPrompt(false) }) { Text("Block") }
              Button(onClick = { sessionManager.respondToPendingGeoPrompt(true) }) { Text("Allow") }
            }
          }
        }
      }

      AnimatedVisibility(
        visible = browserState.loadProgress in 1..99,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        LinearProgressIndicator(
          progress = { animatedProgress },
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (!browserState.isRestored) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      } else {
        key(activeTab.id) {
          AndroidView(
            factory = { ctx ->
              sessionManager.getOrCreateWebView(ctx, activeTab).apply {
                visibility = if (isActive) View.VISIBLE else View.GONE
              }
            },
            update = { wv ->
              wv.visibility = if (isActive) View.VISIBLE else View.GONE
              if (isActive) wv.onResume()
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
          )
        }
      }
    }
  }

  if (showClearDataDialog) {
    AlertDialog(
      onDismissRequest = { showClearDataDialog = false },
      title = { Text("Clear Browsing Data?") },
      text = {
        Text(
          "Your Gmail and website logins are normally kept saved permanently. Clear cookies, site storage, and open tabs only if you want to sign out of all websites in Chrome."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            sessionManager.clearBrowsingDataExplicitly()
            showClearDataDialog = false
          }
        ) {
          Text("Clear All")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDataDialog = false }) { Text("Keep Logins") }
      },
    )
  }

  if (showHistoryDialog) {
    AlertDialog(
      onDismissRequest = { showHistoryDialog = false },
      title = { Text("Browsing History") },
      text = {
        if (browserState.history.isEmpty()) {
          Text(
            "No visited URLs recorded in this session yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            items(browserState.history) { item ->
              Card(
                shape = RoundedCornerShape(12.dp),
                modifier =
                  Modifier.fillMaxWidth().clickable {
                    sessionManager.openOrNavigate(item.url)
                    showHistoryDialog = false
                  },
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    item.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showHistoryDialog = false }) { Text("Close") }
      },
      dismissButton = {
        if (browserState.history.isNotEmpty()) {
          TextButton(onClick = { sessionManager.clearHistory() }) { Text("Clear History") }
        }
      },
    )
  }
}

// --- 10. UNIFIED SEARCH APP ---
@Composable
fun SearchApp(
  initialQuery: String,
  notesState: UiState<List<VirtualNote>>,
  contactsState: UiState<List<VirtualContact>>,
  filesState: UiState<List<VirtualFileRecord>>,
  searchHistory: List<String> = emptyList(),
  onRecordSearch: (String) -> Unit = {},
  onClearSearchHistory: () -> Unit = {},
  onLaunchApp: (VirtualAppId) -> Unit,
  onSearchWeb: (String) -> Unit,
  onCloseApp: () -> Unit,
) {
  var query by remember(initialQuery) { mutableStateOf(initialQuery) }
  var selectedCategory by remember { mutableStateOf("All") }
  val categories = remember { listOf("All", "Web", "Images", "Videos", "News") }

  fun buildSearchUrl(q: String, category: String): String {
    val encoded = URLEncoder.encode(q, "UTF-8")
    return when (category) {
      "Images" -> "https://duckduckgo.com/?q=$encoded&ia=images&iax=images"
      "Videos" -> "https://duckduckgo.com/?q=$encoded&ia=videos&iax=videos"
      "News" -> "https://duckduckgo.com/?q=$encoded&ia=news&iar=news"
      else -> "https://duckduckgo.com/?q=$encoded"
    }
  }

  val matchingApps =
    remember(query) {
      VirtualAppCatalog.apps.filter {
        query.isBlank() ||
          it.title.contains(query, true) ||
          it.subtitle.contains(query, true)
      }
    }
  val matchingNotes =
    remember(query, notesState) {
      (notesState as? UiState.Success)?.data?.filter {
        query.isNotBlank() &&
          (it.title.contains(query, true) || it.content.contains(query, true))
      }.orEmpty()
    }
  val matchingContacts =
    remember(query, contactsState) {
      (contactsState as? UiState.Success)?.data?.filter {
        query.isNotBlank() &&
          (it.name.contains(query, true) || it.phone.contains(query, true))
      }.orEmpty()
    }
  val matchingFiles =
    remember(query, filesState) {
      (filesState as? UiState.Success)?.data?.filter {
        query.isNotBlank() && it.name.contains(query, true)
      }.orEmpty()
    }

  Scaffold(topBar = { VirtualAppTopBar("Search", "Apps, Local Cloud & Web", onBack = onCloseApp) }) {
    padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Type to search apps, notes, contacts, or web…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
          KeyboardActions(
            onSearch = {
              if (query.isNotBlank()) {
                onRecordSearch(query)
                onSearchWeb(buildSearchUrl(query, selectedCategory))
              }
            }
          ),
        modifier = Modifier.fillMaxWidth().testTag("unified_search_input"),
      )

      Spacer(modifier = Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { cat ->
          FilterChip(
            selected = selectedCategory == cat,
            onClick = { selectedCategory = cat },
            label = { Text(cat) },
          )
        }
      }

      if (searchHistory.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Recent Searches", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          TextButton(onClick = onClearSearchHistory) { Text("Clear") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(searchHistory) { past ->
            FilterChip(
              selected = false,
              onClick = { query = past },
              leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
              label = { Text(past, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
          }
        }
      }

      if (query.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = {
            onRecordSearch(query)
            onSearchWeb(buildSearchUrl(query, selectedCategory))
          },
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Rounded.Language, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Search \"$query\" ($selectedCategory) in Chrome")
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedCategory == "All" && matchingContacts.isNotEmpty()) {
          item { Text("Contacts", style = MaterialTheme.typography.labelLarge) }
          items(matchingContacts) { c ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onLaunchApp(VirtualAppId.CONTACTS) }) {
              Column(Modifier.padding(12.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(c.phone, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
        if (matchingNotes.isNotEmpty()) {
          item { Text("Notes", style = MaterialTheme.typography.labelLarge) }
          items(matchingNotes) { n ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onLaunchApp(VirtualAppId.NOTES) }) {
              Column(Modifier.padding(12.dp)) {
                Text(n.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                Text(n.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
        if (matchingFiles.isNotEmpty()) {
          item { Text("Files", style = MaterialTheme.typography.labelLarge) }
          items(matchingFiles) { f ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onLaunchApp(VirtualAppId.FILES) }) {
              Column(Modifier.padding(12.dp)) { Text(f.name, style = MaterialTheme.typography.titleMedium) }
            }
          }
        }

        item { Text("Apps", style = MaterialTheme.typography.labelLarge) }
        items(matchingApps, key = { it.appId.id }) { spec ->
          Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clickable { onLaunchApp(spec.appId) },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(spec.icon, contentDescription = spec.title, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(spec.title, style = MaterialTheme.typography.titleMedium)
                Text(spec.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }
}

// --- 11. MAPS APP (OpenStreetMap + Nominatim + Virtual GPS / Physical Location) ---
@SuppressLint("SetJavaScriptEnabled", "MissingPermission")
@Composable
fun MapsApp(
  googleState: GoogleServicesUiState,
  locationConfig: VirtualLocationConfig = VirtualLocationConfig(),
  onSearchPlaces: (String) -> Unit,
  onCloseApp: () -> Unit,
) {
  val context = LocalContext.current
  var query by remember { mutableStateOf("") }
  val isVirtualActive = locationConfig.enabled && locationConfig.mode == VirtualLocationMode.VIRTUAL_GPS
  var centerLat by remember(locationConfig.latitude, isVirtualActive) {
    mutableStateOf(if (isVirtualActive) locationConfig.latitude else 40.7128)
  }
  var centerLon by remember(locationConfig.longitude, isVirtualActive) {
    mutableStateOf(if (isVirtualActive) locationConfig.longitude else -74.0060)
  }
  var centerLabel by remember(locationConfig.city, locationConfig.stateCode, isVirtualActive) {
    mutableStateOf(
      if (isVirtualActive) "Virtual GPS: ${locationConfig.city}, ${locationConfig.stateCode}"
      else "New York, NY"
    )
  }
  var locationMessage by remember { mutableStateOf<String?>(null) }

  val locationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc =
          lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (loc != null) {
          centerLat = loc.latitude
          centerLon = loc.longitude
          centerLabel = "Physical Device Location"
          locationMessage = null
        } else {
          locationMessage = "Device location unavailable right now; showing $centerLabel"
        }
      } else {
        locationMessage = "Location permission declined. Use search or US city chips."
      }
    }

  val usCities = remember { VirtualLocationCatalog.presets }

  Scaffold(
    topBar = {
      VirtualAppTopBar("Maps", centerLabel, onBack = onCloseApp) {
        IconButton(
          onClick = {
            if (isVirtualActive) {
              centerLat = locationConfig.latitude
              centerLon = locationConfig.longitude
              centerLabel = "Virtual GPS: ${locationConfig.city}, ${locationConfig.stateCode}"
              locationMessage = "Centered on active Virtual GPS (${locationConfig.formattedCoordinates})"
            } else if (
              ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            ) {
              val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
              val loc =
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                  ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
              if (loc != null) {
                centerLat = loc.latitude
                centerLon = loc.longitude
                centerLabel = "Physical Device Location"
              } else {
                locationMessage = "No cached physical fix; pick a US city or enable Virtual GPS."
              }
            } else {
              locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
          }
        ) {
          Icon(Icons.Rounded.MyLocation, contentDescription = "Center on active location")
        }
      }
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search US cities, landmarks…") },
          singleLine = true,
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { onSearchPlaces(query) }, enabled = !googleState.isMapSearching) {
          if (googleState.isMapSearching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          else Text("Go")
        }
      }

      LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(usCities, key = { it.id }) { preset ->
          FilterChip(
            selected = centerLabel.contains(preset.city),
            onClick = {
              centerLat = preset.latitude
              centerLon = preset.longitude
              centerLabel = preset.displayName
            },
            label = { Text(preset.shortLabel) },
          )
        }
      }

      if (!locationMessage.isNullOrBlank()) {
        Text(
          text = locationMessage!!,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
      }

      if (googleState.mapPlaces.isNotEmpty()) {
        LazyRow(
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(googleState.mapPlaces) { place ->
            FilterChip(
              selected = false,
              onClick = {
                centerLat = place.lat
                centerLon = place.lon
                centerLabel = place.displayName.substringBefore(",")
              },
              label = { Text(place.displayName.take(26), maxLines = 1) },
            )
          }
        }
      }

      val mapUrl =
        remember(centerLat, centerLon) {
          val delta = 0.08
          val bbox = "${centerLon - delta},${centerLat - delta},${centerLon + delta},${centerLat + delta}"
          "https://www.openstreetmap.org/export/embed.html?bbox=$bbox&layer=mapnik&marker=$centerLat,$centerLon"
        }

      AndroidView(
        factory = { ctx ->
          WebView(ctx).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            tag = mapUrl
            loadUrl(mapUrl)
          }
        },
        update = { wv ->
          if (wv.tag != mapUrl) {
            wv.tag = mapUrl
            wv.loadUrl(mapUrl)
          }
        },
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )
    }
  }
}

// --- 12. PLAY DISCOVERY APP ---
@Composable
fun PlayDiscoveryApp(
  pinnedAppIds: List<String>,
  onTogglePin: (VirtualAppId) -> Unit,
  onLaunchApp: (VirtualAppId) -> Unit,
  onCloseApp: () -> Unit,
) {
  var selectedCat by remember { mutableStateOf<AppCategory?>(null) }
  var detailSpec by remember { mutableStateOf<VirtualAppSpec?>(null) }

  val apps =
    remember(selectedCat) {
      VirtualAppCatalog.apps.filter { selectedCat == null || it.category == selectedCat }
    }

  Scaffold(
    topBar = { VirtualAppTopBar("Play Discovery", "Virtual OS App Catalog", onBack = onCloseApp) }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
      Spacer(modifier = Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          FilterChip(selected = selectedCat == null, onClick = { selectedCat = null }, label = { Text("All") })
        }
        items(AppCategory.entries) { cat ->
          FilterChip(
            selected = selectedCat == cat,
            onClick = { selectedCat = if (selectedCat == cat) null else cat },
            label = { Text(cat.label) },
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(apps, key = { it.appId.id }) { spec ->
          val isPinned = pinnedAppIds.contains(spec.appId.id)
          Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().clickable { detailSpec = spec },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(spec.icon, contentDescription = spec.title, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(spec.title, style = MaterialTheme.typography.titleMedium)
                Text(spec.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("${spec.rating} • ${spec.sizeLabel}", style = MaterialTheme.typography.labelSmall)
                }
              }
              IconButton(onClick = { onTogglePin(spec.appId) }) {
                Icon(
                  Icons.Rounded.PushPin,
                  contentDescription = if (isPinned) "Unpin from Home" else "Pin to Home",
                  tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(onClick = { onLaunchApp(spec.appId) }, shape = RoundedCornerShape(16.dp)) {
                Text("Open")
              }
            }
          }
        }
      }
    }
  }

  detailSpec?.let { spec ->
    val isPinned = pinnedAppIds.contains(spec.appId.id)
    AlertDialog(
      onDismissRequest = { detailSpec = null },
      title = { Text("${spec.title} v${spec.version}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(spec.subtitle, style = MaterialTheme.typography.bodyLarge)
          Text("Category: ${spec.category.label}", style = MaterialTheme.typography.bodyMedium)
          Text("Footprint: ${spec.sizeLabel}", style = MaterialTheme.typography.bodyMedium)
          Text("Permissions: ${spec.permissionsSummary}", style = MaterialTheme.typography.bodyMedium)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            detailSpec = null
            onLaunchApp(spec.appId)
          }
        ) {
          Text("Launch App")
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { onTogglePin(spec.appId) }) {
          Text(if (isPinned) "Unpin Home" else "Pin to Home")
        }
      },
    )
  }
}
