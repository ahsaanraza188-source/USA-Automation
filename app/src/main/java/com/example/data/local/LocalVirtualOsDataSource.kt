package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.BrowserVault
import com.example.data.model.CalendarEvent
import com.example.data.model.DevicePreferences
import com.example.data.model.MessageThread
import com.example.data.model.VirtualAlarm
import com.example.data.model.VirtualContact
import com.example.data.model.VirtualFileRecord
import com.example.data.model.VirtualMessage
import com.example.data.model.VirtualNote
import com.example.data.repository.VirtualOsDataSource
import com.google.firebase.Timestamp
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.localVirtualOsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "local_virtual_os_store"
)

class LocalVirtualOsDataSource(context: Context) : VirtualOsDataSource {
  private val appContext = context.applicationContext
  private val dataStore = appContext.localVirtualOsDataStore

  val isLocalGuestModeFlow: Flow<Boolean> =
    dataStore.data.map { prefs -> prefs[KEY_LOCAL_GUEST_MODE] ?: false }

  suspend fun setLocalGuestMode(enabled: Boolean) {
    dataStore.edit { prefs -> prefs[KEY_LOCAL_GUEST_MODE] = enabled }
  }

  // --- NOTES ---
  override fun observeNotes(uid: String): Flow<List<VirtualNote>> =
    dataStore.data.map { prefs -> parseNotes(prefs[KEY_NOTES]) }

  override suspend fun saveNote(note: VirtualNote): String {
    val now = System.currentTimeMillis()
    val id = note.id.ifBlank { UUID.randomUUID().toString() }
    dataStore.edit { prefs ->
      val list = parseNotes(prefs[KEY_NOTES]).toMutableList()
      val idx = list.indexOfFirst { it.id == id }
      val createdTs = if (idx >= 0) list[idx].createdAt else Timestamp(Date(now))
      val updated =
        note.copy(
          id = id,
          userId = LOCAL_UID,
          createdAt = createdTs ?: Timestamp(Date(now)),
          updatedAt = Timestamp(Date(now)),
        )
      if (idx >= 0) list[idx] = updated else list.add(updated)
      prefs[KEY_NOTES] = serializeNotes(list)
    }
    return id
  }

  override suspend fun deleteNote(noteId: String) {
    dataStore.edit { prefs ->
      val updated = parseNotes(prefs[KEY_NOTES]).filterNot { it.id == noteId }
      prefs[KEY_NOTES] = serializeNotes(updated)
    }
  }

  // --- CONTACTS ---
  override fun observeContacts(uid: String): Flow<List<VirtualContact>> =
    dataStore.data.map { prefs -> parseContacts(prefs[KEY_CONTACTS]) }

  override suspend fun saveContact(contact: VirtualContact): String {
    val now = System.currentTimeMillis()
    val id = contact.id.ifBlank { UUID.randomUUID().toString() }
    dataStore.edit { prefs ->
      val list = parseContacts(prefs[KEY_CONTACTS]).toMutableList()
      val idx = list.indexOfFirst { it.id == id }
      val createdTs = if (idx >= 0) list[idx].createdAt else Timestamp(Date(now))
      val updated =
        contact.copy(
          id = id,
          userId = LOCAL_UID,
          createdAt = createdTs ?: Timestamp(Date(now)),
          updatedAt = Timestamp(Date(now)),
        )
      if (idx >= 0) list[idx] = updated else list.add(updated)
      prefs[KEY_CONTACTS] = serializeContacts(list)
    }
    return id
  }

  override suspend fun deleteContact(contactId: String) {
    dataStore.edit { prefs ->
      val updated = parseContacts(prefs[KEY_CONTACTS]).filterNot { it.id == contactId }
      prefs[KEY_CONTACTS] = serializeContacts(updated)
    }
  }

  // --- THREADS & MESSAGES ---
  override fun observeThreads(uid: String): Flow<List<MessageThread>> =
    dataStore.data.map { prefs -> parseThreads(prefs[KEY_THREADS]) }

