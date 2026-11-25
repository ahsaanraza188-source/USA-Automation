package com.example.ui.apps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.DevicePreferences
import com.example.domain.RegionalCatalog
import com.example.domain.RegionalFormatter
import com.example.ui.theme.MonoCodeStyle

private enum class RegionPickerTarget {
  COUNTRY,
  STATE,
  LANGUAGE,
  TIMEZONE,
  CURRENCY,
}

@Composable
fun DeviceRegionSettingsSection(
  preferences: DevicePreferences,
  onUpdatePreferences: ((DevicePreferences) -> DevicePreferences) -> Unit,
  onOpenDiagnostics: () -> Unit,
) {
  var activePicker by remember { mutableStateOf<RegionPickerTarget?>(null) }
  val now = remember(preferences) { System.currentTimeMillis() }
  val isUsaDefault =
    preferences.countryCode == "US" &&
      preferences.localeTag == "en-US" &&
      preferences.dateFormat == "MM/DD/YYYY" &&
      !preferences.use24HourTime &&
      preferences.currencyCode == "USD" &&
      preferences.temperatureUnit == "FAHRENHEIT" &&
      preferences.measurementSystem == "US_CUSTOMARY"

  Card(
    shape = RoundedCornerShape(22.dp),
    modifier = Modifier.fillMaxWidth().testTag("device_region_settings_card"),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text("Device Region Settings", style = MaterialTheme.typography.titleMedium)
            Text(
              text = if (isUsaDefault) "USA Virtual Defaults Active" else "${preferences.countryName} (${preferences.localeTag})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        TextButton(
          onClick = { onUpdatePreferences { RegionalCatalog.resetToUsaDefaults(it) } },
          modifier = Modifier.testTag("reset_usa_defaults_btn"),
        ) {
          Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("US Reset")
        }
      }

      // Live Regional Format Preview
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Live Regional Format Preview", style = MaterialTheme.typography.labelMedium)
          Text(
            text = "${RegionalFormatter.formatDate(now, preferences)} • ${RegionalFormatter.formatTime(now, preferences)} (${RegionalFormatter.shortTimezoneCode(preferences.usTimezoneId)})",
            style = MonoCodeStyle,
          )
          Text(
            text = "${RegionalFormatter.formatCurrency(1234.56, preferences)} • ${RegionalFormatter.formatTemperature(72.0, preferences)} • ${RegionalFormatter.formatDistanceFromMiles(5.0, preferences)} / ${RegionalFormatter.formatWeightFromPounds(150.0, preferences)}",
            style = MonoCodeStyle,
          )
        }
      }

      Text(
        text = "Virtual Device Region controls system language, timezone, and formatting only. Your public network IP & location permissions remain strictly separate.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      HorizontalDivider()

      RegionSettingRow("Country", "${preferences.countryName} (${preferences.countryCode})") {
        activePicker = RegionPickerTarget.COUNTRY
      }
      RegionSettingRow("State / Region", preferences.stateRegion) {
        activePicker = RegionPickerTarget.STATE
      }
      RegionSettingRow("Language & Locale", "${preferences.languageName} (${preferences.localeTag})") {
        activePicker = RegionPickerTarget.LANGUAGE
      }
      RegionSettingRow("Time Zone", preferences.usTimezoneId) {
        activePicker = RegionPickerTarget.TIMEZONE
      }
      RegionSettingRow("Currency", RegionalCatalog.findCurrency(preferences.currencyCode).label) {
        activePicker = RegionPickerTarget.CURRENCY
      }

      HorizontalDivider()

      // Date Format
      Text("Date Format", style = MaterialTheme.typography.labelLarge)
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(RegionalCatalog.dateFormats) { fmt ->
          FilterChip(
            selected = preferences.dateFormat == fmt,
            onClick = { onUpdatePreferences { it.copy(dateFormat = fmt) } },
            label = { Text(fmt) },
          )
        }
      }

      // 12-hour vs 24-hour Time Format
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("Time Format", style = MaterialTheme.typography.labelLarge)
          Text(
            text = if (preferences.use24HourTime) "24-hour clock (17:30)" else "12-hour clock (5:30 PM — US Default)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = preferences.use24HourTime,
          onCheckedChange = { use24 -> onUpdatePreferences { it.copy(use24HourTime = use24) } },
        )
      }

      // Number Format
      Text("Number Format", style = MaterialTheme.typography.labelLarge)
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(RegionalCatalog.numberFormats) { fmt ->
          FilterChip(
            selected = preferences.numberFormat == fmt,
            onClick = { onUpdatePreferences { it.copy(numberFormat = fmt) } },
            label = { Text(fmt) },
          )
        }
      }

      // Temperature & Measurement Units
      Text("Regional Preferences (Temperature & Units)", style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
          selected = preferences.temperatureUnit == "FAHRENHEIT",
          onClick = { onUpdatePreferences { it.copy(temperatureUnit = "FAHRENHEIT") } },
          label = { Text("Fahrenheit (°F)") },
          modifier = Modifier.weight(1f),
        )
        FilterChip(
          selected = preferences.temperatureUnit == "CELSIUS",
          onClick = { onUpdatePreferences { it.copy(temperatureUnit = "CELSIUS") } },
          label = { Text("Celsius (°C)") },
          modifier = Modifier.weight(1f),
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
          selected = preferences.measurementSystem == "US_CUSTOMARY",
          onClick = { onUpdatePreferences { it.copy(measurementSystem = "US_CUSTOMARY") } },
          label = { Text("US (mi, ft, in, lb)") },
          modifier = Modifier.weight(1f),
        )
        FilterChip(
          selected = preferences.measurementSystem == "METRIC",
          onClick = { onUpdatePreferences { it.copy(measurementSystem = "METRIC") } },
          label = { Text("Metric (km, m, kg)") },
          modifier = Modifier.weight(1f),
        )
      }

      OutlinedButton(
        onClick = onOpenDiagnostics,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
      ) {
        Text("Verify USA Region & Public IP in Diagnostics")
      }
    }
  }

  activePicker?.let { target ->
    RegionOptionPickerDialog(
      target = target,
      preferences = preferences,
      onDismiss = { activePicker = null },
      onSelect = { updater ->
        onUpdatePreferences(updater)
        activePicker = null
      },
    )
  }
}

