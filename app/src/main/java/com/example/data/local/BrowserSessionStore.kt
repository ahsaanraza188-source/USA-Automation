package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.browserSessionDataStore by preferencesDataStore(name = "virtual_os_browser_session")

data class PersistedBrowserTab(
  val id: Int,
  val title: String,
  val url: String,
  val isIncognito: Boolean = false,
)

data class BrowserBookmark(
  val title: String,
  val url: String,
)

data class PersistedBrowserSession(
  val tabs: List<PersistedBrowserTab>,
  val activeTabIndex: Int,
  val bookmarks: List<BrowserBookmark>,
  val cookiesMap: Map<String, String>,
  val webStorageMap: Map<String, String>,
  val hasSavedState: Boolean,
)

class BrowserSessionStore(private val context: Context) {

  private fun tabsKey(userId: String) = stringPreferencesKey("tabs_$userId")
  private fun activeIndexKey(userId: String) = intPreferencesKey("active_index_$userId")
  private fun bookmarksKey(userId: String) = stringPreferencesKey("bookmarks_$userId")
  private fun cookiesKey(userId: String) = stringPreferencesKey("cookies_$userId")
  private fun webStorageKey(userId: String) = stringPreferencesKey("webstorage_$userId")

  suspend fun loadSession(userId: String): PersistedBrowserSession {
    val prefs = context.browserSessionDataStore.data.first()
    val rawTabs = prefs[tabsKey(userId)]
    val parsedTabs = parseTabs(rawTabs)
    val tabs = parsedTabs.ifEmpty { defaultTabs() }
    val rawIndex = prefs[activeIndexKey(userId)] ?: 0
    val safeIndex = rawIndex.coerceIn(0, tabs.lastIndex)
    val parsedBookmarks = parseBookmarks(prefs[bookmarksKey(userId)])
    val bookmarks = parsedBookmarks.ifEmpty { defaultBookmarks() }
    val cookiesMap = parseStringMap(prefs[cookiesKey(userId)])
    val webStorageMap = parseStringMap(prefs[webStorageKey(userId)])
    val hasSaved =
      parsedTabs.isNotEmpty() || cookiesMap.isNotEmpty() || webStorageMap.isNotEmpty()
    return PersistedBrowserSession(
      tabs = tabs,
      activeTabIndex = safeIndex,
      bookmarks = bookmarks,
      cookiesMap = cookiesMap,
      webStorageMap = webStorageMap,
      hasSavedState = hasSaved,
    )
  }

  suspend fun saveAll(
    userId: String,
    tabs: List<PersistedBrowserTab>,
    activeTabIndex: Int,
    bookmarks: List<BrowserBookmark>,
    cookiesMap: Map<String, String>,
    webStorageMap: Map<String, String>,
  ) {
    context.browserSessionDataStore.edit { prefs ->
      prefs[tabsKey(userId)] = serializeTabs(tabs)
      prefs[activeIndexKey(userId)] = activeTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
      prefs[bookmarksKey(userId)] = serializeBookmarks(bookmarks)
      prefs[cookiesKey(userId)] = serializeStringMap(cookiesMap)
      prefs[webStorageKey(userId)] = serializeStringMap(webStorageMap)
    }
  }

  suspend fun saveTabs(userId: String, tabs: List<PersistedBrowserTab>, activeTabIndex: Int) {
    context.browserSessionDataStore.edit { prefs ->
      prefs[tabsKey(userId)] = serializeTabs(tabs)
      prefs[activeIndexKey(userId)] = activeTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    }
  }

  suspend fun saveBookmarks(userId: String, bookmarks: List<BrowserBookmark>) {
    context.browserSessionDataStore.edit { prefs ->
      prefs[bookmarksKey(userId)] = serializeBookmarks(bookmarks)
    }
  }

  suspend fun saveCookiesAndStorage(
    userId: String,
    cookiesMap: Map<String, String>,
    webStorageMap: Map<String, String>,
  ) {
    context.browserSessionDataStore.edit { prefs ->
      prefs[cookiesKey(userId)] = serializeStringMap(cookiesMap)
      prefs[webStorageKey(userId)] = serializeStringMap(webStorageMap)
    }
  }

  suspend fun clearAll(userId: String) {
    context.browserSessionDataStore.edit { prefs ->
      prefs.remove(tabsKey(userId))
      prefs.remove(activeIndexKey(userId))
      prefs.remove(bookmarksKey(userId))
      prefs.remove(cookiesKey(userId))
      prefs.remove(webStorageKey(userId))
    }
  }

  companion object {
    fun serializeTabs(tabs: List<PersistedBrowserTab>): String {
      val array = JSONArray()
      tabs.forEach { tab ->
        array.put(
          JSONObject().apply {
            put("id", tab.id)
            put("title", tab.title)
            put("url", tab.url)
          }
        )
      }
      return array.toString()
    }

    fun parseTabs(raw: String?): List<PersistedBrowserTab> {
      if (raw.isNullOrBlank()) return emptyList()
      return runCatching {
        val array = JSONArray(raw)
        buildList {
          for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val url = obj.optString("url").trim()
            if (url.isNotBlank()) {
              add(
                PersistedBrowserTab(
                  id = obj.optInt("id", i + 1),
                  title = obj.optString("title", "Tab").ifBlank { "Tab" },
                  url = url,
                )
              )
            }
          }
        }
      }.getOrDefault(emptyList())
    }

    fun serializeBookmarks(bookmarks: List<BrowserBookmark>): String {
      val array = JSONArray()
      bookmarks.forEach { b ->
        array.put(
          JSONObject().apply {
            put("title", b.title)
            put("url", b.url)
          }
        )
      }
      return array.toString()
    }

    fun parseBookmarks(raw: String?): List<BrowserBookmark> {
      if (raw.isNullOrBlank()) return emptyList()
      return runCatching {
        val array = JSONArray(raw)
        buildList {
          for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val title = obj.optString("title").trim()
            val url = obj.optString("url").trim()
            if (title.isNotBlank() && url.isNotBlank()) {
              add(BrowserBookmark(title = title, url = url))
            }
          }
        }
      }.getOrDefault(emptyList())
    }

    fun serializeStringMap(map: Map<String, String>): String {
      val obj = JSONObject()
      map.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) obj.put(k, v) }
      return obj.toString()
    }

    fun parseStringMap(raw: String?): Map<String, String> {
      if (raw.isNullOrBlank()) return emptyMap()
      return runCatching {
        val obj = JSONObject(raw)
        buildMap {
          val keys = obj.keys()
          while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optString(k)
            if (k.isNotBlank() && v.isNotBlank()) put(k, v)
          }
        }
      }.getOrDefault(emptyMap())
    }

    fun defaultTabs(): List<PersistedBrowserTab> =
      listOf(
        PersistedBrowserTab(
          id = 1,
          title = "Gmail",
          url = "https://mail.google.com/",
        )
      )

    fun defaultBookmarks(): List<BrowserBookmark> =
      listOf(
        BrowserBookmark("Gmail", "https://mail.google.com/"),
        BrowserBookmark("Google", "https://www.google.com/"),
        BrowserBookmark("YouTube", "https://m.youtube.com/"),
        BrowserBookmark("Wikipedia US", "https://en.wikipedia.org/wiki/United_States"),
        BrowserBookmark("Weather.gov", "https://www.weather.gov/"),
        BrowserBookmark("USA.gov", "https://www.usa.gov/"),
      )
  }
}