  override fun observeMessages(uid: String, threadId: String): Flow<List<VirtualMessage>> =
    dataStore.data.map { prefs -> parseMessages(prefs[KEY_MESSAGES]).filter { it.threadId == threadId } }

  override suspend fun createOrGetThread(contactName: String, contactPhone: String): String {
    val now = System.currentTimeMillis()
    val id = UUID.randomUUID().toString()
    dataStore.edit { prefs ->
      val list = parseThreads(prefs[KEY_THREADS]).toMutableList()
      val ts = Timestamp(Date(now))
      list.add(
        MessageThread(
          id = id,
          userId = LOCAL_UID,
          contactName = contactName.take(120),
          contactPhone = contactPhone.take(40),
          lastMessage = "Conversation started",
          unreadCount = 0L,
          createdAt = ts,
          updatedAt = ts,
        )
      )
      prefs[KEY_THREADS] = serializeThreads(list)
    }
    return id
  }

  override suspend fun sendMessage(
    threadId: String,
    sender: String,
    body: String,
    isOutgoing: Boolean,
  ): String {
    val now = System.currentTimeMillis()
    val msgId = UUID.randomUUID().toString()
    val ts = Timestamp(Date(now))
    dataStore.edit { prefs ->
      val msgs = parseMessages(prefs[KEY_MESSAGES]).toMutableList()
      msgs.add(
        VirtualMessage(
          id = msgId,
          userId = LOCAL_UID,
          threadId = threadId,
          sender = sender.take(120),
          body = body.take(2000),
          isOutgoing = isOutgoing,
          createdAt = ts,
          updatedAt = ts,
        )
      )
      prefs[KEY_MESSAGES] = serializeMessages(msgs)

      val threads = parseThreads(prefs[KEY_THREADS]).toMutableList()
      val tIdx = threads.indexOfFirst { it.id == threadId }
      if (tIdx >= 0) {
        threads[tIdx] =
          threads[tIdx].copy(lastMessage = body.take(500), unreadCount = 0L, updatedAt = ts)
        prefs[KEY_THREADS] = serializeThreads(threads)
      }
    }
    return msgId
  }

  override suspend fun deleteThread(threadId: String) {
    dataStore.edit { prefs ->
      prefs[KEY_THREADS] = serializeThreads(parseThreads(prefs[KEY_THREADS]).filterNot { it.id == threadId })
      prefs[KEY_MESSAGES] = serializeMessages(parseMessages(prefs[KEY_MESSAGES]).filterNot { it.threadId == threadId })
    }
  }

  // --- CALENDAR EVENTS ---
  override fun observeEvents(uid: String): Flow<List<CalendarEvent>> =
    dataStore.data.map { prefs -> parseEvents(prefs[KEY_EVENTS]) }

  override suspend fun saveEvent(event: CalendarEvent): String {
    val now = System.currentTimeMillis()
    val id = event.id.ifBlank { UUID.randomUUID().toString() }
    dataStore.edit { prefs ->
      val list = parseEvents(prefs[KEY_EVENTS]).toMutableList()
      val idx = list.indexOfFirst { it.id == id }
      val createdTs = if (idx >= 0) list[idx].createdAt else Timestamp(Date(now))
      val updated =
        event.copy(
          id = id,
          userId = LOCAL_UID,
          createdAt = createdTs ?: Timestamp(Date(now)),
          updatedAt = Timestamp(Date(now)),
        )
      if (idx >= 0) list[idx] = updated else list.add(updated)
      prefs[KEY_EVENTS] = serializeEvents(list)
    }
    return id
  }

  override suspend fun deleteEvent(eventId: String) {
    dataStore.edit { prefs ->
      prefs[KEY_EVENTS] = serializeEvents(parseEvents(prefs[KEY_EVENTS]).filterNot { it.id == eventId })
    }
  }

