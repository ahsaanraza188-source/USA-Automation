package com.example.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.BrowserGeoPermissionMode
import com.example.domain.VirtualLocationCatalog
import com.example.domain.VirtualLocationConfig
import com.example.domain.VirtualLocationMode
import com.example.domain.VirtualLocationPreset
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.OsTheme

@Composable
fun VirtualGpsTabContent(
  locationConfig: VirtualLocationConfig,
  customLocations: List<VirtualLocationPreset>,
  onToggleEnabled: (Boolean) -> Unit,
  onSelectMode: (VirtualLocationMode) -> Unit,
  onSelectPreset: (VirtualLocationPreset) -> Unit,
  onApplyManualCoordinates: (city: String, state: String, lat: String, lon: String, accuracy: String) -> Unit,
  onAddCustomLocation: (city: String, state: String, lat: String, lon: String) -> Unit,
  onDeleteCustomLocation: (String) -> Unit,
  onSelectBrowserPermission: (BrowserGeoPermissionMode) -> Unit,
  onToggleWebrtcGuard: (Boolean) -> Unit,
  onOpenUrlInChrome: (String) -> Unit,
) {
  var showAddDialog by remember { mutableStateOf(false) }

  var cityDraft by remember(locationConfig.city) { mutableStateOf(locationConfig.city) }
  var stateDraft by remember(locationConfig.state) { mutableStateOf(locationConfig.state) }
  var latDraft by remember(locationConfig.latitude) { mutableStateOf(locationConfig.latitude.toString()) }
  var lonDraft by remember(locationConfig.longitude) { mutableStateOf(locationConfig.longitude.toString()) }
  var accDraft by remember(locationConfig.accuracyMeters) { mutableStateOf(locationConfig.accuracyMeters.toInt().toString()) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Virtual Location Master Card
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag("virtual_gps_manager_card"),
      ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Rounded.MyLocation, null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(8.dp))
              Column {
                Text("USA Virtual GPS Manager", style = MaterialTheme.typography.titleMedium)
                Text(
                  text =
                    if (locationConfig.enabled)
                      "${locationConfig.displayLabel} • ${locationConfig.formattedCoordinates}"
                    else "Virtual GPS Disabled",
                  style = MonoCodeStyle,
                  color =
                    if (locationConfig.enabled) OsTheme.extended.verifiedEmerald
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Switch(
              checked = locationConfig.enabled,
              onCheckedChange = onToggleEnabled,
              modifier = Modifier.testTag("virtual_gps_switch"),
            )
          }

          Text("Location Source Mode", style = MaterialTheme.typography.labelLarge)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VirtualLocationMode.entries) { mode ->
              FilterChip(
                selected = locationConfig.mode == mode,
                onClick = { onSelectMode(mode) },
                label = { Text(mode.label) },
              )
            }
          }

          HorizontalDivider()

          Text("Browser Geolocation Permission Manager", style = MaterialTheme.typography.labelLarge)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BrowserGeoPermissionMode.entries) { perm ->
              FilterChip(
                selected = locationConfig.browserPermission == perm,
                onClick = { onSelectBrowserPermission(perm) },
                label = { Text(perm.label) },
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(8.dp))
              Column {
                Text("WebRTC Local IP Leak Guard", style = MaterialTheme.typography.labelLarge)
                Text(
                  "Restricts STUN/ICE host IP exposure in Chrome WebView",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Switch(checked = locationConfig.webrtcLeakProtection, onCheckedChange = onToggleWebrtcGuard)
          }

          OutlinedButton(
            onClick = { onOpenUrlInChrome("https://browserleaks.com/geo") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Test Browser Geolocation (browserleaks.com/geo)")
          }
        }
      }
    }

    // 2. 10 Real US City Presets + Custom Locations
    item {
      Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("USA Location Presets (10 Cities)", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_custom_location_btn")) {
              Icon(Icons.Rounded.AddLocationAlt, null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Add Custom")
            }
          }

          if (customLocations.isNotEmpty()) {
            Text("Your Custom Locations", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            customLocations.forEach { preset ->
              val selected = locationConfig.selectedPresetId == preset.id
              LocationPresetRow(
                preset = preset,
                selected = selected,
                onSelect = { onSelectPreset(preset) },
                onDelete = { onDeleteCustomLocation(preset.id) },
              )
            }
            HorizontalDivider()
          }

          VirtualLocationCatalog.presets.forEach { preset ->
            val selected = locationConfig.selectedPresetId == preset.id
            LocationPresetRow(
              preset = preset,
              selected = selected,
              onSelect = { onSelectPreset(preset) },
              onDelete = null,
            )
          }
        }
      }
    }

    // 3. Manual Coordinates Editor (Independent from Device Region)
    item {
      Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Manual Latitude / Longitude Configuration", style = MaterialTheme.typography.titleMedium)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = cityDraft,
              onValueChange = { cityDraft = it },
              label = { Text("City") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = stateDraft,
              onValueChange = { stateDraft = it },
              label = { Text("State") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = latDraft,
              onValueChange = { latDraft = it },
              label = { Text("Latitude") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = lonDraft,
              onValueChange = { lonDraft = it },
              label = { Text("Longitude") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = accDraft,
              onValueChange = { accDraft = it },
              label = { Text("Acc (m)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(0.8f),
            )
          }
          Button(
            onClick = { onApplyManualCoordinates(cityDraft, stateDraft, latDraft, lonDraft, accDraft) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Apply Virtual GPS Coordinates")
          }
          Text(
            text = "Limitation note: Simulator Chrome WebView and Maps receive these coordinates via navigator.geolocation when enabled. Sites using IP-based lookup instead of GPS still read your real network/VPN IP.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  if (showAddDialog) {
    var newCity by remember { mutableStateOf("") }
    var newState by remember { mutableStateOf("Texas") }
    var newLat by remember { mutableStateOf("30.2672") }
    var newLon by remember { mutableStateOf("-97.7431") }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Add Custom US Location") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = newCity, onValueChange = { newCity = it }, label = { Text("City (e.g., Austin)") }, singleLine = true)
          OutlinedTextField(value = newState, onValueChange = { newState = it }, label = { Text("State (e.g., Texas)") }, singleLine = true)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newLat, onValueChange = { newLat = it }, label = { Text("Lat") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = newLon, onValueChange = { newLon = it }, label = { Text("Lon") }, singleLine = true, modifier = Modifier.weight(1f))
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onAddCustomLocation(newCity, newState, newLat, newLon)
            showAddDialog = false
          }
        ) { Text("Save & Activate") }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun LocationPresetRow(
  preset: VirtualLocationPreset,
  selected: Boolean,
  onSelect: () -> Unit,
  onDelete: (() -> Unit)?,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onSelect),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.LocationOn,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(preset.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
          text = String.format(java.util.Locale.US, "%.4f, %.4f • %s", preset.latitude, preset.longitude, preset.timezoneId),
          style = MonoCodeStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (onDelete != null) {
        IconButton(onClick = onDelete) {
          Icon(Icons.Rounded.Delete, contentDescription = "Delete custom location", tint = MaterialTheme.colorScheme.error)
        }
      }
    }
  }
}
