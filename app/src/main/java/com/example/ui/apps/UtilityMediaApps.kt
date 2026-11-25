package com.example.ui.apps

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.data.model.VirtualAlarm
import com.example.data.model.VirtualFileRecord
import com.example.domain.CalculatorEngine
import com.example.domain.VirtualAppCatalog
import com.example.ui.shell.UiState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MonoCodeStyle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

// --- 5. CALCULATOR APP ---
@Composable
fun CalculatorApp(onCloseApp: () -> Unit) {
  var expression by remember { mutableStateOf("") }
  var resultPreview by remember { mutableStateOf("0") }
  var scientificMode by remember { mutableStateOf(false) }
  var useDegrees by remember { mutableStateOf(true) }
  val history = remember { mutableStateListOf<String>() }

  LaunchedEffect(expression, useDegrees) {
    if (expression.isBlank()) {
      resultPreview = "0"
    } else {
      CalculatorEngine.evaluate(expression, useDegrees)
        .onSuccess { resultPreview = it }
    }
  }

  Scaffold(
    topBar = {
      VirtualAppTopBar(
        title = "Calculator",
        subtitle = if (scientificMode) "Scientific (${if (useDegrees) "DEG" else "RAD"})" else "Standard",
        onBack = onCloseApp,
      ) {
        IconButton(onClick = { scientificMode = !scientificMode }) {
          Icon(Icons.Rounded.Science, contentDescription = "Toggle scientific buttons")
        }
      }
    }
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        if (history.isNotEmpty()) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history.take(5)) { entry ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
              ) {
                Text(
                  text = entry,
                  style = MonoCodeStyle,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
        }

        Surface(
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.End,
          ) {
            Text(
              text = expression.ifBlank { "0" },
              style =
                MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFamily),
              textAlign = TextAlign.End,
              maxLines = 2,
              modifier = Modifier.fillMaxWidth().testTag("calc_expression"),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "= $resultPreview",
              style = MaterialTheme.typography.displaySmall.copy(fontFamily = JetBrainsMonoFamily),
              color = MaterialTheme.colorScheme.primary,
              textAlign = TextAlign.End,
              maxLines = 1,
              modifier = Modifier.fillMaxWidth().testTag("calc_result"),
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (scientificMode) {
          val sciRows =
            listOf(
              listOf("sin(", "cos(", "tan(", if (useDegrees) "DEG" else "RAD"),
              listOf("sqrt(", "log(", "ln(", "^"),
              listOf("π", "e", "(", ")"),
            )
          sciRows.forEach { row ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              row.forEach { token ->
                OutlinedButton(
                  onClick = {
                    if (token == "DEG" || token == "RAD") useDegrees = !useDegrees
                    else expression += token
                  },
                  modifier = Modifier.weight(1f).height(44.dp),
                  shape = RoundedCornerShape(14.dp),
                  contentPadding = PaddingValues(0.dp),
                ) {
                  Text(token, style = MonoCodeStyle)
                }
              }
            }
          }
        }

        val rows =
          listOf(
            listOf("AC", "( )", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "DEL", "="),
          )
        rows.forEach { row ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            row.forEach { label ->
              val isEquals = label == "="
              val isAction = label in listOf("AC", "( )", "%", "÷", "×", "−", "+", "DEL")
              Surface(
                shape = RoundedCornerShape(20.dp),
                color =
                  when {
                    isEquals -> MaterialTheme.colorScheme.primary
                    isAction -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                  },
                modifier =
                  Modifier.weight(1f)
                    .height(58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                      when (label) {
                        "AC" -> {
                          expression = ""
                          resultPreview = "0"
                        }
                        "DEL" -> if (expression.isNotEmpty()) expression = expression.dropLast(1)
                        "( )" -> {
                          val openCount = expression.count { it == '(' }
                          val closeCount = expression.count { it == ')' }
                          expression += if (openCount > closeCount) ")" else "("
                        }
                        "=" -> {
                          CalculatorEngine.evaluate(expression, useDegrees)
                            .onSuccess { evaluated ->
                              history.add(0, "$expression = $evaluated")
                              expression = evaluated
                              resultPreview = evaluated
                            }
                            .onFailure { resultPreview = "Error" }
                        }
                        else -> expression += label
                      }
                    }
                    .testTag("calc_btn_$label"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  if (label == "DEL") {
                    Icon(
                      Icons.AutoMirrored.Rounded.Backspace,
                      contentDescription = "Backspace",
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  } else {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                      color =
                        when {
                          isEquals -> MaterialTheme.colorScheme.onPrimary
                          isAction -> MaterialTheme.colorScheme.onPrimaryContainer
                          else -> MaterialTheme.colorScheme.onSurface
                        },
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

// --- 6. CLOCK APP ---
@Composable
fun ClockApp(
  alarmsState: UiState<List<VirtualAlarm>>,
  usTimezoneId: String,
  onSaveAlarm: (VirtualAlarm) -> Unit,
  onDeleteAlarm: (String) -> Unit,
  onCloseApp: () -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var showAddAlarm by remember { mutableStateOf(false) }

  // Timer state
  var timerRemainingSec by remember { mutableLongStateOf(300L) }
  var timerRunning by remember { mutableStateOf(false) }

  // Stopwatch state
  var swElapsedMs by remember { mutableLongStateOf(0L) }
  var swRunning by remember { mutableStateOf(false) }
  val laps = remember { mutableStateListOf<Long>() }

  LaunchedEffect(Unit) {
    while (true) {
      nowMillis = System.currentTimeMillis()
      delay(1000L)
    }
  }
  LaunchedEffect(timerRunning) {
    while (timerRunning && timerRemainingSec > 0) {
      delay(1000L)
      timerRemainingSec -= 1L
      if (timerRemainingSec == 0L) timerRunning = false
    }
  }
  LaunchedEffect(swRunning) {
    while (swRunning) {
      delay(100L)
      swElapsedMs += 100L
    }
  }

  Scaffold(
    topBar = { VirtualAppTopBar("Clock", "US World Time & Alarms", onBack = onCloseApp) },
    floatingActionButton = {
      if (selectedTab == 1) {
        FloatingActionButton(onClick = { showAddAlarm = true }) {
          Icon(Icons.Rounded.Add, contentDescription = "Add alarm")
        }
      }
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      TabRow(selectedTabIndex = selectedTab) {
        listOf("World", "Alarms", "Timer", "Stopwatch").forEachIndexed { idx, title ->
          Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(title) })
        }
      }

      when (selectedTab) {
        0 -> {
          LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(VirtualAppCatalog.usTimezones) { tz ->
              val dateFormatted = remember(nowMillis, tz.zoneId) {
                val timeZone = TimeZone.getTimeZone(tz.zoneId)
                val dateFmt = SimpleDateFormat("EEE, MMM d • z", Locale.US).apply { this.timeZone = timeZone }
                val timeFmt = SimpleDateFormat("h:mm:ss a", Locale.US).apply { this.timeZone = timeZone }
                val nowDate = Date(nowMillis)
                dateFmt.format(nowDate) to timeFmt.format(nowDate)
              }
              val isActive = tz.zoneId == usTimezoneId
              Card(
                shape = RoundedCornerShape(20.dp),
                colors =
                  CardDefaults.cardColors(
                    containerColor =
                      if (isActive) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerLow
                  ),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(18.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text(tz.cityLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                      text = dateFormatted.first,
                      style = MaterialTheme.typography.bodyMedium,
                    )
                  }
                  Text(
                    text = dateFormatted.second,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                  )
                }
              }
            }
          }
        }
        1 -> {
          when (alarmsState) {
            UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Loading alarms…") }
            is UiState.Error -> EmptyStatePanel(Icons.Rounded.Alarm, "Error", alarmsState.message)
            is UiState.Success -> {
              if (alarmsState.data.isEmpty()) {
                EmptyStatePanel(Icons.Rounded.Alarm, "No alarms set", "Tap + to add a cloud-synced alarm.")
              } else {
                LazyColumn(
                  contentPadding = PaddingValues(16.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  items(alarmsState.data, key = { it.id }) { alarm ->
                    val timeStr = String.format("%02d:%02d", alarm.hour, alarm.minute)
                    Card(shape = RoundedCornerShape(20.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Column {
                          Text(
                            text = timeStr,
                            style =
                              MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = JetBrainsMonoFamily
                              ),
                          )
                          Text(
                            text = alarm.label.ifBlank { "Alarm" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                          )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Switch(
                            checked = alarm.isEnabled,
                            onCheckedChange = { onSaveAlarm(alarm.copy(isEnabled = it)) },
                          )
                          IconButton(onClick = { onDeleteAlarm(alarm.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete alarm")
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
        2 -> {
          val mins = timerRemainingSec / 60
          val secs = timerRemainingSec % 60
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(
              text = String.format("%02d:%02d", mins, secs),
              style = MaterialTheme.typography.displayLarge.copy(fontFamily = JetBrainsMonoFamily),
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(60L to "1m", 300L to "5m", 600L to "10m").forEach { (sec, label) ->
                FilterChip(
                  selected = false,
                  onClick = {
                    timerRunning = false
                    timerRemainingSec = sec
                  },
                  label = { Text(label) },
                )
              }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Button(onClick = { timerRunning = !timerRunning }) {
                Icon(if (timerRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (timerRunning) "Pause" else "Start")
              }
              OutlinedButton(
                onClick = {
                  timerRunning = false
                  timerRemainingSec = 300L
                }
              ) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset")
              }
            }
          }
        }
        3 -> {
          val totalSec = swElapsedMs / 1000
          val tenths = (swElapsedMs % 1000) / 100
          Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
              text = String.format("%02d:%02d.%d", totalSec / 60, totalSec % 60, tenths),
              style = MaterialTheme.typography.displayLarge.copy(fontFamily = JetBrainsMonoFamily),
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(onClick = { swRunning = !swRunning }) {
                Text(if (swRunning) "Stop" else "Start")
              }
              OutlinedButton(
                onClick = { if (swRunning) laps.add(0, swElapsedMs) },
                enabled = swRunning,
              ) {
                Text("Lap")
              }
              OutlinedButton(
                onClick = {
                  swRunning = false
                  swElapsedMs = 0L
                  laps.clear()
                }
              ) {
                Text("Reset")
              }
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              items(laps.size) { index ->
                val ms = laps[index]
                val s = ms / 1000
                Text(
                  text = "Lap ${laps.size - index}: ${String.format("%02d:%02d.%d", s / 60, s % 60, (ms % 1000) / 100)}",
                  style = MonoCodeStyle,
                )
              }
            }
          }
        }
      }
    }
  }

  if (showAddAlarm) {
    var hourText by remember { mutableStateOf("07") }
    var minuteText by remember { mutableStateOf("30") }
    var label by remember { mutableStateOf("Morning Check") }
    AlertDialog(
      onDismissRequest = { showAddAlarm = false },
      title = { Text("New Alarm") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = hourText,
              onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
              label = { Text("Hour (0-23)") },
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = minuteText,
              onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
              label = { Text("Min (0-59)") },
              modifier = Modifier.weight(1f),
            )
          }
          OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") })
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveAlarm(
              VirtualAlarm(
                hour = (hourText.toLongOrNull() ?: 7L).coerceIn(0, 23),
                minute = (minuteText.toLongOrNull() ?: 0L).coerceIn(0, 59),
                label = label.trim(),
                isEnabled = true,
              )
            )
            showAddAlarm = false
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = { TextButton(onClick = { showAddAlarm = false }) { Text("Cancel") } },
    )
  }
}

// --- 7. FILES APP ---
@Composable
fun FilesApp(
  filesState: UiState<List<VirtualFileRecord>>,
  onSaveFile: (VirtualFileRecord) -> Unit,
  onDeleteFile: (String) -> Unit,
  onCloseApp: () -> Unit,
) {
  val context = LocalContext.current
  var selectedFolder by remember { mutableStateOf("All") }
  var showCreateDialog by remember { mutableStateOf(false) }
  var previewFile by remember { mutableStateOf<VirtualFileRecord?>(null) }

  val safLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      uri?.let {
        val name = it.lastPathSegment?.substringAfterLast('/') ?: "imported_file"
        val preview =
          runCatching {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText().take(800) }
          }.getOrNull().orEmpty()
        onSaveFile(
          VirtualFileRecord(
            name = name,
            folder = "Downloads",
            mimeType = context.contentResolver.getType(it) ?: "application/octet-stream",
            sizeBytes = preview.length.toLong().coerceAtLeast(128L),
            localPath = it.toString(),
            contentPreview = preview.ifBlank { "Imported via Android Storage Access Framework" },
          )
        )
      }
    }

  Scaffold(
    topBar = {
      VirtualAppTopBar("Files", "Private Vault & SAF", onBack = onCloseApp) {
        IconButton(onClick = { safLauncher.launch(arrayOf("*/*")) }) {
          Icon(Icons.Rounded.FolderOpen, contentDescription = "Import file")
        }
      }
    },
    floatingActionButton = {
      FloatingActionButton(onClick = { showCreateDialog = true }) {
        Icon(Icons.Rounded.Add, contentDescription = "New text file")
      }
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
      Spacer(modifier = Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("All", "Documents", "Downloads", "Pictures", "System")) { folder ->
          FilterChip(
            selected = selectedFolder == folder,
            onClick = { selectedFolder = folder },
            label = { Text(folder) },
          )
        }
      }
      Spacer(modifier = Modifier.height(8.dp))

      when (filesState) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Loading files…") }
        is UiState.Error -> EmptyStatePanel(Icons.Rounded.Description, "Sync error", filesState.message)
        is UiState.Success -> {
          val filtered =
            filesState.data.filter { selectedFolder == "All" || it.folder == selectedFolder }
          if (filtered.isEmpty()) {
            EmptyStatePanel(
              icon = Icons.Rounded.Description,
              title = "No files in $selectedFolder",
              message = "Tap + to create a local document or tap the folder icon to import via SAF.",
            )
          } else {
            LazyColumn(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              contentPadding = PaddingValues(bottom = 84.dp),
            ) {
              items(filtered, key = { it.id }) { file ->
                Card(
                  shape = RoundedCornerShape(16.dp),
                  modifier = Modifier.fillMaxWidth().clickable { previewFile = file },
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text("${file.folder} • ${file.sizeBytes} B", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { onDeleteFile(file.id) }) {
                      Icon(Icons.Rounded.Delete, contentDescription = "Delete file")
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

  if (showCreateDialog) {
    var name by remember { mutableStateOf("notes.txt") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text("Create Document") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("File name") }, singleLine = true)
          OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Content") }, minLines = 3)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val safeName = name.trim().ifBlank { "doc.txt" }
            val localFile = File(context.filesDir, safeName)
            localFile.writeText(body)
            onSaveFile(
              VirtualFileRecord(
                name = safeName,
                folder = "Documents",
                mimeType = "text/plain",
                sizeBytes = localFile.length(),
                localPath = localFile.absolutePath,
                contentPreview = body.take(1000),
              )
            )
            showCreateDialog = false
          }
        ) {
          Text("Create")
        }
      },
      dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
    )
  }

  previewFile?.let { file ->
    AlertDialog(
      onDismissRequest = { previewFile = null },
      title = { Text(file.name) },
      text = {
        Column {
          Text("${file.mimeType} • ${file.sizeBytes} bytes", style = MonoCodeStyle)
          Spacer(modifier = Modifier.height(8.dp))
          Text(file.contentPreview.ifBlank { file.localPath }, style = MaterialTheme.typography.bodyMedium)
        }
      },
      confirmButton = { TextButton(onClick = { previewFile = null }) { Text("Close") } },
    )
  }
}

// --- 8. CAMERA & GALLERY APP ---
@Composable
fun CameraGalleryApp(onCloseApp: () -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val galleryDir = remember { File(context.filesDir, "virtual_gallery").apply { mkdirs() } }
  val photos = remember { mutableStateListOf<String>() }
  var selectedTab by remember { mutableIntStateOf(0) }
  var viewingPhoto by remember { mutableStateOf<String?>(null) }
  var captureMessage by remember { mutableStateOf<String?>(null) }

  var hasCameraPerm by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val permLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasCameraPerm = granted
    }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      uri?.let {
        photos.add(0, it.toString())
        selectedTab = 1
      }
    }

  LaunchedEffect(Unit) {
    val existing =
      galleryDir.listFiles()?.sortedByDescending { it.lastModified() }?.map { Uri.fromFile(it).toString() }.orEmpty()
    photos.clear()
    photos.addAll(existing)
  }

  val imageCapture = remember { ImageCapture.Builder().build() }

  Scaffold(
    topBar = {
      VirtualAppTopBar("Camera & Gallery", "CameraX + Photo Picker", onBack = onCloseApp) {
        IconButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          }
        ) {
          Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Pick photo from device")
        }
      }
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      TabRow(selectedTabIndex = selectedTab) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Camera") })
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Gallery (${photos.size})") })
      }

      if (selectedTab == 0) {
        if (!hasCameraPerm) {
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Icon(
              Icons.Rounded.CameraAlt,
              contentDescription = null,
              modifier = Modifier.size(52.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Camera permission needed for live capture", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              "You can also import photos without permissions using the Photo Picker icon above.",
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
              Text("Grant Camera Access")
            }
          }
        } else {
          Box(modifier = Modifier.fillMaxSize()) {
            val previewView = remember { PreviewView(context) }
            DisposableEffect(lifecycleOwner) {
              val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
              val executor = ContextCompat.getMainExecutor(context)
              cameraProviderFuture.addListener(
                {
                  runCatching {
                    val provider = cameraProviderFuture.get()
                    val preview =
                      Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                      lifecycleOwner,
                      CameraSelector.DEFAULT_BACK_CAMERA,
                      preview,
                      imageCapture,
                    )
                  }.onFailure { captureMessage = "Camera preview unavailable on this device" }
                },
                executor,
              )
              onDispose {
                runCatching { cameraProviderFuture.get().unbindAll() }
              }
            }

            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            if (!captureMessage.isNullOrBlank()) {
              Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
              ) {
                Text(captureMessage!!, modifier = Modifier.padding(10.dp))
              }
            }

            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              modifier =
                Modifier.align(Alignment.BottomCenter)
                  .padding(bottom = 24.dp)
                  .size(68.dp)
                  .clip(CircleShape)
                  .clickable {
                    val outFile = File(galleryDir, "IMG_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
                    imageCapture.takePicture(
                      outputOptions,
                      ContextCompat.getMainExecutor(context),
                      object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                          photos.add(0, Uri.fromFile(outFile).toString())
                          captureMessage = "Saved to Gallery"
                        }

                        override fun onError(exception: ImageCaptureException) {
                          captureMessage = exception.message ?: "Capture failed"
                        }
                      },
                    )
                  },
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Rounded.CameraAlt,
                  contentDescription = "Capture photo",
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(32.dp),
                )
              }
            }
          }
        }
      } else {
        if (photos.isEmpty()) {
          EmptyStatePanel(
            icon = Icons.Rounded.PhotoLibrary,
            title = "Gallery is empty",
            message = "Capture a photo with CameraX or tap the gallery button above to import via Photo Picker.",
          )
        } else {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(photos) { uriStr ->
              AsyncImage(
                model = uriStr,
                contentDescription = "Gallery photo",
                contentScale = ContentScale.Crop,
                modifier =
                  Modifier.fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { viewingPhoto = uriStr },
              )
            }
          }
        }
      }
    }
  }

  viewingPhoto?.let { uriStr ->
    AlertDialog(
      onDismissRequest = { viewingPhoto = null },
      title = { Text("Photo Preview") },
      text = {
        AsyncImage(
          model = uriStr,
          contentDescription = "Selected photo",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxWidth().height(260.dp),
        )
      },
      confirmButton = {
        TextButton(onClick = { viewingPhoto = null }) { Text("Close") }
      },
      dismissButton = {
        TextButton(
          onClick = {
            photos.remove(uriStr)
            if (uriStr.startsWith("file:")) {
              Uri.parse(uriStr).path?.let { File(it).delete() }
            }
            viewingPhoto = null
          }
        ) {
          Text("Delete")
        }
      },
    )
  }
}