  // --- ALARMS ---
  override fun observeAlarms(uid: String): Flow<List<VirtualAlarm>> =
    dataStore.data.map { prefs -> parseAlarms(prefs[KEY_ALARMS]) }

  override suspend fun saveAlarm(alarm: VirtualAlarm): String {
    val now = System.currentTimeMillis()
    val id = alarm.id.ifBlank { UUID.randomUUID().toString() }
    dataStore.edit { prefs ->
      val list = parseAlarms(prefs[KEY_ALARMS]).toMutableList()
      val idx = list.indexOfFirst { it.id == id }
      val createdTs = if (idx >= 0) list[idx].createdAt else Timestamp(Date(now))
      val updated =
        alarm.copy(
          id = id,
          userId = LOCAL_UID,
          hour = alarm.hour.coerceIn(0L, 23L),
          minute = alarm.minute.coerceIn(0L, 59L),
          daysMask = alarm.daysMask.coerceIn(0L, 127L),
          createdAt = createdTs ?: Timestamp(Date(now)),
          updatedAt = Timestamp(Date(now)),
        )
      if (idx >= 0) list[idx] = updated else list.add(updated)
      prefs[KEY_ALARMS] = serializeAlarms(list)
    }
    return id
  }

  override suspend fun deleteAlarm(alarmId: String) {
    dataStore.edit { prefs ->
      prefs[KEY_ALARMS] = serializeAlarms(parseAlarms(prefs[KEY_ALARMS]).filterNot { it.id == alarmId })
    }
  }

  // --- FILES ---
  override fun observeFiles(uid: String): Flow<List<VirtualFileRecord>> =
    dataStore.data.map { prefs -> parseFiles(prefs[KEY_FILES]) }

  override suspend fun saveFileRecord(file: VirtualFileRecord): String {
    val now = System.currentTimeMillis()
    val id = file.id.ifBlank { UUID.randomUUID().toString() }
    dataStore.edit { prefs ->
      val list = parseFiles(prefs[KEY_FILES]).toMutableList()
      val idx = list.indexOfFirst { it.id == id }
      val createdTs = if (idx >= 0) list[idx].createdAt else Timestamp(Date(now))
      val updated =
        file.copy(
          id = id,
          userId = LOCAL_UID,
          createdAt = createdTs ?: Timestamp(Date(now)),
          updatedAt = Timestamp(Date(now)),
        )
      if (idx >= 0) list[idx] = updated else list.add(updated)
      prefs[KEY_FILES] = serializeFiles(list)
    }
    return id
  }

  override suspend fun deleteFileRecord(fileId: String) {
    dataStore.edit { prefs ->
      prefs[KEY_FILES] = serializeFiles(parseFiles(prefs[KEY_FILES]).filterNot { it.id == fileId })
    }
  }

  // --- PREFERENCES ---
  override fun observePreferences(uid: String): Flow<DevicePreferences> =
    dataStore.data.map { prefs -> parsePreferences(prefs[KEY_PREFERENCES]) }

  override suspend fun savePreferences(prefs: DevicePreferences) {
    dataStore.edit { storePrefs ->
      storePrefs[KEY_PREFERENCES] = serializePreferences(prefs.copy(id = "device_settings", userId = LOCAL_UID))
    }
  }

  // Local mode keeps browser sessions locally in BrowserSessionStore without Firestore calls
  override suspend fun loadBrowserVault(uid: String): BrowserVault? = null

  override suspend fun saveBrowserVault(vault: BrowserVault) = Unit

  override suspend fun clearBrowserVault() = Unit

  override suspend fun verifyCloudHealth(): Boolean = false

  // --- JSON HELPERS ---
  private fun tsMillis(ts: Timestamp?): Long = ts?.toDate()?.time ?: System.currentTimeMillis()

