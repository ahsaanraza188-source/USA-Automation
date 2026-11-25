package com.example.ui.apps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BrowserBookmark
import com.example.data.local.BrowserSessionStore
import com.example.data.local.PersistedBrowserTab
import com.example.data.model.BrowserVault
import com.example.data.network.AppDiagnosticsLogger
import com.example.data.network.ProxyTunnelController
import com.example.data.repository.VirtualOsDataSource
import com.example.domain.BrowserGeoPermissionMode
import com.example.domain.VirtualLocationConfig
import com.example.domain.VirtualLocationMode
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class BrowserHistoryEntry(
  val title: String,
  val url: String,
  val visitedAtMillis: Long = System.currentTimeMillis(),
)

data class PendingGeoPrompt(
  val origin: String,
  val callback: GeolocationPermissions.Callback,
)

data class BrowserUiState(
  val isRestored: Boolean = false,
  val tabs: List<PersistedBrowserTab> = BrowserSessionStore.defaultTabs(),
  val activeTabIndex: Int = 0,
  val bookmarks: List<BrowserBookmark> = BrowserSessionStore.defaultBookmarks(),
  val history: List<BrowserHistoryEntry> = emptyList(),
  val loadProgress: Int = 100,
  val canGoBack: Boolean = false,
  val canGoForward: Boolean = false,
  val syncedDomainsCount: Int = 0,
  val pendingGeoPromptOrigin: String? = null,
  val lastGeoStatusMessage: String? = null,
) {
  val activeTab: PersistedBrowserTab
    get() = tabs.getOrElse(activeTabIndex) { tabs.first() }
}

/**
 * ViewModel-scoped Chrome controller that retains live WebViews across configuration changes
 * and Home/App switches, routes through ProxyTunnelController when active, enforces Virtual
 * GPS & Geolocation permission policies, and syncs tabs, cookies, and web localStorage.
 */
