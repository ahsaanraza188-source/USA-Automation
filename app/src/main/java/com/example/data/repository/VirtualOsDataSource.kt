package com.example.data.repository

import com.example.data.model.BrowserVault
import com.example.data.model.CalendarEvent
import com.example.data.model.DevicePreferences
import com.example.data.model.MessageThread
import com.example.data.model.VirtualAlarm
import com.example.data.model.VirtualContact
import com.example.data.model.VirtualFileRecord
import com.example.data.model.VirtualMessage
import com.example.data.model.VirtualNote
import kotlinx.coroutines.flow.Flow

interface VirtualOsDataSource {
  fun observeNotes(uid: String): Flow<List<VirtualNote>>
  suspend fun saveNote(note: VirtualNote): String
  suspend fun deleteNote(noteId: String)

  fun observeContacts(uid: String): Flow<List<VirtualContact>>
  suspend fun saveContact(contact: VirtualContact): String
  suspend fun deleteContact(contactId: String)

  fun observeThreads(uid: String): Flow<List<MessageThread>>
  fun observeMessages(uid: String, threadId: String): Flow<List<VirtualMessage>>
  suspend fun createOrGetThread(contactName: String, contactPhone: String): String
  suspend fun sendMessage(threadId: String, sender: String, body: String, isOutgoing: Boolean = true): String
  suspend fun deleteThread(threadId: String)

  fun observeEvents(uid: String): Flow<List<CalendarEvent>>
  suspend fun saveEvent(event: CalendarEvent): String
  suspend fun deleteEvent(eventId: String)

  fun observeAlarms(uid: String): Flow<List<VirtualAlarm>>
  suspend fun saveAlarm(alarm: VirtualAlarm): String
  suspend fun deleteAlarm(alarmId: String)

  fun observeFiles(uid: String): Flow<List<VirtualFileRecord>>
  suspend fun saveFileRecord(file: VirtualFileRecord): String
  suspend fun deleteFileRecord(fileId: String)

  fun observePreferences(uid: String): Flow<DevicePreferences>
  suspend fun savePreferences(prefs: DevicePreferences)

  suspend fun loadBrowserVault(uid: String): BrowserVault?
  suspend fun saveBrowserVault(vault: BrowserVault)
  suspend fun clearBrowserVault()

  suspend fun verifyCloudHealth(): Boolean
}
