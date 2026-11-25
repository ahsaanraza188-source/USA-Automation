package com.example.data.local

import com.example.ui.apps.BrowserSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSessionStoreTest {

  @Test
  fun defaultBookmarksIncludeGmailAndGoogle() {
    val bookmarks = BrowserSessionStore.defaultBookmarks()
    assertTrue(bookmarks.any { it.title == "Gmail" && it.url.startsWith("https://mail.google.com") })
    assertTrue(bookmarks.any { it.title == "Google" && it.url.startsWith("https://www.google.com") })
  }

  @Test
  fun resolvesUrlsAndSearchQueriesCorrectly() {
    assertEquals("https://mail.google.com", BrowserSessionManager.resolveInputToUrl("mail.google.com"))
    assertEquals("https://example.org/login", BrowserSessionManager.resolveInputToUrl("https://example.org/login"))
    assertEquals(
      "https://www.google.com/search?q=us+weather+forecast",
      BrowserSessionManager.resolveInputToUrl("us weather forecast"),
    )
  }
}