class BrowserSessionManager(
  private val appContext: Context,
  private val userId: String,
  private val repository: VirtualOsDataSource,
  private val proxyTunnelController: ProxyTunnelController? = null,
  private val store: BrowserSessionStore = BrowserSessionStore(appContext),
) : ViewModel() {

  private val webViews = mutableMapOf<Int, WebView>()
  private val cookiesMap = mutableMapOf<String, String>()
  private val webStorageMap = mutableMapOf<String, String>()
  private val restoredStorageOrigins = mutableSetOf<String>()
  private val siteGeoPermissions = mutableMapOf<String, Boolean>()

  private var persistJob: Job? = null
  private var pendingExternalUrl: String? = null
  private var pendingGeoPrompt: PendingGeoPrompt? = null

  @Volatile private var locationConfig: VirtualLocationConfig = VirtualLocationConfig()

  private val _uiState = MutableStateFlow(BrowserUiState())
  val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

  init {
    configureGlobalCookies()
    viewModelScope.launch {
      val local = store.loadSession(userId)
      val cloud = runCatching { repository.loadBrowserVault(userId) }.getOrNull()

      val cloudTabs = BrowserSessionStore.parseTabs(cloud?.tabsJson)
      val cloudBookmarks = BrowserSessionStore.parseBookmarks(cloud?.bookmarksJson)
      val cloudCookies = BrowserSessionStore.parseStringMap(cloud?.cookiesJson)
      val cloudStorage = BrowserSessionStore.parseStringMap(cloud?.webStorageJson)

      val useCloudTabs = !local.hasSavedState && cloudTabs.isNotEmpty()
      val finalTabs = if (useCloudTabs) cloudTabs else local.tabs
      val finalIndex =
        (if (useCloudTabs) (cloud?.activeTabIndex?.toInt() ?: 0) else local.activeTabIndex)
          .coerceIn(0, (finalTabs.size - 1).coerceAtLeast(0))
      val finalBookmarks =
        when {
          useCloudTabs && cloudBookmarks.isNotEmpty() -> cloudBookmarks
          local.bookmarks.isNotEmpty() -> local.bookmarks
          cloudBookmarks.isNotEmpty() -> cloudBookmarks
          else -> BrowserSessionStore.defaultBookmarks()
        }

      cookiesMap.clear()
      cookiesMap.putAll(cloudCookies)
      cookiesMap.putAll(local.cookiesMap)

      webStorageMap.clear()
      webStorageMap.putAll(cloudStorage)
      webStorageMap.putAll(local.webStorageMap)

      restoreCookiesIntoManager(cookiesMap)

      store.saveAll(
        userId = userId,
        tabs = finalTabs,
        activeTabIndex = finalIndex,
        bookmarks = finalBookmarks,
        cookiesMap = cookiesMap,
        webStorageMap = webStorageMap,
      )

      _uiState.value =
        _uiState.value.copy(
          isRestored = true,
          tabs = finalTabs,
          activeTabIndex = finalIndex,
          bookmarks = finalBookmarks,
          syncedDomainsCount = (cookiesMap.keys + webStorageMap.keys).distinct().size,
        )

      pendingExternalUrl?.let { pending ->
        pendingExternalUrl = null
        openOrNavigate(pending)
      }
    }
  }

  fun updateVirtualLocationConfig(config: VirtualLocationConfig) {
    val changed = locationConfig != config
    locationConfig = config
    if (changed) {
      siteGeoPermissions.clear()
      webViews.values.forEach { wv ->
        injectGeolocationAndPrivacyShim(wv, wv.url)
      }
    }
  }

  fun respondToPendingGeoPrompt(allow: Boolean) {
    val prompt = pendingGeoPrompt ?: return
    pendingGeoPrompt = null
    val originKey = originOf(prompt.origin) ?: prompt.origin
    siteGeoPermissions[originKey] = allow
    runCatching { prompt.callback.invoke(prompt.origin, allow, false) }
    val activeWv = webViews[_uiState.value.activeTab.id]
    if (activeWv != null) {
      injectGeolocationAndPrivacyShim(activeWv, activeWv.url)
    }
    _uiState.value =
      _uiState.value.copy(
        pendingGeoPromptOrigin = null,
        lastGeoStatusMessage =
          if (allow) "Granted Virtual GPS (${locationConfig.formattedCoordinates}) to $originKey"
          else "Blocked geolocation for $originKey",
      )
  }

  private fun isOriginAllowedForVirtualGeo(urlOrOrigin: String?): Boolean {
    val cfg = locationConfig
    if (!cfg.enabled || cfg.mode == VirtualLocationMode.DISABLED) return false
    if (cfg.mode == VirtualLocationMode.PHYSICAL_DEVICE) return false
    return when (cfg.browserPermission) {
      BrowserGeoPermissionMode.ALLOW -> true
      BrowserGeoPermissionMode.BLOCK -> false
      BrowserGeoPermissionMode.ASK -> {
        val originKey = originOf(urlOrOrigin) ?: return false
        siteGeoPermissions[originKey] == true
      }
    }
  }

  private fun buildGeoAndPrivacyScript(originUrl: String?): String {
    val cfg = locationConfig
    val sb = StringBuilder()

    // 1. Virtual Geolocation shim when Virtual GPS is enabled
    if (cfg.enabled && cfg.mode == VirtualLocationMode.VIRTUAL_GPS) {
      val allowed = isOriginAllowedForVirtualGeo(originUrl)
      val blocked = cfg.browserPermission == BrowserGeoPermissionMode.BLOCK
      if (allowed) {
        sb.append(
          """
          (function() {
            try {
              var lat = ${cfg.latitude};
              var lon = ${cfg.longitude};
              var acc = ${cfg.accuracyMeters};
              function buildPos() {
                return {
                  coords: {
                    latitude: lat,
                    longitude: lon,
                    accuracy: acc,
                    altitude: null,
                    altitudeAccuracy: null,
                    heading: null,
                    speed: null
                  },
                  timestamp: Date.now()
                };
              }
              var geoImpl = {
                getCurrentPosition: function(success, error, opts) {
                  if (typeof success === 'function') {
                    setTimeout(function() { success(buildPos()); }, 15);
                  }
                },
                watchPosition: function(success, error, opts) {
                  if (typeof success === 'function') {
                    setTimeout(function() { success(buildPos()); }, 15);
                  }
                  return Math.floor(Math.random() * 10000) + 1;
                },
                clearWatch: function(id) {}
              };
              try {
                Object.defineProperty(navigator, 'geolocation', {
                  configurable: true,
                  value: geoImpl
                });
              } catch (e) {
                navigator.geolocation.getCurrentPosition = geoImpl.getCurrentPosition;
                navigator.geolocation.watchPosition = geoImpl.watchPosition;
                navigator.geolocation.clearWatch = geoImpl.clearWatch;
              }
            } catch (err) {}
          })();
          """.trimIndent()
        )
      } else if (blocked) {
        sb.append(
          """
          (function() {
            try {
              var deniedGeo = {
                getCurrentPosition: function(success, error) {
                  if (typeof error === 'function') {
                    error({ code: 1, message: 'Geolocation blocked by Virtual Location Manager', PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 });
                  }
                },
                watchPosition: function(success, error) {
                  if (typeof error === 'function') {
                    error({ code: 1, message: 'Geolocation blocked by Virtual Location Manager', PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 });
                  }
                  return 0;
                },
                clearWatch: function() {}
              };
              try {
                Object.defineProperty(navigator, 'geolocation', { configurable: true, value: deniedGeo });
              } catch (e) {
                navigator.geolocation.getCurrentPosition = deniedGeo.getCurrentPosition;
              }
            } catch (err) {}
          })();
          """.trimIndent()
        )
      }
    } else if (!cfg.enabled || cfg.mode == VirtualLocationMode.DISABLED) {
      sb.append(
        """
        (function() {
          try {
            var disabledGeo = {
              getCurrentPosition: function(success, error) {
                if (typeof error === 'function') {
                  error({ code: 2, message: 'Virtual GPS disabled', PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 });
                }
              },
              watchPosition: function(success, error) {
                if (typeof error === 'function') {
                  error({ code: 2, message: 'Virtual GPS disabled', PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 });
                }
                return 0;
              },
              clearWatch: function() {}
            };
            try {
              Object.defineProperty(navigator, 'geolocation', { configurable: true, value: disabledGeo });
            } catch (e) {}
          } catch (err) {}
        })();
        """.trimIndent()
      )
    }

    // 2. WebRTC LAN/Host IP leak guard when enabled
    if (cfg.webrtcLeakProtection) {
      sb.append(
        """
        (function() {
          try {
            if (!window.__usaWebRtcGuarded && window.RTCPeerConnection) {
              window.__usaWebRtcGuarded = true;
              var OrigRTC = window.RTCPeerConnection;
              window.RTCPeerConnection = function(config, constraints) {
                var safeConfig = Object.assign({}, config || {}, { iceServers: [] });
                return new OrigRTC(safeConfig, constraints);
              };
              window.RTCPeerConnection.prototype = OrigRTC.prototype;
            }
          } catch (err) {}
        })();
        """.trimIndent()
      )
    }

    return sb.toString()
  }

  private fun injectGeolocationAndPrivacyShim(view: WebView?, url: String?) {
    if (view == null) return
    val script = buildGeoAndPrivacyScript(url)
    if (script.isNotBlank()) {
      view.evaluateJavascript(script, null)
    }
  }

  /**
   * Executes a live geolocation verification test inside an actual Android WebView
   * using `navigator.geolocation.getCurrentPosition(...)` and returns the real result.
   */
  @SuppressLint("SetJavaScriptEnabled")
  suspend fun runGeolocationVerificationProbe(): String =
    withContext(Dispatchers.Main) {
      val cfg = locationConfig
      if (!cfg.enabled || cfg.mode == VirtualLocationMode.DISABLED) {
        return@withContext "FAIL: Virtual GPS is disabled. Enable it in Virtual GPS tab."
      }
      if (cfg.browserPermission == BrowserGeoPermissionMode.BLOCK) {
        return@withContext "FAIL: Browser Geolocation Permission is set to Block All."
      }
      val testOrigin = "https://geo-verify.localhost/"
      val probeWv =
        WebView(appContext).apply {
          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
        }
      try {
        val loaded =
          suspendCancellableCoroutine { cont ->
            probeWv.webViewClient =
              object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                  if (cont.isActive) cont.resume(true)
                }
              }
            probeWv.loadDataWithBaseURL(
              testOrigin,
              "<html><body><p>USA Geo Probe</p></body></html>",
              "text/html",
              "UTF-8",
              null,
            )
          }
        if (!loaded) return@withContext "FAIL: WebView probe failed to initialize"

        val allowOverride =
          cfg.mode == VirtualLocationMode.VIRTUAL_GPS &&
            (cfg.browserPermission == BrowserGeoPermissionMode.ALLOW ||
              cfg.browserPermission == BrowserGeoPermissionMode.ASK)
        if (allowOverride) {
          siteGeoPermissions["https://geo-verify.localhost"] = true
        }
        injectGeolocationAndPrivacyShim(probeWv, testOrigin)

        val triggerScript =
          """
          (function() {
            window.__usaGeoProbe = "PENDING";
            try {
              navigator.geolocation.getCurrentPosition(
                function(pos) {
                  window.__usaGeoProbe = "PASS:" + pos.coords.latitude.toFixed(4) + "," + pos.coords.longitude.toFixed(4) + ":" + Math.round(pos.coords.accuracy);
                },
                function(err) {
                  window.__usaGeoProbe = "FAIL:" + (err && err.message ? err.message : ("Code " + err.code));
                },
                { enableHighAccuracy: true, timeout: 3000 }
              );
            } catch (e) {
              window.__usaGeoProbe = "FAIL:" + e.message;
            }
          })();
          """.trimIndent()
        probeWv.evaluateJavascript(triggerScript, null)
        delay(220L)

        val raw =
          suspendCancellableCoroutine<String> { cont ->
            probeWv.evaluateJavascript("window.__usaGeoProbe") { res ->
              if (cont.isActive) cont.resume(res ?: "")
            }
          }
        val clean = runCatching { JSONObject("{\"v\":$raw}").optString("v") }.getOrDefault(raw.trim('"'))
        when {
          clean.startsWith("PASS:") -> {
            val parts = clean.removePrefix("PASS:").split(":")
            val coords = parts.getOrNull(0).orEmpty()
            val acc = parts.getOrNull(1) ?: "18"
            "PASS: Browser returned $coords (±${acc}m)"
          }
          clean.startsWith("FAIL:") -> clean
          else -> "FAIL: Unexpected geolocation probe state ($clean)"
        }
      } catch (e: Exception) {
        "FAIL: ${e.message ?: "Probe exception"}"
      } finally {
        runCatching {
          probeWv.stopLoading()
          probeWv.destroy()
        }
      }
    }

  private fun configureGlobalCookies() {
    runCatching {
      CookieManager.getInstance().apply {
        setAcceptCookie(true)
        flush()
      }
    }
  }

  private fun restoreCookiesIntoManager(map: Map<String, String>) {
    if (map.isEmpty()) return
    runCatching {
      val cm = CookieManager.getInstance()
      cm.setAcceptCookie(true)
      map.forEach { (url, header) ->
        if (url.startsWith("http") && header.isNotBlank()) {
          val isHttps = url.startsWith("https://")
          val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
          header.split(";").forEach { rawPair ->
            val pair = rawPair.trim()
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
              val name = pair.substring(0, eqIdx).trim()
              val value = pair.substring(eqIdx + 1).trim()
              if (name.isNotBlank()) {
                val cookieLine = buildString {
                  append(name).append('=').append(value)
                  append("; Path=/")
                  if (isHttps) append("; Secure")
                  if (!name.startsWith("__Host-") &&
                    (host == "google.com" || host.endsWith(".google.com"))
                  ) {
                    append("; Domain=.google.com")
                  }
                  append("; Max-Age=2592000")
                }
                cm.setCookie(url, cookieLine)
              }
            }
          }
        }
      }
      cm.flush()
    }.onFailure { Log.w(TAG, "Cookie restore warning", it) }
  }

  fun flushCookies() {
    runCatching { CookieManager.getInstance().flush() }
  }

  private fun captureCookiesForUrl(url: String?) {
    val cm = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
    val urlsToCapture = mutableSetOf<String>()
    if (!url.isNullOrBlank() && url.startsWith("http")) {
      urlsToCapture.add(url)
      originOf(url)?.let { urlsToCapture.add("$it/") }
    }
    _uiState.value.tabs.forEach { tab ->
      originOf(tab.url)?.let { urlsToCapture.add("$it/") }
    }
    val touchesGoogle =
      urlsToCapture.any { it.contains("google.com", ignoreCase = true) || it.contains("youtube.com", ignoreCase = true) }
    if (touchesGoogle) {
      urlsToCapture.addAll(GOOGLE_AUTH_ORIGINS)
    }

    var changed = false
    urlsToCapture.forEach { targetUrl ->
      val cookieHeader = runCatching { cm.getCookie(targetUrl) }.getOrNull()
      if (!cookieHeader.isNullOrBlank() && cookiesMap[targetUrl] != cookieHeader) {
        cookiesMap[targetUrl] = cookieHeader
        changed = true
      }
    }
    cm.flush()
    if (changed) {
      _uiState.value =
        _uiState.value.copy(
          syncedDomainsCount = (cookiesMap.keys + webStorageMap.keys).distinct().size
        )
    }
  }

  private fun captureAndRestoreWebStorage(view: WebView?, url: String?) {
    if (view == null || url.isNullOrBlank() || !url.startsWith("http")) return
    val origin = originOf(url) ?: return

    val savedJson = webStorageMap[origin]
    if (!savedJson.isNullOrBlank() && !restoredStorageOrigins.contains(origin)) {
      restoredStorageOrigins.add(origin)
      val quotedJson = JSONObject.quote(savedJson)
      val restoreScript =
        """
        (function() {
          try {
            var saved = JSON.parse($quotedJson);
            var injected = 0;
            for (var k in saved) {
              if (Object.prototype.hasOwnProperty.call(saved, k)) {
                if (window.localStorage.getItem(k) === null) {
                  window.localStorage.setItem(k, saved[k]);
                  injected++;
                }
              }
            }
            return injected;
          } catch (e) { return 0; }
        })();
        """.trimIndent()
      view.evaluateJavascript(restoreScript) { result ->
        val injected = result?.trim()?.toIntOrNull() ?: 0
        if (injected > 0) {
          view.reload()
        } else {
          snapshotLocalStorage(view, origin)
        }
      }
    } else {
      snapshotLocalStorage(view, origin)
    }
  }

  private fun snapshotLocalStorage(view: WebView, origin: String) {
    val readScript =
      """
      (function() {
        try {
          var out = {};
          for (var i = 0; i < window.localStorage.length; i++) {
            var k = window.localStorage.key(i);
            if (k !== null) out[k] = window.localStorage.getItem(k);
          }
          return JSON.stringify(out);
        } catch (e) { return ""; }
      })();
      """.trimIndent()
    view.evaluateJavascript(readScript) { raw ->
      if (raw.isNullOrBlank() || raw == "null" || raw == "\"\"") return@evaluateJavascript
      val decoded = runCatching { JSONObject("{\"v\":$raw}").optString("v") }.getOrNull().orEmpty()
      if (decoded.isNotBlank() && decoded != "{}" && decoded.length <= 40000) {
        if (webStorageMap[origin] != decoded) {
          webStorageMap[origin] = decoded
          _uiState.value =
            _uiState.value.copy(
              syncedDomainsCount = (cookiesMap.keys + webStorageMap.keys).distinct().size
            )
          schedulePersist()
        }
      }
    }
  }

  fun openOrNavigate(rawInput: String) {
    val resolved = resolveInputToUrl(rawInput)
    val state = _uiState.value
    if (!state.isRestored) {
      pendingExternalUrl = resolved
      return
    }
    val active = state.activeTab
    val updatedTabs =
      state.tabs.mapIndexed { idx, tab ->
        if (idx == state.activeTabIndex) tab.copy(url = resolved) else tab
      }
    _uiState.value = state.copy(tabs = updatedTabs)
    schedulePersist()
    val existingWv = webViews[active.id]
    if (existingWv != null) {
      existingWv.loadUrl(resolved, mapOf("Accept-Language" to "en-US,en;q=0.9"))
    }
  }

  fun openNewTab(
    url: String = "https://www.google.com/",
    title: String = "New Tab",
    isIncognito: Boolean = false,
  ): Int {
    val state = _uiState.value
    val nextId = (state.tabs.maxOfOrNull { it.id } ?: 0) + 1
    val resolvedTitle = if (isIncognito && title == "New Tab") "Private Tab" else title
    val newTab = PersistedBrowserTab(id = nextId, title = resolvedTitle, url = url, isIncognito = isIncognito)
    val updatedTabs = state.tabs + newTab
    val nextIndex = updatedTabs.lastIndex
    _uiState.value = state.copy(tabs = updatedTabs, activeTabIndex = nextIndex)
    if (!isIncognito) schedulePersist()
    refreshNavFlags(nextId)
    return nextId
  }

  fun clearHistory() {
    _uiState.value = _uiState.value.copy(history = emptyList())
  }

  fun selectTab(index: Int) {
    val state = _uiState.value
    if (index !in state.tabs.indices) return
    _uiState.value = state.copy(activeTabIndex = index, loadProgress = 100)
    schedulePersist()
    refreshNavFlags(state.tabs[index].id)
  }

  fun closeTab(index: Int) {
    val state = _uiState.value
    if (state.tabs.size <= 1 || index !in state.tabs.indices) return
    val removed = state.tabs[index]
    captureCookiesForUrl(removed.url)
    webViews.remove(removed.id)?.let { wv ->
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.stopLoading()
      wv.destroy()
    }
    val updatedTabs = state.tabs.toMutableList().apply { removeAt(index) }
    val nextIndex =
      when {
        state.activeTabIndex > index -> state.activeTabIndex - 1
        state.activeTabIndex >= updatedTabs.size -> updatedTabs.lastIndex
        else -> state.activeTabIndex
      }
    _uiState.value = state.copy(tabs = updatedTabs, activeTabIndex = nextIndex, loadProgress = 100)
    schedulePersist()
    refreshNavFlags(updatedTabs[nextIndex].id)
  }

  fun toggleBookmarkForCurrentPage(): Boolean {
    val active = _uiState.value.activeTab
    val url = active.url.trim()
    if (url.isBlank()) return false
    val current = _uiState.value.bookmarks
    val exists = current.any { it.url.equals(url, ignoreCase = true) }
    val updated =
      if (exists) current.filterNot { it.url.equals(url, ignoreCase = true) }
      else listOf(BrowserBookmark(title = active.title.take(18).ifBlank { "Saved" }, url = url)) + current
    _uiState.value = _uiState.value.copy(bookmarks = updated)
    schedulePersist()
    return !exists
  }

  fun goBackInActiveTab(): Boolean {
    val wv = webViews[_uiState.value.activeTab.id] ?: return false
    return if (wv.canGoBack()) {
      wv.goBack()
      refreshNavFlags(_uiState.value.activeTab.id)
      true
    } else {
      false
    }
  }

  fun goForwardInActiveTab() {
    val wv = webViews[_uiState.value.activeTab.id] ?: return
    if (wv.canGoForward()) {
      wv.goForward()
      refreshNavFlags(_uiState.value.activeTab.id)
    }
  }

  fun reloadActiveTab() {
    webViews[_uiState.value.activeTab.id]?.reload()
  }

  fun clearBrowsingDataExplicitly() {
    runCatching {
      CookieManager.getInstance().removeAllCookies(null)
      CookieManager.getInstance().flush()
      WebStorage.getInstance().deleteAllData()
    }
    cookiesMap.clear()
    webStorageMap.clear()
    restoredStorageOrigins.clear()
    siteGeoPermissions.clear()
    webViews.values.forEach { wv ->
      wv.clearCache(true)
      wv.clearHistory()
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.destroy()
    }
    webViews.clear()
    val freshTabs = BrowserSessionStore.defaultTabs()
    _uiState.value =
      _uiState.value.copy(
        tabs = freshTabs,
        activeTabIndex = 0,
        loadProgress = 100,
        syncedDomainsCount = 0,
      )
    viewModelScope.launch {
      store.clearAll(userId)
      runCatching { repository.clearBrowserVault() }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  fun getOrCreateWebView(context: Context, tab: PersistedBrowserTab, isPopup: Boolean = false): WebView {
    val existing = webViews[tab.id]
    if (existing != null) {
      (existing.parent as? ViewGroup)?.removeView(existing)
      existing.onResume()
      refreshNavFlags(tab.id)
      return existing
    }

    val wv =
      WebView(appContext).apply {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isVerticalScrollBarEnabled = true
        overScrollMode = View.OVER_SCROLL_ALWAYS

        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          @Suppress("DEPRECATION")
          databaseEnabled = true
          setGeolocationEnabled(true)
          cacheMode = WebSettings.LOAD_DEFAULT
          useWideViewPort = true
          loadWithOverviewMode = true
          builtInZoomControls = true
          displayZoomControls = false
          javaScriptCanOpenWindowsAutomatically = true
          setSupportMultipleWindows(true)
          mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
          val defaultUa = WebSettings.getDefaultUserAgent(appContext)
          userAgentString =
            defaultUa
              .replace("; wv)", ")")
              .replace("; wv;", ";")
              .replace("Version/4.0 ", "")
        }

        val webView = this
        CookieManager.getInstance().apply {
          setAcceptCookie(true)
          setAcceptThirdPartyCookies(webView, true)
        }

        webViewClient =
          object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
              view: WebView?,
              request: WebResourceRequest?,
            ): Boolean {
              val url = request?.url?.toString().orEmpty()
              return !(url.startsWith("http://") ||
                url.startsWith("https://") ||
                url.startsWith("about:") ||
                url.startsWith("blob:") ||
                url.startsWith("data:"))
            }

            override fun onReceivedHttpAuthRequest(
              view: WebView?,
              handler: HttpAuthHandler?,
              host: String?,
              realm: String?,
            ) {
              val creds = proxyTunnelController?.getActiveCredentials()
              if (creds != null && proxyTunnelController.isProxyRoutingActive()) {
                handler?.proceed(creds.username, creds.password)
              } else {
                handler?.cancel()
              }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
              if (!url.isNullOrBlank()) {
                updateTabMetadata(tab.id, url = url, title = null)
                if (!tab.isIncognito) captureCookiesForUrl(url)
              }
              injectGeolocationAndPrivacyShim(view, url)
              refreshNavFlags(tab.id)
            }

            override fun doUpdateVisitedHistory(
              view: WebView?,
              url: String?,
              isReload: Boolean,
            ) {
              if (!url.isNullOrBlank()) {
                updateTabMetadata(tab.id, url = url, title = view?.title)
                if (!tab.isIncognito) {
                  captureCookiesForUrl(url)
                  recordHistoryEntry(url, view?.title)
                }
              }
              refreshNavFlags(tab.id)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
              if (!url.isNullOrBlank()) {
                updateTabMetadata(
                  tabId = tab.id,
                  url = url,
                  title = view?.title?.takeIf { it.isNotBlank() },
                )
                if (!tab.isIncognito) {
                  captureCookiesForUrl(url)
                  captureAndRestoreWebStorage(view, url)
                  recordHistoryEntry(url, view?.title)
                }
              }
              injectGeolocationAndPrivacyShim(view, url)
              if (!tab.isIncognito) schedulePersist()
              refreshNavFlags(tab.id)
            }

            override fun onReceivedError(
              view: WebView?,
              request: WebResourceRequest?,
              error: WebResourceError?,
            ) {
              if (request?.isForMainFrame == true) {
                AppDiagnosticsLogger.warn(
                  component = "Chrome WebView",
                  message = "Page load error for ${request.url}",
                  rootCause = "Code ${error?.errorCode}: ${error?.description}",
                  recoveryHint = "Check VPN/Proxy status or run DNS/HTTPS checks in Location & Network Center.",
                )
              }
            }
          }

        webChromeClient =
          object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
              if (_uiState.value.activeTab.id == tab.id) {
                _uiState.value = _uiState.value.copy(loadProgress = newProgress)
              }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
              if (!title.isNullOrBlank()) {
                updateTabMetadata(tabId = tab.id, url = null, title = title)
              }
            }

            override fun onCreateWindow(
              view: WebView?,
              isDialog: Boolean,
              isUserGesture: Boolean,
              resultMsg: Message?,
            ): Boolean {
              val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
              val popupTabId = openNewTab(url = "about:blank", title = "Sign in")
              val popupTab = _uiState.value.tabs.firstOrNull { it.id == popupTabId } ?: return false
              val popupWebView = getOrCreateWebView(context, popupTab, isPopup = true)
              transport.webView = popupWebView
              resultMsg.sendToTarget()
              return true
            }

            override fun onCloseWindow(window: WebView?) {
              captureCookiesForUrl(window?.url)
              val idx =
                _uiState.value.tabs.indexOfFirst { tabEntry -> webViews[tabEntry.id] === window }
              if (idx >= 0 && _uiState.value.tabs.size > 1) {
                closeTab(idx)
              }
              persistImmediately()
            }

            override fun onGeolocationPermissionsShowPrompt(
              origin: String?,
              callback: GeolocationPermissions.Callback?,
            ) {
              if (origin == null || callback == null) return
              val cfg = locationConfig
              val originKey = originOf(origin) ?: origin

              if (!cfg.enabled || cfg.mode == VirtualLocationMode.DISABLED || cfg.browserPermission == BrowserGeoPermissionMode.BLOCK) {
                callback.invoke(origin, false, false)
                return
              }

              if (cfg.mode == VirtualLocationMode.PHYSICAL_DEVICE) {
                val hasPerm =
                  ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                  ) == PackageManager.PERMISSION_GRANTED
                callback.invoke(origin, hasPerm, false)
                return
              }

              // Virtual GPS mode
              when (cfg.browserPermission) {
                BrowserGeoPermissionMode.ALLOW -> {
                  siteGeoPermissions[originKey] = true
                  callback.invoke(origin, true, false)
                }
                BrowserGeoPermissionMode.ASK -> {
                  val remembered = siteGeoPermissions[originKey]
                  if (remembered != null) {
                    callback.invoke(origin, remembered, false)
                  } else {
                    pendingGeoPrompt?.let { prev ->
                      runCatching { prev.callback.invoke(prev.origin, false, false) }
                    }
                    pendingGeoPrompt = PendingGeoPrompt(origin, callback)
                    _uiState.value = _uiState.value.copy(pendingGeoPromptOrigin = originKey)
                  }
                }
                BrowserGeoPermissionMode.BLOCK -> callback.invoke(origin, false, false)
              }
            }
          }

        if (!isPopup && tab.url.isNotBlank()) {
          loadUrl(tab.url, mapOf("Accept-Language" to "en-US,en;q=0.9"))
        }
      }

    webViews[tab.id] = wv
    return wv
  }

  private fun updateTabMetadata(tabId: Int, url: String?, title: String?) {
    val state = _uiState.value
    var changed = false
    val updatedTabs =
      state.tabs.map { tab ->
        if (tab.id != tabId) tab
        else {
          val nextUrl = url?.takeIf { it.isNotBlank() && it != "about:blank" } ?: tab.url
          val nextTitle = title?.takeIf { it.isNotBlank() } ?: tab.title
          if (nextUrl != tab.url || nextTitle != tab.title) {
            changed = true
            tab.copy(url = nextUrl, title = nextTitle)
          } else {
            tab
          }
        }
      }
    if (changed) {
      _uiState.value = state.copy(tabs = updatedTabs)
      schedulePersist()
    }
  }

  private fun refreshNavFlags(tabId: Int) {
    if (_uiState.value.activeTab.id != tabId) return
    val wv = webViews[tabId]
    _uiState.value =
      _uiState.value.copy(
        canGoBack = wv?.canGoBack() == true,
        canGoForward = wv?.canGoForward() == true,
      )
  }

  private fun schedulePersist() {
    if (!_uiState.value.isRestored) return
    persistJob?.cancel()
    persistJob =
      viewModelScope.launch {
        delay(400L)
        persistSnapshotNow()
      }
  }

  private fun recordHistoryEntry(url: String, title: String?) {
    if (!url.startsWith("http")) return
    val current = _uiState.value.history
    val latest = current.firstOrNull()
    val cleanTitle = title?.takeIf { it.isNotBlank() } ?: originOf(url) ?: url
    if (latest != null && latest.url == url) {
      if (latest.title != cleanTitle) {
        _uiState.value =
          _uiState.value.copy(
            history = listOf(latest.copy(title = cleanTitle)) + current.drop(1)
          )
      }
      return
    }
    _uiState.value =
      _uiState.value.copy(
        history = (listOf(BrowserHistoryEntry(title = cleanTitle, url = url)) + current).take(60)
      )
  }

  fun persistImmediately() {
    val activeTab = _uiState.value.activeTab
    val activeWv = webViews[activeTab.id]
    if (!activeTab.isIncognito) {
      captureCookiesForUrl(activeWv?.url ?: activeTab.url)
      val origin = originOf(activeWv?.url ?: activeTab.url)
      if (activeWv != null && origin != null) {
        snapshotLocalStorage(activeWv, origin)
      }
    }
    if (_uiState.value.isRestored) {
      viewModelScope.launch { persistSnapshotNow() }
    }
  }

  private suspend fun persistSnapshotNow() {
    val snapshot = _uiState.value
    if (!snapshot.isRestored) return
    val persistableTabs =
      snapshot.tabs.filterNot { it.isIncognito }.ifEmpty { BrowserSessionStore.defaultTabs() }
    val safeIndex = snapshot.activeTabIndex.coerceIn(0, persistableTabs.lastIndex)
    val tabsJson = BrowserSessionStore.serializeTabs(persistableTabs)
    val bookmarksJson = BrowserSessionStore.serializeBookmarks(snapshot.bookmarks)
    val cookiesJson = BrowserSessionStore.serializeStringMap(cookiesMap)
    val webStorageJson = BrowserSessionStore.serializeStringMap(webStorageMap)

    store.saveAll(
      userId = userId,
      tabs = persistableTabs,
      activeTabIndex = safeIndex,
      bookmarks = snapshot.bookmarks,
      cookiesMap = cookiesMap,
      webStorageMap = webStorageMap,
    )

    runCatching {
      repository.saveBrowserVault(
        BrowserVault(
          userId = userId,
          tabsJson = tabsJson,
          activeTabIndex = safeIndex.toLong(),
          bookmarksJson = bookmarksJson,
          cookiesJson = cookiesJson,
          webStorageJson = webStorageJson,
        )
      )
    }.onFailure { Log.w(TAG, "Cloud browser vault sync warning", it) }
  }

  fun releaseInMemoryViewsKeepingSession() {
    persistImmediately()
    pendingGeoPrompt = null
    webViews.values.forEach { wv ->
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.stopLoading()
      wv.destroy()
    }
    webViews.clear()
  }

  override fun onCleared() {
    releaseInMemoryViewsKeepingSession()
    super.onCleared()
  }

  companion object {
    private const val TAG = "BrowserSessionMgr"
    private val GOOGLE_AUTH_ORIGINS =
      listOf(
        "https://accounts.google.com/",
        "https://mail.google.com/",
        "https://www.google.com/",
        "https://myaccount.google.com/",
        "https://m.youtube.com/",
        "https://www.youtube.com/",
        "https://aistudio.google.com/",
      )

    fun originOf(url: String?): String? {
      if (url.isNullOrBlank()) return null
      return runCatching {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (uri.port > 0) "$scheme://$host:${uri.port}" else "$scheme://$host"
      }.getOrNull()
    }

    fun resolveInputToUrl(raw: String): String {
      val trimmed = raw.trim()
      return when {
        trimmed.isBlank() -> "https://mail.google.com/"
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
      }
    }
  }
}
