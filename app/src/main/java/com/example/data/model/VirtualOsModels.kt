package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class VirtualNote(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val content: String = "",
  val colorKey: String = "COBALT",
  @get:PropertyName("isPinned")
  @field:PropertyName("isPinned")
  val isPinned: Boolean = false,
  @get:PropertyName("isChecklist")
  @field:PropertyName("isChecklist")
  val isChecklist: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class VirtualContact(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val phone: String = "",
  val email: String = "",
  val company: String = "",
  @get:PropertyName("isFavorite")
  @field:PropertyName("isFavorite")
  val isFavorite: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MessageThread(
  @DocumentId val id: String = "",
  val userId: String = "",
  val contactName: String = "",
  val contactPhone: String = "",
  val lastMessage: String = "",
  val unreadCount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class VirtualMessage(
  @DocumentId val id: String = "",
  val userId: String = "",
  val threadId: String = "",
  val sender: String = "",
  val body: String = "",
  @get:PropertyName("isOutgoing")
  @field:PropertyName("isOutgoing")
  val isOutgoing: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CalendarEvent(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val description: String = "",
  val dateEpochMillis: Long = 0L,
  val timeLabel: String = "",
  val location: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class VirtualAlarm(
  @DocumentId val id: String = "",
  val userId: String = "",
  val hour: Long = 7L,
  val minute: Long = 0L,
  val label: String = "",
  @get:PropertyName("isEnabled")
  @field:PropertyName("isEnabled")
  val isEnabled: Boolean = true,
  val daysMask: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class VirtualFileRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val folder: String = "Documents",
  val mimeType: String = "text/plain",
  val sizeBytes: Long = 0L,
  val localPath: String = "",
  val contentPreview: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DevicePreferences(
  @DocumentId val id: String = "",
  val userId: String = "",
  val themePalette: String = "COBALT",
  val darkMode: Boolean = false,
  val usTimezoneId: String = "America/New_York",
  val oauthClientId: String = "",
  val pinnedAppIds: List<String> = listOf("chrome", "gmail", "youtube", "diagnostics", "settings"),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class BrowserVault(
  @DocumentId val id: String = "",
  val userId: String = "",
  val tabsJson: String = "",
  val activeTabIndex: Long = 0L,
  val bookmarksJson: String = "",
  val cookiesJson: String = "",
  val webStorageJson: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