  private fun parseNotes(raw: String?): List<VirtualNote> =
    runCatching {
      val arr = JSONArray(raw ?: return emptyList())
      List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        VirtualNote(
          id = o.optString("id"),
          userId = LOCAL_UID,
          title = o.optString("title"),
          content = o.optString("content"),
          colorKey = o.optString("colorKey", "COBALT"),
          isPinned = o.optBoolean("isPinned", false),
          isChecklist = o.optBoolean("isChecklist", false),
          createdAt = Timestamp(Date(o.optLong("createdAt"))),
          updatedAt = Timestamp(Date(o.optLong("updatedAt"))),
        )
      }
    }.getOrDefault(emptyList())

  private fun serializeNotes(list: List<VirtualNote>): String =
    JSONArray().apply {
      list.forEach { n ->
        put(
          JSONObject().apply {
            put("id", n.id)
            put("title", n.title)
            put("content", n.content)
            put("colorKey", n.colorKey)
            put("isPinned", n.isPinned)
            put("isChecklist", n.isChecklist)
            put("createdAt", tsMillis(n.createdAt))
            put("updatedAt", tsMillis(n.updatedAt))
          }
        )
      }
    }.toString()

  private fun parseContacts(raw: String?): List<VirtualContact> =
    runCatching {
      val arr = JSONArray(raw ?: return emptyList())
      List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        VirtualContact(
          id = o.optString("id"),
          userId = LOCAL_UID,
          name = o.optString("name"),
          phone = o.optString("phone"),
          email = o.optString("email"),
          company = o.optString("company"),
          isFavorite = o.optBoolean("isFavorite", false),
          createdAt = Timestamp(Date(o.optLong("createdAt"))),
          updatedAt = Timestamp(Date(o.optLong("updatedAt"))),
        )
      }
    }.getOrDefault(emptyList())

  private fun serializeContacts(list: List<VirtualContact>): String =
    JSONArray().apply {
      list.forEach { c ->
        put(
          JSONObject().apply {
            put("id", c.id)
            put("name", c.name)
            put("phone", c.phone)
            put("email", c.email)
            put("company", c.company)
            put("isFavorite", c.isFavorite)
            put("createdAt", tsMillis(c.createdAt))
            put("updatedAt", tsMillis(c.updatedAt))
          }
        )
      }
    }.toString()

  private fun parseThreads(raw: String?): List<MessageThread> =
    runCatching {
      val arr = JSONArray(raw ?: return emptyList())
      List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        MessageThread(
          id = o.optString("id"),
          userId = LOCAL_UID,
          contactName = o.optString("contactName"),
          contactPhone = o.optString("contactPhone"),
          lastMessage = o.optString("lastMessage"),
          unreadCount = o.optLong("unreadCount", 0L),
          createdAt = Timestamp(Date(o.optLong("createdAt"))),
          updatedAt = Timestamp(Date(o.optLong("updatedAt"))),
        )
      }
    }.getOrDefault(emptyList())

  private fun serializeThreads(list: List<MessageThread>): String =
    JSONArray().apply {
      list.forEach { t ->
        put(
          JSONObject().apply {
            put("id", t.id)
            put("contactName", t.contactName)
            put("contactPhone", t.contactPhone)
            put("lastMessage", t.lastMessage)
            put("unreadCount", t.unreadCount)
            put("createdAt", tsMillis(t.createdAt))
            put("updatedAt", tsMillis(t.updatedAt))
          }
        )
      }
    }.toString()

  private fun parseMessages(raw: String?): List<VirtualMessage> =
    runCatching {
      val arr = JSONArray(raw ?: return emptyList())
      List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        VirtualMessage(
          id = o.optString("id"),
          userId = LOCAL_UID,
          threadId = o.optString("threadId"),
          sender = o.optString("sender"),
          body = o.optString("body"),
          isOutgoing = o.optBoolean("isOutgoing", true),
          createdAt = Timestamp(Date(o.optLong("createdAt"))),
          updatedAt = Timestamp(Date(o.optLong("updatedAt"))),
        )
      }
    }.getOrDefault(emptyList())

  private fun serializeMessages(list: List<VirtualMessage>): String =
    JSONArray().apply {
      list.forEach { m ->
        put(
          JSONObject().apply {
            put("id", m.id)
            put("threadId", m.threadId)
            put("sender", m.sender)
            put("body", m.body)
            put("isOutgoing", m.isOutgoing)
            put("createdAt", tsMillis(m.createdAt))
            put("updatedAt", tsMillis(m.updatedAt))
          }
        )
      }
    }.toString()

  private fun parseEvents(raw: String?): List<CalendarEvent> =
    runCatching {
      val arr = JSONArray(raw ?: return emptyList())
      List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        CalendarEvent(
          id = o.optString("id"),
          userId = LOCAL_UID,
          title = o.optString("title"),
          description = o.optString("description"),
          dateEpochMillis = o.optLong("dateEpochMillis"),
          timeLabel = o.optString("timeLabel"),
          location = o.optString("location"),
          createdAt = Timestamp(Date(o.optLong("createdAt"))),
          updatedAt = Timestamp(Date(o.optLong("updatedAt"))),
        )
      }
    }.getOrDefault(emptyList())

  private fun serializeEvents(list: List<CalendarEvent>): String =
    JSONArray().apply {
      list.forEach { e ->
        put(
          JSONObject().apply {
            put("id", e.id)
            put("title", e.title)
            put("description", e.description)
            put("dateEpochMillis", e.dateEpochMillis)
            put("timeLabel", e.timeLabel)
            put("location", e.location)
            put("createdAt", tsMillis(e.createdAt))
            put("updatedAt", tsMillis(e.updatedAt))
          }
        )
      }
    }.toString()

  private fun parseAlarms(raw: String?): List<VirtualAlarm> =
    runCatching {
      val arr = JSONArray(raw ?: return emptyList())
      List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        VirtualAlarm(
          id = o.optString("id"),
          userId = LOCAL_UID,
          hour = o.optLong("hour", 7L),
          minute = o.optLong("minute", 0L),
          label = o.optString("label"),
          isEnabled = o.optBoolean("isEnabled", true),
          daysMask = o.optLong("daysMask", 0L),
          createdAt = Timestamp(Date(o.optLong("createdAt"))),
          updatedAt = Timestamp(Date(o.optLong("updatedAt"))),
        )
      }
    }.getOrDefault(emptyList())

  private fun serializeAlarms(list: List<VirtualAlarm>): String =
    JSONArray().apply {
      list.forEach { a ->
        put(
          JSONObject().apply {
            put("id", a.id)
            put("hour", a.hour)
            put("minute", a.minute)
            put("label", a.label)
            put("isEnabled", a.isEnabled)
            put("daysMask", a.daysMask)
            put("createdAt", tsMillis(a.createdAt))
            put("updatedAt", tsMillis(a.updatedAt))
          }
        )
      }
    }.toString()

  private fun parseFiles(raw: String?): List<VirtualFileRecord> =
    runCatching {
      val arr = JSONArray(raw ?: return emptyList())
      List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        VirtualFileRecord(
          id = o.optString("id"),
          userId = LOCAL_UID,
          name = o.optString("name"),
          folder = o.optString("folder", "Documents"),
          mimeType = o.optString("mimeType", "text/plain"),
          sizeBytes = o.optLong("sizeBytes", 0L),
          localPath = o.optString("localPath"),
          contentPreview = o.optString("contentPreview"),
          createdAt = Timestamp(Date(o.optLong("createdAt"))),
          updatedAt = Timestamp(Date(o.optLong("updatedAt"))),
        )
      }
    }.getOrDefault(emptyList())

  private fun serializeFiles(list: List<VirtualFileRecord>): String =
    JSONArray().apply {
      list.forEach { f ->
        put(
          JSONObject().apply {
            put("id", f.id)
            put("name", f.name)
            put("folder", f.folder)
            put("mimeType", f.mimeType)
            put("sizeBytes", f.sizeBytes)
            put("localPath", f.localPath)
            put("contentPreview", f.contentPreview)
            put("createdAt", tsMillis(f.createdAt))
            put("updatedAt", tsMillis(f.updatedAt))
          }
        )
      }
    }.toString()

  private fun parsePreferences(raw: String?): DevicePreferences {
    if (raw.isNullOrBlank()) return DevicePreferences(id = "device_settings", userId = LOCAL_UID)
    return runCatching {
      val o = JSONObject(raw)
      val pinnedArr = o.optJSONArray("pinnedAppIds")
      val pinned =
        if (pinnedArr != null) {
          List(pinnedArr.length()) { idx -> pinnedArr.optString(idx) }.filter { it.isNotBlank() }
        } else {
          listOf("chrome", "gmail", "youtube", "diagnostics", "settings")
        }
      DevicePreferences(
        id = "device_settings",
        userId = LOCAL_UID,
        themePalette = o.optString("themePalette", "COBALT"),
        darkMode = o.optBoolean("darkMode", false),
        countryCode = o.optString("countryCode", "US").ifBlank { "US" },
        countryName = o.optString("countryName", "United States").ifBlank { "United States" },
        stateRegion = o.optString("stateRegion", "New York").ifBlank { "New York" },
        localeTag = o.optString("localeTag", "en-US").ifBlank { "en-US" },
        languageName = o.optString("languageName", "English (United States)").ifBlank { "English (United States)" },
        usTimezoneId = o.optString("usTimezoneId", "America/New_York").ifBlank { "America/New_York" },
        dateFormat = o.optString("dateFormat", "MM/DD/YYYY").ifBlank { "MM/DD/YYYY" },
        use24HourTime = o.optBoolean("use24HourTime", false),
        currencyCode = o.optString("currencyCode", "USD").ifBlank { "USD" },
        numberFormat = o.optString("numberFormat", "1,234.56").ifBlank { "1,234.56" },
        temperatureUnit = o.optString("temperatureUnit", "FAHRENHEIT").ifBlank { "FAHRENHEIT" },
        measurementSystem = o.optString("measurementSystem", "US_CUSTOMARY").ifBlank { "US_CUSTOMARY" },
        oauthClientId = o.optString("oauthClientId"),
        pinnedAppIds = pinned,
      )
    }.getOrDefault(DevicePreferences(id = "device_settings", userId = LOCAL_UID))
  }

  private fun serializePreferences(p: DevicePreferences): String =
    JSONObject().apply {
      put("themePalette", p.themePalette)
      put("darkMode", p.darkMode)
      put("countryCode", p.countryCode)
      put("countryName", p.countryName)
      put("stateRegion", p.stateRegion)
      put("localeTag", p.localeTag)
      put("languageName", p.languageName)
      put("usTimezoneId", p.usTimezoneId)
      put("dateFormat", p.dateFormat)
      put("use24HourTime", p.use24HourTime)
      put("currencyCode", p.currencyCode)
      put("numberFormat", p.numberFormat)
      put("temperatureUnit", p.temperatureUnit)
      put("measurementSystem", p.measurementSystem)
      put("oauthClientId", p.oauthClientId)
      put("pinnedAppIds", JSONArray(p.pinnedAppIds))
    }.toString()

  companion object {
    const val LOCAL_UID = "local_device_user"
    private val KEY_LOCAL_GUEST_MODE = booleanPreferencesKey("local_guest_mode")
    private val KEY_NOTES = stringPreferencesKey("notes_json")
    private val KEY_CONTACTS = stringPreferencesKey("contacts_json")
    private val KEY_THREADS = stringPreferencesKey("threads_json")
    private val KEY_MESSAGES = stringPreferencesKey("messages_json")
    private val KEY_EVENTS = stringPreferencesKey("events_json")
    private val KEY_ALARMS = stringPreferencesKey("alarms_json")
    private val KEY_FILES = stringPreferencesKey("files_json")
    private val KEY_PREFERENCES = stringPreferencesKey("preferences_json")
  }
}
