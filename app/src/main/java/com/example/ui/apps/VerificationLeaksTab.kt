package com.example.ui.apps

import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.network.SelfTestStatus
import com.example.ui.shell.DiagnosticsUiState
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.OsTheme

@Composable
fun VerificationLeaksTabContent(
  diagState: DiagnosticsUiState,
  onRunFullVerification: () -> Unit,
  onRunBrowserGeoProbe: () -> Unit,
  onRunLeakCheck: () -> Unit,
  onRunFifteenTests: (Boolean) -> Unit,
  onOpenUrlInChrome: (String) -> Unit,
) {
  val context = LocalContext.current
  val hasCameraHardware =
    remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Primary Location & Network Verification Controls
    item {
      Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Location & Network Verification", style = MaterialTheme.typography.titleMedium)
          }
          Text(
            text = "Tests your actual public IP, VPN/Proxy route, DNS/WebRTC leaks, Virtual GPS bounds, permission state, and live browser navigator.geolocation output.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(
            onClick = onRunFullVerification,
            enabled = !diagState.isRunningVerification,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_full_verification_btn"),
          ) {
            if (diagState.isRunningVerification) {
              CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
              Spacer(Modifier.width(8.dp))
              Text("Verifying IP, Routing & Browser GPS…")
            } else {
              Text("Run Full Location & Network Verification", fontWeight = FontWeight.Bold)
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = onRunBrowserGeoProbe,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f),
            ) {
              Text("Test Browser GPS")
            }
            OutlinedButton(
              onClick = onRunLeakCheck,
              enabled = !diagState.isRunningLeakCheck,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f),
            ) {
              Text(if (diagState.isRunningLeakCheck) "Checking…" else "Leak Check")
            }
          }
          if (!diagState.browserGeoProbeResult.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "Live Chrome WebView Probe: ${diagState.browserGeoProbeResult}",
                style = MonoCodeStyle,
                modifier = Modifier.padding(10.dp),
              )
            }
          }
        }
      }
    }

    // 2. Verification Results List with Real Errors & Fixes
    items(diagState.verificationItems, key = { "verify_${it.number}" }) { item ->
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
          verticalAlignment = Alignment.Top,
        ) {
          Icon(icon, contentDescription = item.status.name, tint = tint)
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${item.number}. ${item.title}", style = MaterialTheme.typography.labelLarge)
            Text(item.detail, style = MonoCodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.remediation.isNotBlank()) {
              Text("How to fix: ${item.remediation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }

    // 3. Network & Leak Check Report Card
    diagState.leakReport?.let { leak ->
      item {
        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(8.dp))
              Text("Network & DNS Leak Report", style = MaterialTheme.typography.titleMedium)
            }
            NetworkKeyValueRow("Routed Public IP", "${leak.routedIp ?: "--"} (${leak.routedCountry ?: "--"})")
            NetworkKeyValueRow("Direct Baseline IP", "${leak.directIp ?: "--"} (${leak.directCountry ?: "--"})")
            NetworkKeyValueRow("DoH (dns.google)", if (leak.dohResolverReachable) "Reachable" else "Unreachable")
            NetworkKeyValueRow("WebRTC IP Guard", if (leak.webrtcGuardEnabled) "Enabled in Chrome" else "Off")
            HorizontalDivider()
            Text(leak.dnsLeakRiskLabel, style = MonoCodeStyle, color = MaterialTheme.colorScheme.primary)
            Text(leak.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }

    // 4. External Website Location Testing in Simulator Chrome
    item {
      Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Website Location Testing (Simulator Chrome)", style = MaterialTheme.typography.titleMedium)
          }
          Text(
            text = "Open real verification websites inside the simulator's Chrome browser to inspect your routed IP, HTML5 GPS coordinates, and leak posture.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onOpenUrlInChrome("https://ipinfo.io/") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
              Text("ipinfo.io (IP)")
            }
            OutlinedButton(onClick = { onOpenUrlInChrome("https://browserleaks.com/geo") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
              Text("BrowserLeaks Geo")
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onOpenUrlInChrome("https://ipleak.net/") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
              Text("ipleak.net (Leaks)")
            }
            OutlinedButton(onClick = { onOpenUrlInChrome("https://whoer.net/") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
              Text("whoer.net (Check)")
            }
          }
        }
      }
    }

    // 5. 15-Point OS Self-Test Suite
    item {
      Button(
        onClick = { onRunFifteenTests(hasCameraHardware) },
        enabled = !diagState.isRunningTests,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_15_tests_btn"),
      ) {
        if (diagState.isRunningTests) {
          CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
          Text("Running 15-Point OS Diagnostics…")
        } else {
          Text("Run 15-Point OS Self-Test Suite", fontWeight = FontWeight.Bold)
        }
      }
    }

    items(diagState.selfTests, key = { "self_${it.number}" }) { item ->
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
  }
}
