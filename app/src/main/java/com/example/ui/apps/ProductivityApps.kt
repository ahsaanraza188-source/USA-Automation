package com.example.ui.apps

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CalendarEvent
import com.example.data.model.DevicePreferences
import com.example.data.model.MessageThread
import com.example.data.model.VirtualContact
import com.example.data.model.VirtualMessage
import com.example.data.model.VirtualNote
import com.example.domain.RegionalFormatter
import com.example.ui.shell.UiState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.noteCardTint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- 1. NOTES APP ---
@Composable
fun NotesApp(
  notesState: UiState<List<VirtualNote>>,
  darkTheme: Boolean,
  onSaveNote: (VirtualNote) -> Unit,
  onDeleteNote: (String) -> Unit,
  onCloseApp: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  var editingNote by remember { mutableStateOf<VirtualNote?>(null) }

  Scaffold(
    topBar = { VirtualAppTopBar("Notes", "Synced with Firestore", onBack = onCloseApp) },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { editingNote = VirtualNote() },
        modifier = Modifier.testTag("add_note_fab"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "New note")
      }
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search notes…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(10.dp))

      when (notesState) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> EmptyStatePanel(Icons.Rounded.NoteAlt, "Sync error", notesState.message)
        is UiState.Success -> {
          val filtered =
            notesState.data.filter {
              searchQuery.isBlank() ||
                it.title.contains(searchQuery, true) ||
                it.content.contains(searchQuery, true)
            }
          if (filtered.isEmpty()) {
            EmptyStatePanel(
              icon = Icons.Rounded.NoteAlt,
              title = "No notes yet",
              message = "Tap + to create a color-coded note or checklist synced across sessions.",
            )
          } else {
            LazyColumn(
              verticalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(bottom = 84.dp),
            ) {
              items(filtered, key = { it.id }) { note ->
                Card(
                  shape = RoundedCornerShape(20.dp),
                  colors =
                    CardDefaults.cardColors(containerColor = noteCardTint(note.colorKey, darkTheme)),
                  modifier = Modifier.fillMaxWidth().clickable { editingNote = note },
                ) {
                  Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (note.isPinned) {
                          Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                          )
                          Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (note.isChecklist) {
                          Icon(
                            Icons.Rounded.CheckBox,
                            contentDescription = "Checklist",
                            modifier = Modifier.size(16.dp),
                          )
                          Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                          text = note.title.ifBlank { "Untitled" },
                          style = MaterialTheme.typography.titleMedium,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                      IconButton(onClick = { onDeleteNote(note.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete note")
                      }
                    }
                    if (note.content.isNotBlank()) {
                      Spacer(modifier = Modifier.height(6.dp))
                      Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
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
  }

  editingNote?.let { draft ->
    NoteEditorDialog(
      initial = draft,
      onDismiss = { editingNote = null },
      onSave = {
        onSaveNote(it)
        editingNote = null
      },
    )
  }
}

@Composable
private fun NoteEditorDialog(
  initial: VirtualNote,
  onDismiss: () -> Unit,
  onSave: (VirtualNote) -> Unit,
) {
  var title by remember { mutableStateOf(initial.title) }
  var content by remember { mutableStateOf(initial.content) }
  var colorKey by remember { mutableStateOf(initial.colorKey) }
  var isPinned by remember { mutableStateOf(initial.isPinned) }
  var isChecklist by remember { mutableStateOf(initial.isChecklist) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial.id.isBlank()) "New Note" else "Edit Note") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("note_title_input"),
        )
        OutlinedTextField(
          value = content,
          onValueChange = { content = it },
          label = { Text(if (isChecklist) "Checklist items (one per line)" else "Content") },
          minLines = 3,
          maxLines = 6,
          modifier = Modifier.fillMaxWidth().testTag("note_content_input"),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(listOf("COBALT", "SAGE", "AMBER", "ROSE", "NEUTRAL")) { key ->
            FilterChip(
              selected = colorKey == key,
              onClick = { colorKey = key },
              label = { Text(key.lowercase().replaceFirstChar { it.uppercase() }) },
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Pin to top")
          Switch(checked = isPinned, onCheckedChange = { isPinned = it })
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Checklist mode")
          Switch(checked = isChecklist, onCheckedChange = { isChecklist = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            initial.copy(
              title = title.trim(),
              content = content.trim(),
              colorKey = colorKey,
              isPinned = isPinned,
              isChecklist = isChecklist,
            )
          )
        },
        enabled = title.isNotBlank() || content.isNotBlank(),
        modifier = Modifier.testTag("save_note_btn"),
      ) {
        Text("Save")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

// --- 2. CONTACTS APP ---
@Composable
fun ContactsApp(
  contactsState: UiState<List<VirtualContact>>,
  onSaveContact: (VirtualContact) -> Unit,
  onDeleteContact: (String) -> Unit,
  onMessageContact: (VirtualContact) -> Unit,
  onCloseApp: () -> Unit,
) {
  var search by remember { mutableStateOf("") }
  var editing by remember { mutableStateOf<VirtualContact?>(null) }

  Scaffold(
    topBar = { VirtualAppTopBar("Contacts", "Address Book", onBack = onCloseApp) },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { editing = VirtualContact() },
        modifier = Modifier.testTag("add_contact_fab"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add contact")
      }
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = search,
        onValueChange = { search = it },
        placeholder = { Text("Search name, phone, or company…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(10.dp))

      when (contactsState) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> EmptyStatePanel(Icons.Rounded.Contacts, "Sync error", contactsState.message)
        is UiState.Success -> {
          val filtered =
            contactsState.data.filter {
              search.isBlank() ||
                it.name.contains(search, true) ||
                it.phone.contains(search, true) ||
                it.company.contains(search, true)
            }
          if (filtered.isEmpty()) {
            EmptyStatePanel(
              icon = Icons.Rounded.Contacts,
              title = "No contacts saved",
              message = "Tap + to add a US contact and message them directly.",
            )
          } else {
            LazyColumn(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              contentPadding = PaddingValues(bottom = 84.dp),
            ) {
              items(filtered, key = { it.id }) { contact ->
                Card(
                  shape = RoundedCornerShape(18.dp),
                  modifier = Modifier.fillMaxWidth().clickable { editing = contact },
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Surface(
                      modifier = Modifier.size(44.dp),
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text(
                          text = contact.name.take(1).uppercase(),
                          style = MaterialTheme.typography.titleMedium,
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                      }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = listOf(contact.phone, contact.email).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    IconButton(onClick = { onSaveContact(contact.copy(isFavorite = !contact.isFavorite)) }) {
                      Icon(
                        imageVector = if (contact.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "Toggle favorite",
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                    IconButton(onClick = { onMessageContact(contact) }) {
                      Icon(Icons.Rounded.Message, contentDescription = "Message ${contact.name}")
                    }
                    IconButton(onClick = { onDeleteContact(contact.id) }) {
                      Icon(Icons.Rounded.Delete, contentDescription = "Delete contact")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  editing?.let { draft ->
    var name by remember { mutableStateOf(draft.name) }
    var phone by remember { mutableStateOf(draft.phone) }
    var email by remember { mutableStateOf(draft.email) }
    var company by remember { mutableStateOf(draft.company) }
    AlertDialog(
      onDismissRequest = { editing = null },
      title = { Text(if (draft.id.isBlank()) "New Contact" else "Edit Contact") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
          OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone (+1…)") }, singleLine = true)
          OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
          OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Company") }, singleLine = true)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveContact(draft.copy(name = name.trim(), phone = phone.trim(), email = email.trim(), company = company.trim()))
            editing = null
          },
          enabled = name.isNotBlank(),
        ) {
          Text("Save")
        }
      },
      dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
    )
  }
}

private data class VirtualCallRecord(
  val number: String,
  val label: String,
  val timestampLabel: String,
)

// --- 3. MESSAGES & PHONE DIALER APP ---
@Composable
fun MessagesApp(
  threadsState: UiState<List<MessageThread>>,
  activeThreadId: String?,
  activeMessages: List<VirtualMessage>,
  contactsState: UiState<List<VirtualContact>> = UiState.Success(emptyList()),
  onSelectThread: (String?) -> Unit,
  onCreateThread: (String, String) -> Unit,
  onSendMessage: (String, String) -> Unit,
  onDeleteThread: (String) -> Unit,
  onCloseApp: () -> Unit,
) {
  val context = LocalContext.current
  var showNewDialog by remember { mutableStateOf(false) }
  var selectedTab by remember { mutableIntStateOf(0) }
  var dialNumber by remember { mutableStateOf("+1") }
  val callLog = remember { mutableStateListOf<VirtualCallRecord>() }
  var activeCallBanner by remember { mutableStateOf<String?>(null) }

  if (activeThreadId != null) {
    BackHandler { onSelectThread(null) }
    val activeThread =
      (threadsState as? UiState.Success)?.data?.firstOrNull { it.id == activeThreadId }
    var draftText by remember { mutableStateOf("") }

    Scaffold(
      topBar = {
        VirtualAppTopBar(
          title = activeThread?.contactName ?: "Conversation",
          subtitle = activeThread?.contactPhone,
          onBack = { onSelectThread(null) },
        )
      }
    ) { padding ->
      Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(vertical = 12.dp),
        ) {
          items(activeMessages, key = { it.id }) { msg ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = if (msg.isOutgoing) Arrangement.End else Arrangement.Start,
            ) {
              Surface(
                shape = RoundedCornerShape(18.dp),
                color =
                  if (msg.isOutgoing) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceVariant,
              ) {
                Text(
                  text = msg.body,
                  style = MaterialTheme.typography.bodyLarge,
                  color =
                    if (msg.isOutgoing) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = draftText,
            onValueChange = { draftText = it },
            placeholder = { Text("Text message…") },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f).testTag("message_compose_input"),
          )
          Spacer(modifier = Modifier.width(8.dp))
          IconButton(
            onClick = {
              if (draftText.isNotBlank()) {
                onSendMessage(activeThreadId, draftText.trim())
                draftText = ""
              }
            },
            modifier = Modifier.size(48.dp).testTag("send_message_btn"),
          ) {
            Icon(
              Icons.AutoMirrored.Rounded.Send,
              contentDescription = "Send message",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
    return
  }

  Scaffold(
    topBar = {
      VirtualAppTopBar(
        title = "Messages & Phone",
        subtitle = if (selectedTab == 0) "Synced Threads" else "Dial Pad & System Dialer",
        onBack = onCloseApp,
      )
    },
    floatingActionButton = {
      if (selectedTab == 0) {
        FloatingActionButton(
          onClick = { showNewDialog = true },
          modifier = Modifier.testTag("new_thread_fab"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = "Start chat")
        }
      }
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      TabRow(selectedTabIndex = selectedTab) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Messages") },
          icon = { Icon(Icons.Rounded.Message, contentDescription = null) },
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Dialer & Calls") },
          icon = { Icon(Icons.Rounded.Dialpad, contentDescription = null) },
        )
      }

      if (selectedTab == 0) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
          when (threadsState) {
            UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> EmptyStatePanel(Icons.Rounded.Message, "Sync error", threadsState.message)
            is UiState.Success -> {
              if (threadsState.data.isEmpty()) {
                EmptyStatePanel(
                  icon = Icons.Rounded.Message,
                  title = "No conversations yet",
                  message = "Tap + to start a new thread or message someone from Contacts.",
                )
              } else {
                LazyColumn(
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  contentPadding = PaddingValues(top = 8.dp, bottom = 84.dp),
                ) {
                  items(threadsState.data, key = { it.id }) { thread ->
                    Card(
                      shape = RoundedCornerShape(18.dp),
                      modifier = Modifier.fillMaxWidth().clickable { onSelectThread(thread.id) },
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Surface(
                          modifier = Modifier.size(44.dp),
                          shape = CircleShape,
                          color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                          Box(contentAlignment = Alignment.Center) {
                            Text(thread.contactName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                          }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                          Text(thread.contactName, style = MaterialTheme.typography.titleMedium)
                          Text(
                            text = thread.lastMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                          )
                        }
                        IconButton(onClick = { onDeleteThread(thread.id) }) {
                          Icon(Icons.Rounded.Delete, contentDescription = "Delete thread")
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      } else {
        val contacts = (contactsState as? UiState.Success)?.data.orEmpty()
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Carrier PSTN voice calling & SMS require a mobile carrier plan. Use System Dialer to hand off to your device phone app, or log virtual calls below.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }

          if (activeCallBanner != null) {
            item {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(activeCallBanner!!, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                  TextButton(onClick = { activeCallBanner = null }) { Text("End") }
                }
              }
            }
          }

          if (contacts.isNotEmpty()) {
            item {
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(contacts.take(6), key = { it.id }) { contact ->
                  FilterChip(
                    selected = dialNumber == contact.phone,
                    onClick = { if (contact.phone.isNotBlank()) dialNumber = contact.phone },
                    label = { Text(contact.name) },
                  )
                }
              }
            }
          }

          item {
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = dialNumber.ifBlank { "Enter number" },
                  style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFamily),
                  modifier = Modifier.weight(1f),
                  maxLines = 1,
                )
                IconButton(
                  onClick = { if (dialNumber.isNotEmpty()) dialNumber = dialNumber.dropLast(1) }
                ) {
                  Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Backspace digit")
                }
              }
            }
          }

          item {
            val dialRows = listOf(
              listOf("1", "2", "3"),
              listOf("4", "5", "6"),
              listOf("7", "8", "9"),
              listOf("*", "0", "#"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              dialRows.forEach { row ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  row.forEach { digit ->
                    Surface(
                      shape = RoundedCornerShape(18.dp),
                      color = MaterialTheme.colorScheme.surfaceContainerHigh,
                      modifier =
                        Modifier.weight(1f)
                          .height(52.dp)
                          .clip(RoundedCornerShape(18.dp))
                          .clickable { dialNumber += digit },
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text(digit, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                      }
                    }
                  }
                }
              }
            }
          }

          item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = {
                  val target = dialNumber.trim()
                  if (target.isNotBlank()) {
                    val matchedName =
                      contacts.firstOrNull { it.phone == target }?.name ?: "External Number"
                    val timeLabel = SimpleDateFormat("h:mm a", Locale.US).format(Date())
                    callLog.add(0, VirtualCallRecord(target, matchedName, timeLabel))
                    activeCallBanner = "Virtual Call Log • $matchedName ($target)"
                  }
                },
                enabled = dialNumber.isNotBlank(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Rounded.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Log Call")
              }
              OutlinedButton(
                onClick = {
                  val target = dialNumber.trim()
                  if (target.isNotBlank()) {
                    runCatching {
                      context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target")))
                    }
                  }
                },
                enabled = dialNumber.isNotBlank(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("System Dialer")
              }
            }
          }

          if (callLog.isNotEmpty()) {
            item { Text("Session Call Log", style = MaterialTheme.typography.titleSmall) }
            items(callLog) { entry ->
              Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text(entry.label, style = MaterialTheme.typography.titleSmall)
                    Text(entry.number, style = MaterialTheme.typography.bodySmall)
                  }
                  Text(entry.timestampLabel, style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }
    }
  }

  if (showNewDialog) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showNewDialog = false },
      title = { Text("New Conversation") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Recipient Name") }, singleLine = true)
          OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone (+1-555-0100)") }, singleLine = true)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onCreateThread(name.trim(), phone.trim())
            showNewDialog = false
          },
          enabled = name.isNotBlank(),
        ) {
          Text("Start")
        }
      },
      dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("Cancel") } },
    )
  }
}

// --- 4. CALENDAR APP ---
@Composable
fun CalendarApp(
  eventsState: UiState<List<CalendarEvent>>,
  preferences: DevicePreferences = DevicePreferences(),
  onSaveEvent: (CalendarEvent) -> Unit,
  onDeleteEvent: (String) -> Unit,
  onCloseApp: () -> Unit,
) {
  var editing by remember { mutableStateOf<CalendarEvent?>(null) }

  Scaffold(
    topBar = {
      VirtualAppTopBar(
        title = "Calendar",
        subtitle = "${preferences.usTimezoneId} (${RegionalFormatter.shortTimezoneCode(preferences.usTimezoneId)})",
        onBack = onCloseApp,
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          editing = CalendarEvent(dateEpochMillis = System.currentTimeMillis(), timeLabel = "10:00 AM")
        },
        modifier = Modifier.testTag("add_event_fab"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add event")
      }
    },
  ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
      when (eventsState) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> EmptyStatePanel(Icons.Rounded.CalendarMonth, "Sync error", eventsState.message)
        is UiState.Success -> {
          if (eventsState.data.isEmpty()) {
            EmptyStatePanel(
              icon = Icons.Rounded.CalendarMonth,
              title = "Your US agenda is clear",
              message = "Tap + to schedule an event. The next event also appears on your Home At-a-Glance widget.",
            )
          } else {
            LazyColumn(
              verticalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp),
            ) {
              items(eventsState.data, key = { it.id }) { event ->
                Card(
                  shape = RoundedCornerShape(18.dp),
                  modifier = Modifier.fillMaxWidth().clickable { editing = event },
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Surface(
                      shape = RoundedCornerShape(14.dp),
                      color = MaterialTheme.colorScheme.primaryContainer,
                      modifier = Modifier.padding(end = 12.dp),
                    ) {
                      Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                      ) {
                        Text(
                          text = RegionalFormatter.formatDate(event.dateEpochMillis, preferences),
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                          text = event.timeLabel.ifBlank { "All day" },
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                      }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                      Text(event.title, style = MaterialTheme.typography.titleMedium)
                      if (event.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(
                            Icons.Rounded.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                          )
                          Spacer(modifier = Modifier.width(4.dp))
                          Text(event.location, style = MaterialTheme.typography.bodyMedium)
                        }
                      }
                      if (event.description.isNotBlank()) {
                        Text(
                          event.description,
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                    IconButton(onClick = { onDeleteEvent(event.id) }) {
                      Icon(Icons.Rounded.Delete, contentDescription = "Delete event")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  editing?.let { draft ->
    var title by remember { mutableStateOf(draft.title) }
    var timeLabel by remember { mutableStateOf(draft.timeLabel) }
    var location by remember { mutableStateOf(draft.location) }
    var desc by remember { mutableStateOf(draft.description) }
    AlertDialog(
      onDismissRequest = { editing = null },
      title = { Text(if (draft.id.isBlank()) "New Event" else "Edit Event") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Event Title") }, singleLine = true)
          OutlinedTextField(value = timeLabel, onValueChange = { timeLabel = it }, label = { Text("Time (e.g. 2:30 PM EST)") }, singleLine = true)
          OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, singleLine = true)
          OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, maxLines = 3)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveEvent(
              draft.copy(
                title = title.trim(),
                timeLabel = timeLabel.trim(),
                location = location.trim(),
                description = desc.trim(),
              )
            )
            editing = null
          },
          enabled = title.isNotBlank(),
        ) {
          Text("Save")
        }
      },
      dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
    )
  }
}
