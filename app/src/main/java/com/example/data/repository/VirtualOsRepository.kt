package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BrowserVault
import com.example.data.model.CalendarEvent
import com.example.data.model.DevicePreferences
import com.example.data.model.MessageThread
import com.example.data.model.VirtualAlarm
import com.example.data.model.VirtualContact
import com.example.data.model.VirtualFileRecord
import com.example.data.model.VirtualMessage
import com.example.data.model.VirtualNote
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VirtualOsRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) : VirtualOsDataSource {
  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun userDoc(uid: String) = db.collection("users").document(uid)

  private fun <T> Flow<T>.logFirestoreErrors(
    operationType: OperationType,
    path: String,
    uid: String,
  ): Flow<T> = catch { error ->
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path, auth)
    }
    throw error
  }

  // --- NOTES ---
  override fun observeNotes(uid: String): Flow<List<VirtualNote>> {
    val path = "users/$uid/notes"
    return userDoc(uid)
      .collection("notes")
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          VirtualNote::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  override suspend fun saveNote(note: VirtualNote): String {
    val uid = requireUserId()
    val collection = userDoc(uid).collection("notes")
    val isCreate = note.id.isBlank()
    val docRef = if (isCreate) collection.document() else collection.document(note.id)
    try {
      if (isCreate) {
        val payload =
          mapOf(
            "userId" to uid,
            "title" to note.title.take(200),
            "content" to note.content.take(10000),
            "colorKey" to note.colorKey,
            "isPinned" to note.isPinned,
            "isChecklist" to note.isChecklist,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.set(payload).await()
      } else {
        requireNotNull(note.createdAt) { "Note createdAt must not be null for update" }
        docRef
          .update(
            mapOf(
              "title" to note.title.take(200),
              "content" to note.content.take(10000),
              "colorKey" to note.colorKey,
              "isPinned" to note.isPinned,
              "isChecklist" to note.isChecklist,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  override suspend fun deleteNote(noteId: String) {
    val uid = requireUserId()
    val docRef = userDoc(uid).collection("notes").document(noteId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- CONTACTS ---
  override fun observeContacts(uid: String): Flow<List<VirtualContact>> {
    val path = "users/$uid/contacts"
    return userDoc(uid)
      .collection("contacts")
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          VirtualContact::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  override suspend fun saveContact(contact: VirtualContact): String {
    val uid = requireUserId()
    val collection = userDoc(uid).collection("contacts")
    val isCreate = contact.id.isBlank()
    val docRef = if (isCreate) collection.document() else collection.document(contact.id)
    try {
      if (isCreate) {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "name" to contact.name.take(120),
              "phone" to contact.phone.take(40),
              "email" to contact.email.take(120),
              "company" to contact.company.take(120),
              "isFavorite" to contact.isFavorite,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .update(
            mapOf(
              "name" to contact.name.take(120),
              "phone" to contact.phone.take(40),
              "email" to contact.email.take(120),
              "company" to contact.company.take(120),
              "isFavorite" to contact.isFavorite,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  override suspend fun deleteContact(contactId: String) {
    val uid = requireUserId()
    val docRef = userDoc(uid).collection("contacts").document(contactId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- MESSAGES & THREADS ---
  override fun observeThreads(uid: String): Flow<List<MessageThread>> {
    val path = "users/$uid/threads"
    return userDoc(uid)
      .collection("threads")
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          MessageThread::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  override fun observeMessages(uid: String, threadId: String): Flow<List<VirtualMessage>> {
    val path = "users/$uid/threads/$threadId/messages"
    return userDoc(uid)
      .collection("threads")
      .document(threadId)
      .collection("messages")
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          VirtualMessage::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  override suspend fun createOrGetThread(contactName: String, contactPhone: String): String {
    val uid = requireUserId()
    val collection = userDoc(uid).collection("threads")
    val docRef = collection.document()
    try {
      docRef
        .set(
          mapOf(
            "userId" to uid,
            "contactName" to contactName.take(120),
            "contactPhone" to contactPhone.take(40),
            "lastMessage" to "Conversation started",
            "unreadCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  override suspend fun sendMessage(threadId: String, sender: String, body: String, isOutgoing: Boolean): String {
    val uid = requireUserId()
    val threadRef = userDoc(uid).collection("threads").document(threadId)
    val msgRef = threadRef.collection("messages").document()
    try {
      msgRef
        .set(
          mapOf(
            "userId" to uid,
            "threadId" to threadId,
            "sender" to sender.take(120),
            "body" to body.take(2000),
            "isOutgoing" to isOutgoing,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      threadRef
        .update(
          mapOf(
            "lastMessage" to body.take(500),
            "unreadCount" to 0L,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      return msgRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgRef.path, auth)
      throw e
    }
  }

  override suspend fun deleteThread(threadId: String) {
    val uid = requireUserId()
    val docRef = userDoc(uid).collection("threads").document(threadId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- CALENDAR EVENTS ---
  override fun observeEvents(uid: String): Flow<List<CalendarEvent>> {
    val path = "users/$uid/events"
    return userDoc(uid)
      .collection("events")
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          CalendarEvent::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  override suspend fun saveEvent(event: CalendarEvent): String {
    val uid = requireUserId()
    val collection = userDoc(uid).collection("events")
    val isCreate = event.id.isBlank()
    val docRef = if (isCreate) collection.document() else collection.document(event.id)
    try {
      if (isCreate) {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "title" to event.title.take(200),
              "description" to event.description.take(2000),
              "dateEpochMillis" to event.dateEpochMillis,
              "timeLabel" to event.timeLabel.take(40),
              "location" to event.location.take(200),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .update(
            mapOf(
              "title" to event.title.take(200),
              "description" to event.description.take(2000),
              "dateEpochMillis" to event.dateEpochMillis,
              "timeLabel" to event.timeLabel.take(40),
              "location" to event.location.take(200),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  override suspend fun deleteEvent(eventId: String) {
    val uid = requireUserId()
    val docRef = userDoc(uid).collection("events").document(eventId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- CLOCK ALARMS ---
  override fun observeAlarms(uid: String): Flow<List<VirtualAlarm>> {
    val path = "users/$uid/alarms"
    return userDoc(uid)
      .collection("alarms")
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          VirtualAlarm::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  override suspend fun saveAlarm(alarm: VirtualAlarm): String {
    val uid = requireUserId()
    val collection = userDoc(uid).collection("alarms")
    val isCreate = alarm.id.isBlank()
    val docRef = if (isCreate) collection.document() else collection.document(alarm.id)
    try {
      if (isCreate) {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "hour" to alarm.hour.coerceIn(0L, 23L),
              "minute" to alarm.minute.coerceIn(0L, 59L),
              "label" to alarm.label.take(80),
              "isEnabled" to alarm.isEnabled,
              "daysMask" to alarm.daysMask.coerceIn(0L, 127L),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .update(
            mapOf(
              "hour" to alarm.hour.coerceIn(0L, 23L),
              "minute" to alarm.minute.coerceIn(0L, 59L),
              "label" to alarm.label.take(80),
              "isEnabled" to alarm.isEnabled,
              "daysMask" to alarm.daysMask.coerceIn(0L, 127L),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  override suspend fun deleteAlarm(alarmId: String) {
    val uid = requireUserId()
    val docRef = userDoc(uid).collection("alarms").document(alarmId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- FILES METADATA ---
  override fun observeFiles(uid: String): Flow<List<VirtualFileRecord>> {
    val path = "users/$uid/files"
    return userDoc(uid)
      .collection("files")
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          VirtualFileRecord::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  override suspend fun saveFileRecord(file: VirtualFileRecord): String {
    val uid = requireUserId()
    val collection = userDoc(uid).collection("files")
    val isCreate = file.id.isBlank()
    val docRef = if (isCreate) collection.document() else collection.document(file.id)
    try {
      if (isCreate) {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "name" to file.name.take(200),
              "folder" to file.folder,
              "mimeType" to file.mimeType.take(100),
              "sizeBytes" to file.sizeBytes.coerceAtLeast(0L),
              "localPath" to file.localPath.take(500),
              "contentPreview" to file.contentPreview.take(4000),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .update(
            mapOf(
              "name" to file.name.take(200),
              "folder" to file.folder,
              "mimeType" to file.mimeType.take(100),
              "sizeBytes" to file.sizeBytes.coerceAtLeast(0L),
              "localPath" to file.localPath.take(500),
              "contentPreview" to file.contentPreview.take(4000),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      return docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  override suspend fun deleteFileRecord(fileId: String) {
    val uid = requireUserId()
    val docRef = userDoc(uid).collection("files").document(fileId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- DEVICE PREFERENCES ---
  override fun observePreferences(uid: String): Flow<DevicePreferences> {
    val path = "users/$uid/preferences/$SETTINGS_DOC_ID"
    return userDoc(uid)
      .collection("preferences")
      .document(SETTINGS_DOC_ID)
      .snapshots()
      .map { snapshot ->
        snapshot.toObject(
          DevicePreferences::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        ) ?: DevicePreferences(id = SETTINGS_DOC_ID, userId = uid)
      }
      .logFirestoreErrors(OperationType.GET, path, uid)
  }

  override suspend fun savePreferences(prefs: DevicePreferences) {
    val uid = requireUserId()
    val docRef = userDoc(uid).collection("preferences").document(SETTINGS_DOC_ID)
    try {
      val mutableFields =
        mapOf(
          "themePalette" to prefs.themePalette,
          "darkMode" to prefs.darkMode,
          "countryCode" to prefs.countryCode.take(8),
          "countryName" to prefs.countryName.take(80),
          "stateRegion" to prefs.stateRegion.take(80),
          "localeTag" to prefs.localeTag.take(20),
          "languageName" to prefs.languageName.take(80),
          "usTimezoneId" to prefs.usTimezoneId.take(60),
          "dateFormat" to prefs.dateFormat,
          "use24HourTime" to prefs.use24HourTime,
          "currencyCode" to prefs.currencyCode.take(3),
          "numberFormat" to prefs.numberFormat,
          "temperatureUnit" to prefs.temperatureUnit,
          "measurementSystem" to prefs.measurementSystem,
          "oauthClientId" to prefs.oauthClientId.take(200),
          "pinnedAppIds" to prefs.pinnedAppIds.take(20),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      val existing = docRef.get().await()
      if (!existing.exists()) {
        docRef
          .set(
            mutableFields +
              mapOf(
                "userId" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
              )
          )
          .await()
      } else {
        docRef.update(mutableFields).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      throw e
    }
  }

  // --- CLOUD BROWSER SESSION VAULT ---
  override suspend fun loadBrowserVault(uid: String): BrowserVault? {
    val docRef = userDoc(uid).collection("browser_vault").document(BROWSER_VAULT_DOC_ID)
    return try {
      docRef
        .get()
        .await()
        .toObject(BrowserVault::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      null
    }
  }

  override suspend fun saveBrowserVault(vault: BrowserVault) {
    val uid = requireUserId()
    val docRef = userDoc(uid).collection("browser_vault").document(BROWSER_VAULT_DOC_ID)
    val safeIndex = vault.activeTabIndex.coerceIn(0L, 50L)
    try {
      val existing = docRef.get().await()
      if (!existing.exists()) {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "tabsJson" to vault.tabsJson.take(32000),
              "activeTabIndex" to safeIndex,
              "bookmarksJson" to vault.bookmarksJson.take(16000),
              "cookiesJson" to vault.cookiesJson.take(200000),
              "webStorageJson" to vault.webStorageJson.take(200000),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .update(
            mapOf(
              "tabsJson" to vault.tabsJson.take(32000),
              "activeTabIndex" to safeIndex,
              "bookmarksJson" to vault.bookmarksJson.take(16000),
              "cookiesJson" to vault.cookiesJson.take(200000),
              "webStorageJson" to vault.webStorageJson.take(200000),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      throw e
    }
  }

  override suspend fun clearBrowserVault() {
    val uid = requireUserId()
    val docRef = userDoc(uid).collection("browser_vault").document(BROWSER_VAULT_DOC_ID)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  override suspend fun verifyCloudHealth(): Boolean {
    val uid = requireUserId()
    val docRef = userDoc(uid).collection("preferences").document(SETTINGS_DOC_ID)
    return try {
      docRef.get().await()
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      false
    }
  }

  companion object {
    const val SETTINGS_DOC_ID = "device_settings"
    const val BROWSER_VAULT_DOC_ID = "default"
  }
}
