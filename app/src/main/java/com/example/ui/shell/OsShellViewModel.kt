package com.example.ui.shell

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CalendarEvent
import com.example.data.model.DevicePreferences
import com.example.data.model.MessageThread
import com.example.data.model.VirtualAlarm
import com.example.data.model.VirtualContact
import com.example.data.model.VirtualFileRecord
import com.example.data.model.VirtualMessage
import com.example.data.model.VirtualNote
import com.example.data.repository.VirtualOsRepository
import com.example.domain.VirtualAppId
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

data class ShellUiState(
  val activeApp: VirtualAppId? = null,
  val recentsStack: List<VirtualAppId> = emptyList(),
  val isDrawerOpen: Boolean = false,
  val isRecentsOpen: Boolean = false,
  val isShadeOpen: Boolean = false,
  val wifiEnabled: Boolean = true,
  val bluetoothEnabled: Boolean = true,
  val dndEnabled: Boolean = false,
  val brightnessLevel: Float = 0.85f,
  val activeThreadId: String? = null,
  val pendingBrowserUrl: String? = null,
  val pendingSearchQuery: String = "",
  val statusMessage: String? = null,
)

class OsShellViewModel(
  private val repository: VirtualOsRepository,
  val currentUserId: String,
) : ViewModel() {

  private val _shellState = MutableStateFlow(ShellUiState())
  val shellState: StateFlow<ShellUiState> = _shellState.asStateFlow()

  private val _clockTick = MutableStateFlow(System.currentTimeMillis())
  val clockTick: StateFlow<Long> = _clockTick.asStateFlow()

  val preferences: StateFlow<DevicePreferences> =
    repository
      .observePreferences(currentUserId)
      .catch { error ->
        unwrapAndLog("preferences", error)
        emit(DevicePreferences(userId = currentUserId))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DevicePreferences(userId = currentUserId),
      )

  val notesState: StateFlow<UiState<List<VirtualNote>>> =
    repository
      .observeNotes(currentUserId)
      .map<List<VirtualNote>, UiState<List<VirtualNote>>> { list ->
        UiState.Success(
          list.sortedWith(
            compareByDescending<VirtualNote> { it.isPinned }.thenByDescending {
              it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)
            }
          )
        )
      }
      .catch { error -> emit(UiState.Error(unwrapAndLog("notes", error))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val contactsState: StateFlow<UiState<List<VirtualContact>>> =
    repository
      .observeContacts(currentUserId)
      .map<List<VirtualContact>, UiState<List<VirtualContact>>> { list ->
        UiState.Success(
          list.sortedWith(compareByDescending<VirtualContact> { it.isFavorite }.thenBy { it.name.lowercase() })
        )
      }
      .catch { error -> emit(UiState.Error(unwrapAndLog("contacts", error))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val threadsState: StateFlow<UiState<List<MessageThread>>> =
    repository
      .observeThreads(currentUserId)
      .map<List<MessageThread>, UiState<List<MessageThread>>> { list ->
        UiState.Success(list.sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) })
      }
      .catch { error -> emit(UiState.Error(unwrapAndLog("threads", error))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  @OptIn(ExperimentalCoroutinesApi::class)
  val activeThreadMessages: StateFlow<List<VirtualMessage>> =
    _shellState
      .map { it.activeThreadId }
      .flatMapLatest { threadId ->
        if (threadId.isNullOrBlank()) flowOf(emptyList())
        else
          repository.observeMessages(currentUserId, threadId).map { list ->
            list.sortedBy { it.createdAt ?: Timestamp(0, 0) }
          }
      }
      .catch { error ->
        unwrapAndLog("messages", error)
        emit(emptyList())
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

  val eventsState: StateFlow<UiState<List<CalendarEvent>>> =
    repository
      .observeEvents(currentUserId)
      .map<List<CalendarEvent>, UiState<List<CalendarEvent>>> { list ->
        UiState.Success(list.sortedBy { it.dateEpochMillis })
      }
      .catch { error -> emit(UiState.Error(unwrapAndLog("events", error))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val alarmsState: StateFlow<UiState<List<VirtualAlarm>>> =
    repository
      .observeAlarms(currentUserId)
      .map<List<VirtualAlarm>, UiState<List<VirtualAlarm>>> { list ->
        UiState.Success(list.sortedWith(compareBy({ it.hour }, { it.minute })))
      }
      .catch { error -> emit(UiState.Error(unwrapAndLog("alarms", error))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val filesState: StateFlow<UiState<List<VirtualFileRecord>>> =
    repository
      .observeFiles(currentUserId)
      .map<List<VirtualFileRecord>, UiState<List<VirtualFileRecord>>> { list ->
        UiState.Success(list.sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) })
      }
      .catch { error -> emit(UiState.Error(unwrapAndLog("files", error))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  init {
    viewModelScope.launch {
      while (true) {
        _clockTick.value = System.currentTimeMillis()
        delay(5_000L)
      }
    }
  }

  private fun unwrapAndLog(tag: String, error: Throwable): String {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val root = firestoreError ?: error
    Log.w(TAG, "Error observing $tag", root)
    return root.message ?: "Failed to load $tag"
  }

  // --- Shell Navigation & Window Management ---
  fun launchApp(appId: VirtualAppId) {
    val currentRecents = _shellState.value.recentsStack.filterNot { it == appId }
    _shellState.value =
      _shellState.value.copy(
        activeApp = appId,
        recentsStack = listOf(appId) + currentRecents,
        isDrawerOpen = false,
        isRecentsOpen = false,
        isShadeOpen = false,
      )
  }

  fun goHome() {
    _shellState.value =
      _shellState.value.copy(
        activeApp = null,
        isDrawerOpen = false,
        isRecentsOpen = false,
        isShadeOpen = false,
      )
  }

  fun handleVirtualBack(): Boolean {
    val state = _shellState.value
    return when {
      state.isShadeOpen -> {
        _shellState.value = state.copy(isShadeOpen = false)
        true
      }
      state.isRecentsOpen -> {
        _shellState.value = state.copy(isRecentsOpen = false)
        true
      }
      state.isDrawerOpen -> {
        _shellState.value = state.copy(isDrawerOpen = false)
        true
      }
      state.activeApp == VirtualAppId.MESSAGES && state.activeThreadId != null -> {
        _shellState.value = state.copy(activeThreadId = null)
        true
      }
      state.activeApp != null -> {
        _shellState.value = state.copy(activeApp = null)
        true
      }
      else -> false
    }
  }

  fun toggleDrawer(open: Boolean? = null) {
    val target = open ?: !_shellState.value.isDrawerOpen
    _shellState.value =
      _shellState.value.copy(isDrawerOpen = target, isRecentsOpen = false, isShadeOpen = false)
  }

  fun toggleRecents(open: Boolean? = null) {
    val target = open ?: !_shellState.value.isRecentsOpen
    _shellState.value =
      _shellState.value.copy(isRecentsOpen = target, isDrawerOpen = false, isShadeOpen = false)
  }

  fun toggleShade(open: Boolean? = null) {
    val target = open ?: !_shellState.value.isShadeOpen
    _shellState.value = _shellState.value.copy(isShadeOpen = target)
  }

  fun dismissRecentApp(appId: VirtualAppId) {
    val updated = _shellState.value.recentsStack.filterNot { it == appId }
    val nextActive = if (_shellState.value.activeApp == appId) null else _shellState.value.activeApp
    _shellState.value = _shellState.value.copy(recentsStack = updated, activeApp = nextActive)
  }

  fun clearAllRecents() {
    _shellState.value =
      _shellState.value.copy(recentsStack = emptyList(), activeApp = null, isRecentsOpen = false)
  }

  fun toggleWifi() {
    _shellState.value = _shellState.value.copy(wifiEnabled = !_shellState.value.wifiEnabled)
  }

  fun toggleBluetooth() {
    _shellState.value = _shellState.value.copy(bluetoothEnabled = !_shellState.value.bluetoothEnabled)
  }

  fun toggleDnd() {
    _shellState.value = _shellState.value.copy(dndEnabled = !_shellState.value.dndEnabled)
  }

  fun setBrightness(value: Float) {
    _shellState.value = _shellState.value.copy(brightnessLevel = value.coerceIn(0.35f, 1.0f))
  }

  fun openUrlInChrome(url: String) {
    _shellState.value = _shellState.value.copy(pendingBrowserUrl = url)
    launchApp(VirtualAppId.CHROME)
  }

  fun consumePendingBrowserUrl(): String? {
    val url = _shellState.value.pendingBrowserUrl
    if (url != null) {
      _shellState.value = _shellState.value.copy(pendingBrowserUrl = null)
    }
    return url
  }

  fun openSearchWithQuery(query: String) {
    _shellState.value = _shellState.value.copy(pendingSearchQuery = query)
    launchApp(VirtualAppId.SEARCH)
  }

  fun setActiveThread(threadId: String?) {
    _shellState.value = _shellState.value.copy(activeThreadId = threadId)
  }

  fun startMessageToContact(contact: VirtualContact) {
    viewModelScope.launch {
      runCatching { repository.createOrGetThread(contact.name, contact.phone) }
        .onSuccess { threadId ->
          _shellState.value = _shellState.value.copy(activeThreadId = threadId)
          launchApp(VirtualAppId.MESSAGES)
        }
        .onFailure { postStatus(it.message ?: "Failed to open conversation") }
    }
  }

  fun clearStatusMessage() {
    _shellState.value = _shellState.value.copy(statusMessage = null)
  }

  fun showStatus(msg: String) {
    postStatus(msg)
  }

  private fun postStatus(msg: String) {
    _shellState.value = _shellState.value.copy(statusMessage = msg)
  }

  // --- Cloud CRUD Operations ---
  fun saveNote(note: VirtualNote) {
    viewModelScope.launch {
      runCatching { repository.saveNote(note) }
        .onFailure { postStatus(it.message ?: "Failed to save note") }
    }
  }

  fun deleteNote(noteId: String) {
    viewModelScope.launch {
      runCatching { repository.deleteNote(noteId) }
        .onFailure { postStatus(it.message ?: "Failed to delete note") }
    }
  }

  fun saveContact(contact: VirtualContact) {
    viewModelScope.launch {
      runCatching { repository.saveContact(contact) }
        .onFailure { postStatus(it.message ?: "Failed to save contact") }
    }
  }

  fun deleteContact(contactId: String) {
    viewModelScope.launch {
      runCatching { repository.deleteContact(contactId) }
        .onFailure { postStatus(it.message ?: "Failed to delete contact") }
    }
  }

  fun createThread(name: String, phone: String) {
    viewModelScope.launch {
      runCatching { repository.createOrGetThread(name, phone) }
        .onSuccess { setActiveThread(it) }
        .onFailure { postStatus(it.message ?: "Failed to create thread") }
    }
  }

  fun sendMessage(threadId: String, body: String) {
    viewModelScope.launch {
      runCatching { repository.sendMessage(threadId, "You", body, isOutgoing = true) }
        .onFailure { postStatus(it.message ?: "Failed to send message") }
    }
  }

  fun deleteThread(threadId: String) {
    viewModelScope.launch {
      runCatching { repository.deleteThread(threadId) }
        .onSuccess { if (_shellState.value.activeThreadId == threadId) setActiveThread(null) }
        .onFailure { postStatus(it.message ?: "Failed to delete thread") }
    }
  }

  fun saveEvent(event: CalendarEvent) {
    viewModelScope.launch {
      runCatching { repository.saveEvent(event) }
        .onFailure { postStatus(it.message ?: "Failed to save event") }
    }
  }

  fun deleteEvent(eventId: String) {
    viewModelScope.launch {
      runCatching { repository.deleteEvent(eventId) }
        .onFailure { postStatus(it.message ?: "Failed to delete event") }
    }
  }

  fun saveAlarm(alarm: VirtualAlarm) {
    viewModelScope.launch {
      runCatching { repository.saveAlarm(alarm) }
        .onFailure { postStatus(it.message ?: "Failed to save alarm") }
    }
  }

  fun deleteAlarm(alarmId: String) {
    viewModelScope.launch {
      runCatching { repository.deleteAlarm(alarmId) }
        .onFailure { postStatus(it.message ?: "Failed to delete alarm") }
    }
  }

  fun saveFileRecord(file: VirtualFileRecord) {
    viewModelScope.launch {
      runCatching { repository.saveFileRecord(file) }
        .onFailure { postStatus(it.message ?: "Failed to save file") }
    }
  }

  fun deleteFileRecord(fileId: String) {
    viewModelScope.launch {
      runCatching { repository.deleteFileRecord(fileId) }
        .onFailure { postStatus(it.message ?: "Failed to delete file") }
    }
  }

  fun updatePreferences(transform: (DevicePreferences) -> DevicePreferences) {
    viewModelScope.launch {
      val updated = transform(preferences.value)
      runCatching { repository.savePreferences(updated) }
        .onSuccess { postStatus("Preferences synced") }
        .onFailure { postStatus(it.message ?: "Failed to sync preferences") }
    }
  }

  fun togglePinToHome(appId: VirtualAppId) {
    updatePreferences { prefs ->
      val current = prefs.pinnedAppIds
      val next =
        if (current.contains(appId.id)) current - appId.id
        else (current + appId.id).take(12)
      prefs.copy(pinnedAppIds = next)
    }
  }

  suspend fun checkCloudHealth(): Boolean = repository.verifyCloudHealth()

  fun formatUsTime(zoneIdStr: String): String {
    val tz = TimeZone.getTimeZone(zoneIdStr.ifBlank { "America/New_York" })
    return SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = tz }.format(Date(_clockTick.value))
  }

  fun formatUsDate(zoneIdStr: String): String {
    val tz = TimeZone.getTimeZone(zoneIdStr.ifBlank { "America/New_York" })
    return SimpleDateFormat("EEE, MMM d • z", Locale.US).apply { timeZone = tz }.format(Date(_clockTick.value))
  }

  private companion object {
    const val TAG = "OsShellVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
