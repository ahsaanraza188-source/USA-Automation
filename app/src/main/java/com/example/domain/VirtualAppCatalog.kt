package com.example.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.ui.graphics.vector.ImageVector

enum class VirtualAppId(val id: String) {
  CALCULATOR("calculator"),
  CHROME("chrome"),
  SEARCH("search"),
  GMAIL("gmail"),
  YOUTUBE("youtube"),
  MAPS("maps"),
  CONTACTS("contacts"),
  MESSAGES("messages"),
  CAMERA_GALLERY("camera_gallery"),
  FILES("files"),
  CLOCK("clock"),
  CALENDAR("calendar"),
  NOTES("notes"),
  SETTINGS("settings"),
  PLAY_DISCOVERY("play_discovery"),
  DIAGNOSTICS("diagnostics"),
}

enum class AppCategory(val label: String) {
  GOOGLE("Google & Web"),
  COMMUNICATION("Communication"),
  PRODUCTIVITY("Productivity"),
  MEDIA("Camera & Media"),
  SYSTEM("System & Tools"),
}

data class VirtualAppSpec(
  val appId: VirtualAppId,
  val title: String,
  val subtitle: String,
  val category: AppCategory,
  val icon: ImageVector,
  val version: String,
  val rating: String,
  val sizeLabel: String,
  val permissionsSummary: String,
  val accentIndex: Int,
)

data class UsTimezoneOption(
  val zoneId: String,
  val shortName: String,
  val cityLabel: String,
)

object VirtualAppCatalog {
  val usTimezones =
    listOf(
      UsTimezoneOption("America/New_York", "EST / EDT", "New York (Eastern)"),
      UsTimezoneOption("America/Chicago", "CST / CDT", "Chicago (Central)"),
      UsTimezoneOption("America/Denver", "MST / MDT", "Denver (Mountain)"),
      UsTimezoneOption("America/Los_Angeles", "PST / PDT", "Los Angeles (Pacific)"),
    )

