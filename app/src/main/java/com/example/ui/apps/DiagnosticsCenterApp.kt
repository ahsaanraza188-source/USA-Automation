package com.example.ui.apps

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DevicePreferences
import com.example.data.network.DiagnosticLogEntry
import com.example.data.network.DiagnosticLogLevel
import com.example.data.network.SelfTestStatus
import com.example.domain.RegionalFormatter
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
  onRefreshIp: () -> Unit,
  onRefreshGeolocation: () -> Unit,
  onRunFifteenTests: (Boolean) -> Unit,
  onOpenSettings: () -> Unit,
  onClearLogs: () -> Unit,
  onCloseApp: () -> Unit,
) {
  val context = LocalContext.current
  val hasCameraHardware =
    remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
  var logFilter by remember { mutableStateOf<DiagnosticLogLevel?>(null) }

  val locationPermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      onRefreshGeolocation()
    }

  val regionChecks =
    remember(preferences, diagState.ipReport) {
      RegionalFormatter.evaluateUsaRegionVerification(preferences, diagState.ipReport)
    }

  val filteredLogs =
    remember(logs, logFilter) {
      logs.filter { logFilter == null || it.level == logFilter }
    }

  Scaffold(
    topBar = {
      VirtualAppTopBar("Diagnostics Center", "Network, Region & Error Console", onBack = onCloseApp) {
        IconButton(onClick = onRefreshIp) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Refresh network diagnostics")
        }
      }
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // 1. Strict 3-Way Separation: Virtual Region vs Device Geolocation vs Real Public IP
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
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Actual Public Network IP", style = MaterialTheme.typography.titleLarge)
              when {
                diagState.isCheckingIp -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                ip?.isVerifiedUsa == true -> {
                  Surface(shape = CircleShape, color = OsTheme.extended.verifiedEmerald) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Rounded.VerifiedUser, null, modifier = Modifier.size(14.dp), tint = OsTheme.extended.onVerifiedEmerald)
                      Spacer(Modifier.width(4.dp))
                      Text("VERIFIED USA IP", style = MaterialTheme.typography.labelSmall, color = OsTheme.extended.onVerifiedEmerald)
                    }
                  }
                }
                ip != null -> {
                  Surface(shape = CircleShape, color = OsTheme.extended.warningAmber) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Rounded.Public, null, modifier = Modifier.size(14.dp), tint = OsTheme.extended.onWarningAmber)
                      Spacer(Modifier.width(4.dp))
                      Text("NON-US (${ip.countryCode})", style = MaterialTheme.typography.labelSmall, color = OsTheme.extended.onWarningAmber)
                    }
                  }
                }
              }
            }

            if (!diagState.ipError.isNullOrBlank()) {
              Text(diagState.ipError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (ip != null) {
              DiagKeyValueRow("Public IP Address", ip.ipAddress)
              DiagKeyValueRow("Verified Country", "${ip.countryName} (${ip.countryCode})")
              DiagKeyValueRow("City / Region", "${ip.city}, ${ip.region}")
              DiagKeyValueRow("ISP / Org", ip.isp)
              DiagKeyValueRow("ASN & Latency", "${ip.asn} • ${ip.latencyMs} ms (${ip.providerName})")
            }

            HorizontalDivider()

            // Concept 1: Virtual Device Region
            Text("1. Virtual Device Region (UI & Locale Only)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            DiagKeyValueRow("Country & State", "${preferences.countryName} (${preferences.countryCode}) • ${preferences.stateRegion}")
            DiagKeyValueRow("Locale & Zone", "${preferences.localeTag} • ${preferences.usTimezoneId}")
            DiagKeyValueRow("Formats", "${preferences.dateFormat} • ${if (preferences.use24HourTime) "24h" else "12h"} • ${preferences.currencyCode}")

            HorizontalDivider()

            // Concept 2: Device / Browser Geolocation Permission
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("2. Device Geolocation Permission", style = MaterialTheme.typography.labelLarge)
              }
              if (geo?.permissionGranted != true) {
                TextButton(onClick = { locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                  Text("Check/Grant")
                }
              }
            }
            DiagKeyValueRow("Status", geo?.statusLabel ?: "Checking…")
            geo?.coordinatesLabel?.let { coords ->
              DiagKeyValueRow("Last Cached Fix", coords)
            }

            HorizontalDivider()

            // Concept 3: Legitimate USA VPN Guidance
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.VpnLock, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(6.dp))
              Text("3. User-Authorized USA VPN Status", style = MaterialTheme.typography.labelLarge)
            }
            DiagKeyValueRow(
              "VPN Tunnel",
              if (telemetry?.vpnActive == true) "Active (System VPN interface detected)" else "Not Active",
            )
            Text(
              text = stringResource(R.string.diagnostics_ip_disclaimer) +
                " To obtain a genuine USA public IP, connect a user-authorized VPN client (e.g., WireGuard, OpenVPN, or Proton VPN) to a US server, then tap Refresh above.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // 2. USA Region Verification Checklist (Honest & Independent)
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

            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
              Text("Open Device Region Settings")
            }
          }
        }
      }

      // 3. DNS Diagnostics & HTTPS Connectivity Testing
      item {
        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Dns, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("DNS & HTTPS Diagnostics", style = MaterialTheme.typography.titleMedium)
              }
              if (diagState.isRunningNetSuite) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
              }
            }

            Text("DNS Resolution", style = MaterialTheme.typography.labelLarge)
            if (diagState.dnsResults.isEmpty()) {
              Text("Running DNS resolution checks…", style = MaterialTheme.typography.bodySmall)
            } else {
              diagState.dnsResults.forEach { dns ->
                DiagKeyValueRow(
                  dns.host,
                  if (dns.success) "${dns.resolvedAddresses.firstOrNull()} (${dns.latencyMs}ms)"
                  else "FAIL: ${dns.error}",
                )
              }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(6.dp))
              Text("HTTPS Connectivity & TLS", style = MaterialTheme.typography.labelLarge)
            }
            if (diagState.httpsResults.isEmpty()) {
              Text("Running HTTPS connectivity probes…", style = MaterialTheme.typography.bodySmall)
            } else {
              diagState.httpsResults.forEach { probe ->
                DiagKeyValueRow(
                  probe.label,
                  if (probe.success) "HTTP ${probe.httpCode} • ${probe.tlsProtocol ?: "TLS"} (${probe.latencyMs}ms)"
                  else "FAIL: ${probe.error}",
                )
              }
            }
          }
        }
      }

      // 4. Device Runtime Telemetry
      item {
        diagState.telemetry?.let { t ->
          Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Runtime Telemetry", style = MaterialTheme.typography.titleMedium)
              DiagKeyValueRow("Device", "${t.model} (${t.androidVersion}, API ${t.apiLevel})")
              DiagKeyValueRow("Transport", t.transportLabel)
              DiagKeyValueRow("RAM", "${t.availRamMb} MB free / ${t.totalRamMb} MB total")
              DiagKeyValueRow("App Private Storage", "${t.appStorageUsedKb} KB used")
            }
          }
        }
      }

      // 5. 15-Point Self-Test Suite
      item {
        Button(
          onClick = { onRunFifteenTests(hasCameraHardware) },
          enabled = !diagState.isRunningTests,
          shape = RoundedCornerShape(20.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("run_15_tests_btn"),
        ) {
          if (diagState.isRunningTests) {
            CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Running 15-Point Diagnostics…")
          } else {
            Text("Run 15-Point Self-Test Suite", fontWeight = FontWeight.Bold)
          }
        }
      }

      items(diagState.selfTests, key = { it.number }) { item ->
        val (icon, tint) =
          when (item.status) {
            SelfTestStatus.PASS -> Icons.Rounded.CheckCircle to OsTheme.extended.verifiedEmerald
            SelfTestStatus.WARN -> Icons.Rounded.Warning to OsTheme.extended.warningAmber
            SelfTestStatus.FAIL -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
            else -> Icons.Rounded.Info to MaterialTheme.colorScheme.onSurfaceVariant
          }
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(icon, contentDescription = item.status.name, tint = tint)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("${item.number}. ${item.title}", style = MaterialTheme.typography.labelLarge)
              Text(item.detail, style = MonoCodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }

      // 6. Centralized Error & Diagnostic Console
      item {
        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("diagnostics_error_console")) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                FilterChip(
                  selected = logFilter == null,
                  onClick = { logFilter = null },
                  label = { Text("All (${logs.size})") },
                )
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
            if (filteredLogs.isEmpty()) {
              Text(
                "No real errors or events recorded for this filter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
              filteredLogs.take(25).forEach { entry ->
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
      }
    }
  }
}

@Composable
private fun DiagKeyValueRow(label: String, value: String) {
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
