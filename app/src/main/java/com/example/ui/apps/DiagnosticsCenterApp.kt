package com.example.ui.apps

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DevicePreferences
import com.example.data.network.DiagnosticLogEntry
import com.example.data.network.DiagnosticLogLevel
import com.example.data.network.ProxyTunnelRuntimeState
import com.example.domain.BrowserGeoPermissionMode
import com.example.domain.ProxyProtocol
import com.example.domain.RegionalFormatter
import com.example.domain.VirtualLocationConfig
import com.example.domain.VirtualLocationMode
import com.example.domain.VirtualLocationPreset
import com.example.domain.VpnConnectionMode
import com.example.domain.VpnProxyConfig
import com.example.ui.shell.DiagnosticsUiState
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.OsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsApp(
  diagState: DiagnosticsUiState,
  logs: List<DiagnosticLogEntry>,
  preferences: DevicePreferences,
  locationConfig: VirtualLocationConfig,
  customLocations: List<VirtualLocationPreset>,
  vpnProxyConfig: VpnProxyConfig,
  proxyRuntime: ProxyTunnelRuntimeState,
  onRefreshIp: () -> Unit,
  onRefreshGeolocation: () -> Unit,
  onToggleVirtualLocation: (Boolean) -> Unit,
  onSelectLocationMode: (VirtualLocationMode) -> Unit,
  onSelectLocationPreset: (VirtualLocationPreset) -> Unit,
  onApplyManualCoordinates: (String, String, String, String, String) -> Unit,
  onAddCustomLocation: (String, String, String, String) -> Unit,
  onDeleteCustomLocation: (String) -> Unit,
  onSelectBrowserGeoPermission: (BrowserGeoPermissionMode) -> Unit,
  onToggleWebrtcGuard: (Boolean) -> Unit,
  onSelectVpnMode: (VpnConnectionMode) -> Unit,
  onSelectGateway: (String) -> Unit,
  onConnectProxy: (ProxyProtocol, String, String, String, String, String, String, Boolean, Boolean) -> Unit,
  onDisconnectProxy: () -> Unit,
  onRunFullVerification: () -> Unit,
  onRunBrowserGeoProbe: () -> Unit,
  onRunLeakCheck: () -> Unit,
  onRunFifteenTests: (Boolean) -> Unit,
  onOpenSettings: () -> Unit,
  onOpenUrlInChrome: (String) -> Unit,
  onClearLogs: () -> Unit,
  onCloseApp: () -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabs = remember { listOf("Overview", "VPN & Proxy", "Virtual GPS", "Verify & Leaks", "Console") }

  Scaffold(
    topBar = {
      VirtualAppTopBar("Location & Network", "USA Virtual OS Management Center", onBack = onCloseApp) {
        IconButton(onClick = onRefreshIp) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Refresh network diagnostics")
        }
      }
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      if (!diagState.actionFeedback.isNullOrBlank()) {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = diagState.actionFeedback,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
      }

      ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
        tabs.forEachIndexed { idx, title ->
          Tab(
            selected = selectedTab == idx,
            onClick = { selectedTab = idx },
            text = { Text(title) },
          )
        }
      }

      when (selectedTab) {
        0 ->
          OverviewTabContent(
            diagState = diagState,
            preferences = preferences,
            locationConfig = locationConfig,
            proxyRuntime = proxyRuntime,
            onRefreshGeolocation = onRefreshGeolocation,
            onOpenTab = { selectedTab = it },
            onOpenSettings = onOpenSettings,
          )
        1 ->
          NetworkProxyTabContent(
            diagState = diagState,
            vpnProxyConfig = vpnProxyConfig,
            proxyRuntime = proxyRuntime,
            onSelectMode = onSelectVpnMode,
            onSelectGateway = onSelectGateway,
            onConnectProxy = onConnectProxy,
            onDisconnectProxy = onDisconnectProxy,
            onRefreshIp = onRefreshIp,
            onOpenUrlInChrome = onOpenUrlInChrome,
          )
        2 ->
          VirtualGpsTabContent(
            locationConfig = locationConfig,
            customLocations = customLocations,
            onToggleEnabled = onToggleVirtualLocation,
            onSelectMode = onSelectLocationMode,
            onSelectPreset = onSelectLocationPreset,
            onApplyManualCoordinates = onApplyManualCoordinates,
            onAddCustomLocation = onAddCustomLocation,
            onDeleteCustomLocation = onDeleteCustomLocation,
            onSelectBrowserPermission = onSelectBrowserGeoPermission,
            onToggleWebrtcGuard = onToggleWebrtcGuard,
            onOpenUrlInChrome = onOpenUrlInChrome,
          )
        3 ->
          VerificationLeaksTabContent(
            diagState = diagState,
            onRunFullVerification = onRunFullVerification,
            onRunBrowserGeoProbe = onRunBrowserGeoProbe,
            onRunLeakCheck = onRunLeakCheck,
            onRunFifteenTests = onRunFifteenTests,
            onOpenUrlInChrome = onOpenUrlInChrome,
          )
        4 -> ConsoleTabContent(logs = logs, onClearLogs = onClearLogs)
      }
    }
  }
}

