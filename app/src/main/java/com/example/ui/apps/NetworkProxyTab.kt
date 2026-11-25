package com.example.ui.apps

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.network.ProxyTunnelRuntimeState
import com.example.data.network.ProxyTunnelStatus
import com.example.domain.ProxyProtocol
import com.example.domain.VirtualLocationCatalog
import com.example.domain.VpnConnectionMode
import com.example.domain.VpnProxyConfig
import com.example.ui.shell.DiagnosticsUiState
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.OsTheme

@Composable
fun NetworkProxyTabContent(
  diagState: DiagnosticsUiState,
  vpnProxyConfig: VpnProxyConfig,
  proxyRuntime: ProxyTunnelRuntimeState,
  onSelectMode: (VpnConnectionMode) -> Unit,
  onSelectGateway: (String) -> Unit,
  onConnectProxy: (
    protocol: ProxyProtocol,
    gatewayId: String,
    host: String,
    portStr: String,
    providerLabel: String,
    username: String,
    password: String,
    bypassLocal: Boolean,
    clearSavedCredentials: Boolean,
  ) -> Unit,
  onDisconnectProxy: () -> Unit,
  onRefreshIp: () -> Unit,
  onOpenUrlInChrome: (String) -> Unit,
) {
  val context = LocalContext.current
  val telemetry = diagState.telemetry
  val ip = diagState.ipReport
  val selectedGateway = VirtualLocationCatalog.findGateway(vpnProxyConfig.selectedGatewayId)

  var selectedProtocol by remember(vpnProxyConfig.protocol) { mutableStateOf(vpnProxyConfig.protocol) }
  var hostDraft by remember(vpnProxyConfig.host) { mutableStateOf(vpnProxyConfig.host) }
  var portDraft by remember(vpnProxyConfig.port) { mutableStateOf(vpnProxyConfig.port.toString()) }
  var providerDraft by remember(vpnProxyConfig.providerLabel) { mutableStateOf(vpnProxyConfig.providerLabel) }
  var usernameDraft by remember { mutableStateOf("") }
  var passwordDraft by remember { mutableStateOf("") }
  var bypassLocalDraft by remember(vpnProxyConfig.bypassLocalAddresses) { mutableStateOf(vpnProxyConfig.bypassLocalAddresses) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Live Routed Exit Summary Card
    item {
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag("vpn_exit_status_card"),
      ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.VpnLock, null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(8.dp))
              Text("USA Network Exit Status", style = MaterialTheme.typography.titleMedium)
            }
            val badgeBg =
              when {
                ip?.isVerifiedUsa == true -> OsTheme.extended.verifiedEmerald
                proxyRuntime.status == ProxyTunnelStatus.ERROR -> MaterialTheme.colorScheme.error
                else -> OsTheme.extended.warningAmber
              }
            val badgeFg =
              when {
                ip?.isVerifiedUsa == true -> OsTheme.extended.onVerifiedEmerald
                proxyRuntime.status == ProxyTunnelStatus.ERROR -> MaterialTheme.colorScheme.onError
                else -> OsTheme.extended.onWarningAmber
              }
            Surface(shape = CircleShape, color = badgeBg) {
              Text(
                text =
                  when {
                    proxyRuntime.status == ProxyTunnelStatus.CONNECTING -> "CONNECTING"
                    ip?.isVerifiedUsa == true -> "VERIFIED USA"
                    ip != null -> "EXIT: ${ip.countryCode}"
                    else -> "UNVERIFIED"
                  },
                style = MaterialTheme.typography.labelSmall,
                color = badgeFg,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }

          if (ip != null) {
            NetworkKeyValueRow("Public IP", ip.ipAddress)
            NetworkKeyValueRow("Country", "${ip.countryName} (${ip.countryCode})")
            NetworkKeyValueRow("City / Region", "${ip.city}, ${ip.region}")
            NetworkKeyValueRow("ISP / Provider", ip.isp)
            NetworkKeyValueRow("Active Route", ip.routedVia)
          } else {
            Text(
              text = diagState.ipError ?: "Tap Verify Connection below to probe the real outbound IP.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = onRefreshIp,
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text("Verify IP Now")
            }
            OutlinedButton(
              onClick = { onOpenUrlInChrome("https://ipinfo.io/") },
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(14.dp),
            ) {
              Text("Check in Chrome")
            }
          }
        }
      }
    }

    // 2. Connection Mode & Target US Gateway Selection
    item {
      Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Connection Method", style = MaterialTheme.typography.titleMedium)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VpnConnectionMode.entries) { mode ->
              FilterChip(
                selected = vpnProxyConfig.mode == mode,
                onClick = { onSelectMode(mode) },
                label = { Text(mode.label) },
              )
            }
          }

          Text("Target USA Server Region", style = MaterialTheme.typography.labelLarge)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VirtualLocationCatalog.usaGatewayRegions, key = { it.id }) { gw ->
              FilterChip(
                selected = vpnProxyConfig.selectedGatewayId == gw.id,
                onClick = { onSelectGateway(gw.id) },
                label = { Text("${gw.city}, ${gw.stateCode}") },
              )
            }
          }
          Text(
            text = "Selected gateway target: ${selectedGateway.recommendedVpnNote} (${selectedGateway.timezoneId}). Connect your matching US VPN node or proxy endpoint below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // 3. Android System VPN (Whole-Device Tunnel)
    item {
      Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(8.dp))
              Text("Android System USA VPN", style = MaterialTheme.typography.titleMedium)
            }
            Text(
              text = if (telemetry?.vpnActive == true) "ACTIVE" else "OFF",
              style = MonoCodeStyle,
              color =
                if (telemetry?.vpnActive == true) OsTheme.extended.verifiedEmerald
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = "Routes ALL simulator Chrome, APIs, and DNS traffic through a real user-authorized Android VPN tunnel (Proton VPN, WireGuard, OpenVPN, Tailscale US exit). No traffic leaks to your ISP when enabled with Android 'Block connections without VPN'.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
              onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                  .onFailure {
                    runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                  }
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f),
            ) {
              Text("Open VPN Settings", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
              onClick = {
                runCatching {
                  context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=ch.protonvpn.android"))
                  )
                }
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f),
            ) {
              Text("Proton VPN (US)", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
          OutlinedButton(
            onClick = {
              runCatching {
                context.startActivity(
                  Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.wireguard.android"))
                )
              }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Install WireGuard Client")
          }
        }
      }
    }

    // 4. Configurable USA Secure Proxy Tunnel (Browser WebView + Diagnostics)
    item {
      Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().testTag("proxy_tunnel_config_card")) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("USA Proxy Tunnel (Browser + Diagnostics)", style = MaterialTheme.typography.titleMedium)
          }
          Text(
            text =
              if (proxyRuntime.webViewProxySupported)
                "WebView PROXY_OVERRIDE is supported. Connecting routes simulator Chrome WebView and Diagnostics checks through your legitimate USA HTTPS/HTTP/SOCKS5 proxy."
              else
                "Note: WebView PROXY_OVERRIDE is unavailable on this Android WebView build; proxy applies to Diagnostics checks, while Chrome requires an Android System VPN.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ProxyProtocol.entries) { proto ->
              FilterChip(
                selected = selectedProtocol == proto,
                onClick = {
                  selectedProtocol = proto
                  if (portDraft == "8080" || portDraft == "443" || portDraft == "1080") {
                    portDraft = proto.defaultPort.toString()
                  }
                },
                label = { Text(proto.label) },
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = hostDraft,
              onValueChange = { hostDraft = it },
              label = { Text("Proxy Host / IP") },
              placeholder = { Text("us-ny.yourproxy.net") },
              singleLine = true,
              modifier = Modifier.weight(2f).testTag("proxy_host_input"),
            )
            OutlinedTextField(
              value = portDraft,
              onValueChange = { portDraft = it },
              label = { Text("Port") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("proxy_port_input"),
            )
          }

          OutlinedTextField(
            value = providerDraft,
            onValueChange = { providerDraft = it },
            label = { Text("Provider / Server Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = usernameDraft,
              onValueChange = { usernameDraft = it },
              label = { Text(if (vpnProxyConfig.hasSavedCredentials) "Username (Saved)" else "Username (Optional)") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = passwordDraft,
              onValueChange = { passwordDraft = it },
              label = { Text(if (vpnProxyConfig.hasSavedCredentials) "Password (Encrypted)" else "Password (Optional)") },
              visualTransformation = PasswordVisualTransformation(),
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Bypass Local LAN Addresses", style = MaterialTheme.typography.labelLarge)
              Text(
                "Skip proxy for localhost / 192.168.* only",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(checked = bypassLocalDraft, onCheckedChange = { bypassLocalDraft = it })
          }

          if (proxyRuntime.status == ProxyTunnelStatus.ERROR && !proxyRuntime.errorMessage.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                  proxyRuntime.errorMessage,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
              }
            }
          } else if (proxyRuntime.isConnected) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = "Proxy Connected: ${proxyRuntime.activeEndpointLabel} (${proxyRuntime.protocolLabel})",
                  style = MaterialTheme.typography.labelLarge,
                )
                Text(
                  text = "WebView Browser Proxy Applied: ${proxyRuntime.webViewProxyApplied}",
                  style = MonoCodeStyle,
                )
              }
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
              onClick = {
                onConnectProxy(
                  selectedProtocol,
                  vpnProxyConfig.selectedGatewayId,
                  hostDraft,
                  portDraft,
                  providerDraft,
                  usernameDraft,
                  passwordDraft,
                  bypassLocalDraft,
                  false,
                )
              },
              enabled = proxyRuntime.status != ProxyTunnelStatus.CONNECTING,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.weight(1f).testTag("connect_proxy_btn"),
            ) {
              if (proxyRuntime.status == ProxyTunnelStatus.CONNECTING) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(6.dp))
                Text("Verifying…")
              } else {
                Text(if (proxyRuntime.isConnected) "Reconnect & Verify" else "Connect & Verify")
              }
            }
            if (proxyRuntime.isConnected) {
              OutlinedButton(
                onClick = onDisconnectProxy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).testTag("disconnect_proxy_btn"),
              ) {
                Text("Disconnect")
              }
            }
          }

          if (vpnProxyConfig.hasSavedCredentials) {
            TextButton(
              onClick = {
                onConnectProxy(
                  selectedProtocol,
                  vpnProxyConfig.selectedGatewayId,
                  hostDraft,
                  portDraft,
                  providerDraft,
                  "",
                  "",
                  bypassLocalDraft,
                  true,
                )
              }
            ) {
              Icon(Icons.Rounded.Security, null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Clear Saved Encrypted Credentials")
            }
          }
        }
      }
    }

    // 5. DNS & HTTPS Probes
    item {
      Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Dns, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("DNS & HTTPS Routing Probes", style = MaterialTheme.typography.titleMedium)
          }
          diagState.dnsResults.forEach { dns ->
            NetworkKeyValueRow(
              dns.host,
              if (dns.success) "${dns.resolvedAddresses.firstOrNull()} (${dns.latencyMs}ms)"
              else "FAIL: ${dns.error}",
            )
          }
          HorizontalDivider()
          diagState.httpsResults.forEach { probe ->
            NetworkKeyValueRow(
              probe.label,
              if (probe.success) "HTTP ${probe.httpCode} • ${probe.tlsProtocol ?: "TLS"} (${probe.latencyMs}ms)"
              else "FAIL: ${probe.error}",
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun NetworkKeyValueRow(label: String, value: String) {
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
