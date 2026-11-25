package com.example.ui.apps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BrowserBookmark
import com.example.data.local.BrowserSessionStore
import com.example.data.local.PersistedBrowserTab
import com.example.data.model.BrowserVault
import com.example.data.repository.VirtualOsDataSource
import java.net.URLEncoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class BrowserUiState(
  val isRestored: Boolean = false,
  val tabs: List<PersistedBrowserTab> = BrowserSessionStore.defaultTabs(),
  val activeTabIndex: Int = 0,
  val bookmarks: List<BrowserBookmark> = BrowserSessionStore.defaultBookmarks(),
  val loadProgress: Int = 100,
  val canGoBack: Boolean = false,
  val canGoForward: Boolean = false,
  val syncedDomainsCount: Int = 0,
) {
  val activeTab: PersistedBrowserTab
    get() = tabs.getOrElse(activeTabIndex) { tabs.first() }
}

/**
 * ViewModel-scoped Chrome controller that retains live WebViews across configuration changes
 * and Home/App switches, flushes CookieManager immediately, and syncs tabs, cookies, and
 * web localStorage to both DataStore and Firebase Firestore BrowserVault.
 */
class BrowserSessionManager(
  private val appContext: Context,
  private val userId: String,
  private val repository: VirtualOsDataSource,
  private val store: BrowserSessionStore = BrowserSessionStore(appContext),
) : ViewModel() {

  private val webViews = mutableMapOf<Int, WebView>()
  private val cookiesMap = mutableMapOf<String, String>()
  private val webStorageMap = mutableMapOf<String, String>()
  private val restoredStorageOrigins = mutableSetOf<String>()

  private var persistJob: Job? = null
  private var pendingExternalUrl: String? = null

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

      // Merge cloud and local cookies/storage (local takes priority for fresher same-device edits)
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
                  // Preserve session cookies for 30 days across restarts
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
    // Always snapshot key Google origins if user visited Google/Gmail/YouTube
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

    // Restore saved localStorage tokens once per origin if not yet restored this session
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
      existingWv.loadUrl(resolved)
    }
  }

  fun openNewTab(url: String = "https://www.google.com/", title: String = "New Tab"): Int {
    val state = _uiState.value
    val nextId = (state.tabs.maxOfOrNull { it.id } ?: 0) + 1
    val newTab = PersistedBrowserTab(id = nextId, title = title, url = url)
    val updatedTabs = state.tabs + newTab
    val nextIndex = updatedTabs.lastIndex
    _uiState.value = state.copy(tabs = updatedTabs, activeTabIndex = nextIndex)
    schedulePersist()
    refreshNavFlags(nextId)
    return nextId
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
          cacheMode = WebSettings.LOAD_DEFAULT
          useWideViewPort = true
          loadWithOverviewMode = true
          builtInZoomControls = true
          displayZoomControls = false
          javaScriptCanOpenWindowsAutomatically = true
          setSupportMultipleWindows(true)
          mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
          // Clean User-Agent so Google Accounts / Gmail / OAuth sign-in never gets blocked as WebView
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

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
              if (!url.isNullOrBlank()) {
                updateTabMetadata(tab.id, url = url, title = null)
                captureCookiesForUrl(url)
              }
              refreshNavFlags(tab.id)
            }

            override fun doUpdateVisitedHistory(
              view: WebView?,
              url: String?,
              isReload: Boolean,
            ) {
              if (!url.isNullOrBlank()) {
                updateTabMetadata(tab.id, url = url, title = view?.title)
                captureCookiesForUrl(url)
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
                captureCookiesForUrl(url)
                captureAndRestoreWebStorage(view, url)
              }
              schedulePersist()
              refreshNavFlags(tab.id)
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
          }

        if (!isPopup && tab.url.isNotBlank()) {
          loadUrl(tab.url)
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

  fun persistImmediately() {
    val activeWv = webViews[_uiState.value.activeTab.id]
    captureCookiesForUrl(activeWv?.url ?: _uiState.value.activeTab.url)
    val origin = originOf(activeWv?.url ?: _uiState.value.activeTab.url)
    if (activeWv != null && origin != null) {
      snapshotLocalStorage(activeWv, origin)
    }
    if (_uiState.value.isRestored) {
      viewModelScope.launch { persistSnapshotNow() }
    }
  }

  private suspend fun persistSnapshotNow() {
    val snapshot = _uiState.value
    if (!snapshot.isRestored) return
    val tabsJson = BrowserSessionStore.serializeTabs(snapshot.tabs)
    val bookmarksJson = BrowserSessionStore.serializeBookmarks(snapshot.bookmarks)
    val cookiesJson = BrowserSessionStore.serializeStringMap(cookiesMap)
    val webStorageJson = BrowserSessionStore.serializeStringMap(webStorageMap)

    store.saveAll(
      userId = userId,
      tabs = snapshot.tabs,
      activeTabIndex = snapshot.activeTabIndex,
      bookmarks = snapshot.bookmarks,
      cookiesMap = cookiesMap,
      webStorageMap = webStorageMap,
    )

    runCatching {
      repository.saveBrowserVault(
        BrowserVault(
          userId = userId,
          tabsJson = tabsJson,
          activeTabIndex = snapshot.activeTabIndex.toLong(),
          bookmarksJson = bookmarksJson,
          cookiesJson = cookiesJson,
          webStorageJson = webStorageJson,
        )
      )
    }.onFailure { Log.w(TAG, "Cloud browser vault sync warning", it) }
  }

  fun releaseInMemoryViewsKeepingSession() {
    persistImmediately()
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