@Composable
private fun RegionSettingRow(
  title: String,
  value: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Icon(Icons.Rounded.ChevronRight, contentDescription = "Change $title")
    }
  }
}

@Composable
private fun RegionOptionPickerDialog(
  target: RegionPickerTarget,
  preferences: DevicePreferences,
  onDismiss: () -> Unit,
  onSelect: ((DevicePreferences) -> DevicePreferences) -> Unit,
) {
  val title =
    when (target) {
      RegionPickerTarget.COUNTRY -> "Select Country"
      RegionPickerTarget.STATE -> "Select State / Region"
      RegionPickerTarget.LANGUAGE -> "Select Language & Locale"
      RegionPickerTarget.TIMEZONE -> "Select Time Zone"
      RegionPickerTarget.CURRENCY -> "Select Currency"
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        when (target) {
          RegionPickerTarget.COUNTRY -> {
            items(RegionalCatalog.countries) { country ->
              val selected = preferences.countryCode.equals(country.code, ignoreCase = true)
              PickerOptionCard(
                label = "${country.name} (${country.code})",
                subtitle = "${country.defaultLocaleTag} • ${country.defaultTimezoneId} • ${country.defaultCurrencyCode}",
                selected = selected,
                onClick = { onSelect { RegionalCatalog.applyCountryDefaults(it, country) } },
              )
            }
          }
          RegionPickerTarget.STATE -> {
            val states = RegionalCatalog.findCountry(preferences.countryCode).states
            items(states) { state ->
              PickerOptionCard(
                label = state,
                subtitle = preferences.countryName,
                selected = preferences.stateRegion == state,
                onClick = { onSelect { it.copy(stateRegion = state) } },
              )
            }
          }
          RegionPickerTarget.LANGUAGE -> {
            items(RegionalCatalog.languages) { lang ->
              PickerOptionCard(
                label = lang.displayName,
                subtitle = "Locale tag: ${lang.localeTag}",
                selected = preferences.localeTag == lang.localeTag,
                onClick = {
                  onSelect { it.copy(localeTag = lang.localeTag, languageName = lang.displayName) }
                },
              )
            }
          }
          RegionPickerTarget.TIMEZONE -> {
            items(RegionalCatalog.allTimezones) { tz ->
              PickerOptionCard(
                label = tz.cityLabel,
                subtitle = "${tz.zoneId} • ${tz.shortName}",
                selected = preferences.usTimezoneId == tz.zoneId,
                onClick = { onSelect { it.copy(usTimezoneId = tz.zoneId) } },
              )
            }
          }
          RegionPickerTarget.CURRENCY -> {
            items(RegionalCatalog.currencies) { cur ->
              PickerOptionCard(
                label = cur.label,
                subtitle = "Symbol: ${cur.symbol}",
                selected = preferences.currencyCode == cur.code,
                onClick = { onSelect { it.copy(currencyCode = cur.code) } },
              )
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}

@Composable
private fun PickerOptionCard(
  label: String,
  subtitle: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MonoCodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (selected) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
      }
    }
  }
}

@Composable
fun DeviceInfoAndPermissionsCard(isCloudSignedIn: Boolean) {
  val context = LocalContext.current
  val hasCamera =
    remember(context) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    }
  val hasLocation =
    remember(context) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    }
  val usedKb =
    remember(context) {
      (context.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024L).coerceAtLeast(1L)
    }

  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Device Info, Storage & Permissions", style = MaterialTheme.typography.titleMedium)
      }
      InfoLine("Hardware", "${Build.MANUFACTURER} ${Build.MODEL}")
      InfoLine("Host Android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
      InfoLine("App ID", context.packageName)
      InfoLine("Storage Mode", if (isCloudSignedIn) "Firestore Cloud + Local Cache" else "Local DataStore ($usedKb KB)")
      InfoLine("Camera Permission", if (hasCamera) "Granted" else "Not Requested / Denied")
      InfoLine("Location Permission", if (hasLocation) "Granted (Coarse)" else "Not Granted")
      InfoLine("Media Picker", "Zero-Permission Photo Picker & SAF")
    }
  }
}

@Composable
private fun InfoLine(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.width(12.dp))
    Text(value, style = MonoCodeStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
