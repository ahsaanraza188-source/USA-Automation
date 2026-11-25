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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CalendarEvent
import com.example.data.model.DevicePreferences
import com.example.data.network.PublicIpReport
import com.example.domain.AppCategory
import com.example.domain.RegionalFormatter
import com.example.domain.VirtualAppCatalog
import com.example.domain.VirtualAppId
import com.example.domain.VirtualAppSpec
import com.example.ui.theme.OsPaletteKey
import com.example.ui.theme.OsTheme

@Composable
fun VirtualHomeScreen(
  paletteKey: OsPaletteKey,
  dateFormatted: String,
  timeFormatted: String,
  preferences: DevicePreferences = DevicePreferences(),
  nextEvent: CalendarEvent?,
  ipReport: PublicIpReport?,
  pinnedAppIds: List<String>,
  onLaunchApp: (VirtualAppId) -> Unit,
  onOpenDrawer: () -> Unit,
  onOpenSearch: () -> Unit,
) {
  val wallpaperRes =
    if (paletteKey == OsPaletteKey.MEADOW) R.drawable.img_wallpaper_meadow
    else R.drawable.img_wallpaper_cobalt

  val homeGridApps =
    remember(pinnedAppIds) {
      val defaults =
        listOf(
          VirtualAppId.GMAIL,
          VirtualAppId.MAPS,
          VirtualAppId.CALENDAR,
          VirtualAppId.NOTES,
          VirtualAppId.CLOCK,
          VirtualAppId.CALCULATOR,
          VirtualAppId.FILES,
          VirtualAppId.CONTACTS,
          VirtualAppId.PLAY_DISCOVERY,
          VirtualAppId.DIAGNOSTICS,
          VirtualAppId.SETTINGS,
        )
      val pinned = pinnedAppIds.mapNotNull { VirtualAppCatalog.findByKey(it) }
      (pinned + defaults.map { VirtualAppCatalog.findById(it) })
        .distinctBy { it.appId }
        .take(12)
    }

  val dockApps =
    remember {
      listOf(
        VirtualAppCatalog.findById(VirtualAppId.CHROME),
        VirtualAppCatalog.findById(VirtualAppId.MESSAGES),
        VirtualAppCatalog.findById(VirtualAppId.CAMERA_GALLERY),
        VirtualAppCatalog.findById(VirtualAppId.YOUTUBE),
      )
    }

  Box(modifier = Modifier.fillMaxSize()) {
    Image(
      painter = painterResource(id = wallpaperRes),
      contentDescription = null,
      modifier = Modifier.fillMaxSize().alpha(0.32f),
      contentScale = ContentScale.Crop,
    )
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              listOf(
                MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
              )
            )
          )
    )

    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // Pixel At a Glance Header
      Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
          tonalElevation = 2.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = dateFormatted,
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "${preferences.countryCode} (${preferences.localeTag}) • $timeFormatted",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text =
                    "${RegionalFormatter.formatTemperature(72.0, preferences)} • " +
                      "${preferences.currencyCode} • " +
                      if (preferences.measurementSystem == "US_CUSTOMARY") "US Units (mi, lb)" else "Metric (km, kg)",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }

              Surface(
                shape = CircleShape,
                color =
                  if (ipReport?.isVerifiedUsa == true) OsTheme.extended.verifiedEmerald.copy(alpha = 0.16f)
                  else MaterialTheme.colorScheme.secondaryContainer,
                modifier =
                  Modifier.clip(CircleShape).clickable { onLaunchApp(VirtualAppId.DIAGNOSTICS) },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector =
                      if (ipReport?.isVerifiedUsa == true) Icons.Rounded.VerifiedUser
                      else Icons.Rounded.Public,
                    contentDescription = "Network check",
                    modifier = Modifier.size(16.dp),
                    tint =
                      if (ipReport?.isVerifiedUsa == true) OsTheme.extended.verifiedEmerald
                      else MaterialTheme.colorScheme.onSecondaryContainer,
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = ipReport?.let { "IP ${it.countryCode}" } ?: "Verify IP",
                    style = MaterialTheme.typography.labelMedium,
                    color =
                      if (ipReport?.isVerifiedUsa == true) OsTheme.extended.verifiedEmerald
                      else MaterialTheme.colorScheme.onSecondaryContainer,
                  )
                }
              }
            }

            if (nextEvent != null) {
              Spacer(modifier = Modifier.height(10.dp))
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onLaunchApp(VirtualAppId.CALENDAR) },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = "Upcoming event",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "${nextEvent.title} • ${nextEvent.timeLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Pill
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
          tonalElevation = 3.dp,
          modifier =
            Modifier.fillMaxWidth()
              .height(52.dp)
              .clip(CircleShape)
              .clickable(onClick = onOpenSearch)
              .testTag("home_search_pill"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = "Search",
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = stringResource(R.string.search_hint),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }

      // Home Screen App Grid
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 76.dp),
        modifier = Modifier.weight(1f).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        items(homeGridApps, key = { it.appId.id }) { spec ->
          VirtualAppIconTile(
            spec = spec,
            onClick = { onLaunchApp(spec.appId) },
            testTag = "home_app_${spec.appId.id}",
          )
        }
      }

      // Pixel Bottom Dock
      Surface(
        shape = RoundedCornerShape(28.dp),
        color = OsTheme.extended.dockSurface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          dockApps.forEach { spec ->
            VirtualAppIconTile(
              spec = spec,
              showLabel = false,
              onClick = { onLaunchApp(spec.appId) },
              testTag = "dock_app_${spec.appId.id}",
            )
          }

          // All Apps Drawer Launcher
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
              Modifier.clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onOpenDrawer)
                .padding(4.dp)
                .testTag("open_app_drawer_btn"),
          ) {
            Surface(
              modifier = Modifier.size(52.dp),
              shape = RoundedCornerShape(18.dp),
              color = MaterialTheme.colorScheme.primary,
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Apps,
                  contentDescription = "Open App Drawer",
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(28.dp),
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
fun AppDrawerOverlay(
  visible: Boolean,
  onDismiss: () -> Unit,
  onLaunchApp: (VirtualAppId) -> Unit,
) {
  if (visible) {
    BackHandler { onDismiss() }
  }

  AnimatedVisibility(
    visible = visible,
    enter =
      slideInVertically(
        initialOffsetY = { it / 3 },
        animationSpec = spring(
          stiffness = Spring.StiffnessMediumLow,
          dampingRatio = Spring.DampingRatioNoBouncy,
        ),
      ) + fadeIn(animationSpec = tween(200)),
    exit =
      slideOutVertically(
        targetOffsetY = { it / 3 },
        animationSpec = tween(180),
      ) + fadeOut(animationSpec = tween(160)),
  ) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    var inspectedSpec by remember { mutableStateOf<VirtualAppSpec?>(null) }

    val filtered =
      remember(query, selectedCategory) {
        VirtualAppCatalog.apps.filter { spec ->
          val matchesCat = selectedCategory == null || spec.category == selectedCategory
          val matchesQuery =
            query.isBlank() ||
              spec.title.contains(query, ignoreCase = true) ||
              spec.subtitle.contains(query, ignoreCase = true)
          matchesCat && matchesQuery
        }
      }

    Surface(
      modifier = Modifier.fillMaxSize().testTag("app_drawer_overlay"),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Text(
              text = "All Apps (16)",
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Long-press an app icon to inspect version & permissions",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_drawer_btn")) {
            Icon(Icons.Rounded.Close, contentDescription = "Close App Drawer")
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text(stringResource(R.string.drawer_search_hint)) },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.fillMaxWidth().testTag("drawer_search_input"),
        )

        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = selectedCategory == null,
              onClick = { selectedCategory = null },
              label = { Text("All") },
            )
          }
          items(AppCategory.entries) { category ->
            FilterChip(
              selected = selectedCategory == category,
              onClick = {
                selectedCategory = if (selectedCategory == category) null else category
              },
              label = { Text(category.label) },
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 82.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(filtered, key = { it.appId.id }) { spec ->
            VirtualAppIconTile(
              spec = spec,
              onClick = { onLaunchApp(spec.appId) },
              onLongClick = { inspectedSpec = spec },
              testTag = "drawer_app_${spec.appId.id}",
            )
          }
        }
      }
    }

    inspectedSpec?.let { spec ->
      AlertDialog(
        onDismissRequest = { inspectedSpec = null },
        title = { Text("${spec.title} • v${spec.version}") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(spec.subtitle, style = MaterialTheme.typography.bodyMedium)
            Text("Category: ${spec.category.label}", style = MaterialTheme.typography.bodySmall)
            Text("Footprint: ${spec.sizeLabel}", style = MaterialTheme.typography.bodySmall)
            Text("Permissions: ${spec.permissionsSummary}", style = MaterialTheme.typography.bodySmall)
          }
        },
        confirmButton = {
          Button(
            onClick = {
              inspectedSpec = null
              onLaunchApp(spec.appId)
            }
          ) { Text("Open App") }
        },
        dismissButton = {
          TextButton(onClick = { inspectedSpec = null }) { Text("Close") }
        },
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VirtualAppIconTile(
  spec: VirtualAppSpec,
  onClick: () -> Unit,
  testTag: String,
  showLabel: Boolean = true,
  onLongClick: (() -> Unit)? = null,
) {
  val containerColor =
    when (spec.accentIndex) {
      0 -> MaterialTheme.colorScheme.primaryContainer
      1 -> MaterialTheme.colorScheme.secondaryContainer
      2 -> MaterialTheme.colorScheme.tertiaryContainer
      else -> MaterialTheme.colorScheme.surfaceVariant
    }
  val contentColor =
    when (spec.accentIndex) {
      0 -> MaterialTheme.colorScheme.onPrimaryContainer
      1 -> MaterialTheme.colorScheme.onSecondaryContainer
      2 -> MaterialTheme.colorScheme.onTertiaryContainer
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      Modifier.clip(RoundedCornerShape(18.dp))
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(4.dp)
        .testTag(testTag),
  ) {
    Surface(
      modifier = Modifier.size(54.dp),
      shape = RoundedCornerShape(18.dp),
      color = containerColor,
      tonalElevation = 2.dp,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = spec.icon,
          contentDescription = spec.title,
          tint = contentColor,
          modifier = Modifier.size(28.dp),
        )
      }
    }
    if (showLabel) {
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = spec.title,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
  }
}