  val apps: List<VirtualAppSpec> =
    listOf(
      VirtualAppSpec(
        appId = VirtualAppId.CHROME,
        title = "Chrome",
        subtitle = "Multi-tab WebView browser with bookmarks",
        category = AppCategory.GOOGLE,
        icon = Icons.Rounded.Language,
        version = "131.0",
        rating = "4.8",
        sizeLabel = "System Core",
        permissionsSummary = "Internet, Network State",
        accentIndex = 0,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.SEARCH,
        title = "Search",
        subtitle = "Unified local & web instant search",
        category = AppCategory.GOOGLE,
        icon = Icons.Rounded.Search,
        version = "15.4",
        rating = "4.9",
        sizeLabel = "System Core",
        permissionsSummary = "Internet, Local Index",
        accentIndex = 1,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.GMAIL,
        title = "Gmail",
        subtitle = "OAuth 2.0 PKCE + Gmail REST API v1",
        category = AppCategory.GOOGLE,
        icon = Icons.Rounded.Mail,
        version = "2026.11",
        rating = "4.7",
        sizeLabel = "14.2 MB",
        permissionsSummary = "Internet, User OAuth Client ID",
        accentIndex = 2,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.YOUTUBE,
        title = "YouTube",
        subtitle = "YouTube Data API v3 search & player",
        category = AppCategory.MEDIA,
        icon = Icons.Rounded.PlayArrow,
        version = "19.44",
        rating = "4.8",
        sizeLabel = "22.8 MB",
        permissionsSummary = "Internet, User OAuth Client ID",
        accentIndex = 2,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.MAPS,
        title = "Maps",
        subtitle = "Interactive US map & Nominatim geocoding",
        category = AppCategory.GOOGLE,
        icon = Icons.Rounded.Map,
        version = "11.9",
        rating = "4.7",
        sizeLabel = "18.5 MB",
        permissionsSummary = "Internet, Optional Coarse Location",
        accentIndex = 1,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.CONTACTS,
        title = "Contacts",
        subtitle = "Cloud-synced address book & favorites",
        category = AppCategory.COMMUNICATION,
        icon = Icons.Rounded.Contacts,
        version = "4.3",
        rating = "4.6",
        sizeLabel = "6.1 MB",
        permissionsSummary = "Firestore User Sync",
        accentIndex = 0,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.MESSAGES,
        title = "Messages",
        subtitle = "Real-time cloud-synced chat threads",
        category = AppCategory.COMMUNICATION,
        icon = Icons.Rounded.Message,
        version = "2026.10",
        rating = "4.7",
        sizeLabel = "9.4 MB",
        permissionsSummary = "Firestore User Sync",
        accentIndex = 0,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.CAMERA_GALLERY,
        title = "Camera",
        subtitle = "CameraX viewfinder + Photo Picker gallery",
        category = AppCategory.MEDIA,
        icon = Icons.Rounded.CameraAlt,
        version = "9.2",
        rating = "4.8",
        sizeLabel = "11.0 MB",
        permissionsSummary = "Runtime Camera, Zero-Perm Photo Picker",
        accentIndex = 3,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.FILES,
        title = "Files",
        subtitle = "Sandboxed file vault & SAF importer",
        category = AppCategory.PRODUCTIVITY,
        icon = Icons.Rounded.Folder,
        version = "3.5",
        rating = "4.6",
        sizeLabel = "5.8 MB",
        permissionsSummary = "App Private Storage, SAF, Firestore",
        accentIndex = 1,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.CALCULATOR,
        title = "Calculator",
        subtitle = "Standard & scientific expression engine",
        category = AppCategory.PRODUCTIVITY,
        icon = Icons.Rounded.Calculate,
        version = "8.6",
        rating = "4.9",
        sizeLabel = "2.4 MB",
        permissionsSummary = "None",
        accentIndex = 3,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.CLOCK,
        title = "Clock",
        subtitle = "US world clocks, alarms, timer & stopwatch",
        category = AppCategory.PRODUCTIVITY,
        icon = Icons.Rounded.Schedule,
        version = "7.9",
        rating = "4.8",
        sizeLabel = "4.1 MB",
        permissionsSummary = "Firestore Alarms Sync",
        accentIndex = 0,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.CALENDAR,
        title = "Calendar",
        subtitle = "Schedule & agenda synced to At a Glance",
        category = AppCategory.PRODUCTIVITY,
        icon = Icons.Rounded.CalendarMonth,
        version = "2026.11",
        rating = "4.8",
        sizeLabel = "7.2 MB",
        permissionsSummary = "Firestore User Sync",
        accentIndex = 1,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.NOTES,
        title = "Notes",
        subtitle = "Color-coded notes & checklists",
        category = AppCategory.PRODUCTIVITY,
        icon = Icons.Rounded.NoteAlt,
        version = "5.4",
        rating = "4.9",
        sizeLabel = "4.9 MB",
        permissionsSummary = "Firestore User Sync",
        accentIndex = 2,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.PLAY_DISCOVERY,
        title = "Play Store",
        subtitle = "Virtual app discovery & permissions inspector",
        category = AppCategory.SYSTEM,
        icon = Icons.Rounded.Shop,
        version = "42.1",
        rating = "4.7",
        sizeLabel = "System Core",
        permissionsSummary = "Internal App Catalog",
        accentIndex = 1,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.DIAGNOSTICS,
        title = "Diagnostics",
        subtitle = "Live public IP check & 15-point self-test",
        category = AppCategory.SYSTEM,
        icon = Icons.Rounded.MonitorHeart,
        version = "2.4",
        rating = "5.0",
        sizeLabel = "System Core",
        permissionsSummary = "Internet, Network State",
        accentIndex = 3,
      ),
      VirtualAppSpec(
        appId = VirtualAppId.SETTINGS,
        title = "Settings",
        subtitle = "Material You themes, US region & OAuth ID",
        category = AppCategory.SYSTEM,
        icon = Icons.Rounded.Settings,
        version = "16.0",
        rating = "4.9",
        sizeLabel = "System Core",
        permissionsSummary = "DataStore, Firestore, Credential Manager",
        accentIndex = 0,
      ),
    )

  fun findById(id: VirtualAppId): VirtualAppSpec = apps.first { it.appId == id }
  fun findByKey(key: String): VirtualAppSpec? = apps.firstOrNull { it.appId.id == key }
}