@Composable
private fun OverviewTabContent(
  diagState: DiagnosticsUiState,
  preferences: DevicePreferences,
  locationConfig: VirtualLocationConfig,
  proxyRuntime: ProxyTunnelRuntimeState,
  onRefreshGeolocation: () -> Unit,
  onOpenTab: (Int) -> Unit,
  onOpenSettings: () -> Unit,
) {
  val locationPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      onRefreshGeolocation()
    }
  val regionChecks =
    remember(preferences, diagState.ipReport) {
      RegionalFormatter.evaluateUsaRegionVerification(preferences, diagState.ipReport)
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Strictly Separate 4-Concept Management Overview
    item {
      val ip = diagState.ipReport
      val geo = diagState.geolocation
      val telemetry = diagState.telemetry
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag("diagnostics_ip_card"),
      ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          // Concept 1: Device Region
          Text("1. Device Region (System Locale & Formatting)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          NetworkKeyValueRow("Country & State", "${preferences.countryName} (${preferences.countryCode}) • ${preferences.stateRegion}")
          NetworkKeyValueRow("Locale & Zone", "${preferences.localeTag} • ${preferences.usTimezoneId}")
          NetworkKeyValueRow("Formats", "${preferences.dateFormat} • ${if (preferences.use24HourTime) "24h" else "12h"} • ${preferences.currencyCode}")

          HorizontalDivider()

          // Concept 2: Virtual GPS Location
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(6.dp))
              Text("2. Virtual GPS Location", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { onOpenTab(2) }) { Text("Configure") }
          }
          NetworkKeyValueRow("Virtual Status", if (locationConfig.enabled) "Enabled (${locationConfig.mode.label})" else "Disabled")
          NetworkKeyValueRow("Virtual City / State", "${locationConfig.city}, ${locationConfig.state} (${locationConfig.countryCode})")
          NetworkKeyValueRow("Lat / Lon", "${locationConfig.formattedCoordinates} (±${locationConfig.accuracyMeters.toInt()}m)")
          NetworkKeyValueRow("Browser Permission", locationConfig.browserPermission.label)

          HorizontalDivider()

          // Concept 3: Actual Public IP Location
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.VpnLock, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(6.dp))
              Text("3. Actual Public IP Location", style = MaterialTheme.typography.labelLarge)
            }
            when {
              diagState.isCheckingIp -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
              ip?.isVerifiedUsa == true -> {
                Surface(shape = CircleShape, color = OsTheme.extended.verifiedEmerald) {
                  Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VerifiedUser, null, modifier = Modifier.size(12.dp), tint = OsTheme.extended.onVerifiedEmerald)
                    Spacer(Modifier.width(4.dp))
                    Text("VERIFIED US", style = MaterialTheme.typography.labelSmall, color = OsTheme.extended.onVerifiedEmerald)
                  }
                }
              }
              ip != null -> {
                Surface(shape = CircleShape, color = OsTheme.extended.warningAmber) {
                  Text("IP: ${ip.countryCode}", style = MaterialTheme.typography.labelSmall, color = OsTheme.extended.onWarningAmber, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
              }
            }
          }
          NetworkKeyValueRow("Public IP", ip?.ipAddress ?: "Checking…")
          NetworkKeyValueRow("IP Country", ip?.let { "${it.countryName} (${it.countryCode})" } ?: "--")
          NetworkKeyValueRow("ISP / VPN Provider", ip?.isp ?: "--")
          NetworkKeyValueRow(
            "VPN / Proxy Status",
            when {
              telemetry?.vpnActive == true -> "System VPN Active"
              proxyRuntime.isConnected -> "Proxy Connected (${proxyRuntime.activeEndpointLabel})"
              else -> "Not Connected (Direct)"
            },
          )
          OutlinedButton(onClick = { onOpenTab(1) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Manage USA VPN & Proxy Connection")
          }

          HorizontalDivider()

          // Concept 4: Actual Physical Location
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Public, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(6.dp))
              Text("4. Actual Physical Location (Host)", style = MaterialTheme.typography.labelLarge)
            }
            if (geo?.permissionGranted != true) {
              TextButton(onClick = { locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                Text("Check/Grant")
              }
            }
          }
          NetworkKeyValueRow("Host Permission", geo?.statusLabel ?: "Checking…")
          geo?.coordinatesLabel?.let { coords ->
            NetworkKeyValueRow("Physical Fix", coords)
          }
        }
      }
    }

    // USA Region Verification Checklist
    item {
      val virtualMatchCount = regionChecks.filter { it.key != "public_ip" }.count { it.isMatchingUsDefault }
      Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("usa_region_verification_card")) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.GppGood, null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(8.dp))
              Text("USA Region Verification", style = MaterialTheme.typography.titleMedium)
            }
            Text("$virtualMatchCount/6 US Config", style = MonoCodeStyle, color = MaterialTheme.colorScheme.primary)
          }

          regionChecks.forEach { check ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = if (check.isMatchingUsDefault) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                  contentDescription = null,
                  tint = if (check.isMatchingUsDefault) OsTheme.extended.verifiedEmerald else OsTheme.extended.warningAmber,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(check.label, style = MaterialTheme.typography.labelLarge)
                  Text("Configured: ${check.actualConfigured}", style = MonoCodeStyle)
                  Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
              Text("Region Settings")
            }
            OutlinedButton(onClick = { onOpenTab(3) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
              Text("Verify & Leaks")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ConsoleTabContent(
  logs: List<DiagnosticLogEntry>,
  onClearLogs: () -> Unit,
) {
  var logFilter by remember { mutableStateOf<DiagnosticLogLevel?>(null) }
  val filteredLogs =
    remember(logs, logFilter) { logs.filter { logFilter == null || it.level == logFilter } }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Runtime & Error Console (${filteredLogs.size})", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onClearLogs) {
          Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear logs")
        }
      }
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
          FilterChip(selected = logFilter == null, onClick = { logFilter = null }, label = { Text("All (${logs.size})") })
        }
        items(DiagnosticLogLevel.entries) { lvl ->
          val count = logs.count { it.level == lvl }
          FilterChip(
            selected = logFilter == lvl,
            onClick = { logFilter = if (logFilter == lvl) null else lvl },
            label = { Text("${lvl.name} ($count)") },
          )
        }
      }
    }

    if (filteredLogs.isEmpty()) {
      item {
        Text(
          "No real errors or events recorded for this filter.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
      items(filteredLogs.take(40)) { entry ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "[${fmt.format(Date(entry.timestampEpochMillis))}] ${entry.level.name} • ${entry.component}",
              style = MonoCodeStyle,
              color =
                when (entry.level) {
                  DiagnosticLogLevel.ERROR -> MaterialTheme.colorScheme.error
                  DiagnosticLogLevel.WARN -> OsTheme.extended.warningAmber
                  DiagnosticLogLevel.INFO -> MaterialTheme.colorScheme.primary
                },
            )
            Text(entry.message, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            if (entry.rootCause.isNotBlank()) {
              Text("Cause: ${entry.rootCause}", style = MonoCodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.recoveryHint.isNotBlank()) {
              Text("Fix: ${entry.recoveryHint}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }
  }
}
