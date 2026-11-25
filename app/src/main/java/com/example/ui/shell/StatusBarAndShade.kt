package com.example.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.network.PublicIpReport
import com.example.domain.VirtualAppId
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.OsPaletteKey
import com.example.ui.theme.OsTheme

@Composable
fun VirtualStatusBar(
  timeFormatted: String,
  timezoneId: String,
  wifiEnabled: Boolean,
  dndEnabled: Boolean,
  ipReport: PublicIpReport?,
  onToggleShade: () -> Unit,
) {
  val shortZone =
    when (timezoneId) {
      "America/New_York" -> "US-EST"
      "America/Chicago" -> "US-CST"
      "America/Denver" -> "US-MST"
      "America/Los_Angeles" -> "US-PST"
      else -> "US"
    }

  Surface(
    color = OsTheme.extended.statusBarScrim,
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onToggleShade)
        .testTag("virtual_status_bar"),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = timeFormatted,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.96f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        ) {
          Text(
            text = shortZone,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }
        if (ipReport != null) {
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            shape = CircleShape,
            color =
              if (ipReport.isVerifiedUsa) OsTheme.extended.verifiedEmerald
              else OsTheme.extended.warningAmber,
          ) {
            Text(
              text = "IP:${ipReport.countryCode}",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (ipReport.isVerifiedUsa) OsTheme.extended.onVerifiedEmerald
                else OsTheme.extended.onWarningAmber,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (dndEnabled) {
          Icon(
            imageVector = Icons.Rounded.DoNotDisturbOn,
            contentDescription = "Do not disturb on",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error,
          )
        }
        Icon(
          imageVector = if (wifiEnabled) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
          contentDescription = if (wifiEnabled) "Wi-Fi connected" else "Wi-Fi off",
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
        )
        Icon(
          imageVector = Icons.Rounded.SignalCellularAlt,
          contentDescription = "5G signal",
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.BatteryChargingFull,
            contentDescription = "Battery 94%",
            modifier = Modifier.size(16.dp),
            tint = OsTheme.extended.verifiedEmerald,
          )
          Text(
            text = "94%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
          )
        }
        Icon(
          imageVector = Icons.Rounded.ExpandMore,
          contentDescription = stringResource(R.string.shade_toggle_desc),
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        )
      }
    }
  }
}

@Composable
fun QuickSettingsShadeOverlay(
  visible: Boolean,
  dateFormatted: String,
  shellState: ShellUiState,
  darkMode: Boolean,
  paletteKey: OsPaletteKey,
  ipReport: PublicIpReport?,
  onDismiss: () -> Unit,
  onToggleWifi: () -> Unit,
  onToggleBluetooth: () -> Unit,
  onToggleDnd: () -> Unit,
  onToggleDarkMode: () -> Unit,
  onSelectPalette: (OsPaletteKey) -> Unit,
  onBrightnessChange: (Float) -> Unit,
  onOpenApp: (VirtualAppId) -> Unit,
) {
  if (visible) {
    BackHandler { onDismiss() }
  }

  AnimatedVisibility(
    visible = visible,
    enter =
      slideInVertically(
        initialOffsetY = { -it / 2 },
        animationSpec = spring(
          stiffness = Spring.StiffnessMediumLow,
          dampingRatio = Spring.DampingRatioNoBouncy,
        ),
      ) + fadeIn(animationSpec = tween(200)),
    exit =
      slideOutVertically(
        targetOffsetY = { -it / 2 },
        animationSpec = tween(180),
      ) + fadeOut(animationSpec = tween(160)),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
          .clickable(onClick = onDismiss)
    ) {
      Surface(
        modifier =
          Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, start = 12.dp, end = 12.dp)
            .clickable(enabled = false) {},
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column {
              Text(
                text = "Quick Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = dateFormatted,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Row {
              IconButton(
                onClick = { onOpenApp(VirtualAppId.SETTINGS) },
                modifier = Modifier.testTag("shade_settings_btn"),
              ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Open Settings")
              }
              IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("shade_close_btn"),
              ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Quick Settings")
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // 2x3 Quick Setting Pills
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            QuickTile(
              icon = if (shellState.wifiEnabled) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
              label = "Wi-Fi",
              sublabel = if (shellState.wifiEnabled) "VirtualFiber" else "Off",
              active = shellState.wifiEnabled,
              onClick = onToggleWifi,
              modifier = Modifier.weight(1f),
            )
            QuickTile(
              icon = Icons.Rounded.Bluetooth,
              label = "Bluetooth",
              sublabel = if (shellState.bluetoothEnabled) "Ready" else "Off",
              active = shellState.bluetoothEnabled,
              onClick = onToggleBluetooth,
              modifier = Modifier.weight(1f),
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            QuickTile(
              icon = if (darkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
              label = "Theme",
              sublabel = if (darkMode) "Dark Mode" else "Light Mode",
              active = darkMode,
              onClick = onToggleDarkMode,
              modifier = Modifier.weight(1f),
            )
            QuickTile(
              icon = Icons.Rounded.DoNotDisturbOn,
              label = "DND",
              sublabel = if (shellState.dndEnabled) "Silenced" else "Off",
              active = shellState.dndEnabled,
              onClick = onToggleDnd,
              modifier = Modifier.weight(1f),
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            QuickTile(
              icon = Icons.Rounded.Public,
              label = "US Locale",
              sublabel = "en-US",
              active = true,
              onClick = { onOpenApp(VirtualAppId.SETTINGS) },
              modifier = Modifier.weight(1f),
            )
            QuickTile(
              icon = Icons.Rounded.MonitorHeart,
              label = "Real IP",
              sublabel = ipReport?.let { "${it.ipAddress} (${it.countryCode})" } ?: "Check IP",
              active = ipReport?.isVerifiedUsa == true,
              onClick = { onOpenApp(VirtualAppId.DIAGNOSTICS) },
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Brightness Slider
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(
              imageVector = Icons.Rounded.Brightness6,
              contentDescription = "Screen brightness",
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Slider(
              value = shellState.brightnessLevel,
              onValueChange = onBrightnessChange,
              valueRange = 0.35f..1.0f,
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Material You Palette",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OsPaletteKey.entries.forEach { entry ->
              val selected = entry == paletteKey
              Surface(
                shape = RoundedCornerShape(14.dp),
                color =
                  if (selected) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant,
                modifier =
                  Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectPalette(entry) },
              ) {
                Text(
                  text = entry.name.lowercase().replaceFirstChar { it.uppercase() },
                  style = MonoCodeStyle,
                  color =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                  maxLines = 1,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun QuickTile(
  icon: ImageVector,
  label: String,
  sublabel: String,
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.height(64.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
    shape = RoundedCornerShape(20.dp),
    color =
      if (active) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint =
          if (active) MaterialTheme.colorScheme.onPrimaryContainer
          else MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color =
            if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = sublabel,
          style = MaterialTheme.typography.labelSmall,
          color =
            if (active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          maxLines = 1,
        )
      }
    }
  }
}
