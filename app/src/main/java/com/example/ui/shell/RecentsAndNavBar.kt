package com.example.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.VirtualAppCatalog
import com.example.domain.VirtualAppId

@Composable
fun VirtualNavigationBar(
  recentsCount: Int,
  onBack: () -> Unit,
  onHome: () -> Unit,
  onRecents: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f),
    tonalElevation = 4.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .height(52.dp)
          .padding(horizontal = 24.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.size(48.dp).testTag("virtual_nav_back"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = stringResource(R.string.nav_back_desc),
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }

      IconButton(
        onClick = onHome,
        modifier = Modifier.size(48.dp).testTag("virtual_nav_home"),
      ) {
        Icon(
          imageVector = Icons.Rounded.RadioButtonChecked,
          contentDescription = stringResource(R.string.nav_home_desc),
          tint = MaterialTheme.colorScheme.primary,
        )
      }

      IconButton(
        onClick = onRecents,
        modifier = Modifier.size(48.dp).testTag("virtual_nav_recents"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.CropSquare,
            contentDescription = stringResource(R.string.nav_recents_desc),
            tint = MaterialTheme.colorScheme.onSurface,
          )
          if (recentsCount > 0) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.align(Alignment.TopEnd).size(15.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = recentsCount.coerceAtMost(9).toString(),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimary,
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
fun RecentsSwitcherOverlay(
  visible: Boolean,
  recentsStack: List<VirtualAppId>,
  onResumeApp: (VirtualAppId) -> Unit,
  onDismissApp: (VirtualAppId) -> Unit,
  onClearAll: () -> Unit,
  onCloseOverlay: () -> Unit,
) {
  if (visible) {
    BackHandler { onCloseOverlay() }
  }

  AnimatedVisibility(
    visible = visible,
    enter =
      scaleIn(
        initialScale = 0.94f,
        animationSpec = spring(
          stiffness = Spring.StiffnessMediumLow,
          dampingRatio = Spring.DampingRatioNoBouncy,
        ),
      ) + fadeIn(animationSpec = tween(180)),
    exit = scaleOut(targetScale = 0.96f, animationSpec = tween(150)) + fadeOut(animationSpec = tween(150)),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
          .clickable(onClick = onCloseOverlay)
          .testTag("recents_overlay")
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "Recent Apps",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )

        if (recentsStack.isEmpty()) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
          ) {
            Text(
              text = "No recent apps running",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Open any app from the Home Screen or Drawer to multitask here.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            items(recentsStack, key = { it.id }) { appId ->
              val spec = VirtualAppCatalog.findById(appId)
              Card(
                shape = RoundedCornerShape(28.dp),
                colors =
                  CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                  ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier =
                  Modifier.width(236.dp)
                    .height(340.dp)
                    .clickable { onResumeApp(appId) }
                    .testTag("recent_card_${appId.id}"),
              ) {
                Column(
                  modifier = Modifier.fillMaxSize().padding(18.dp),
                  verticalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = spec.icon,
                        contentDescription = spec.title,
                        tint = MaterialTheme.colorScheme.primary,
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = spec.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    IconButton(onClick = { onDismissApp(appId) }) {
                      Icon(Icons.Rounded.Close, contentDescription = "Close ${spec.title}")
                    }
                  }

                  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                      shape = RoundedCornerShape(24.dp),
                      color = MaterialTheme.colorScheme.primaryContainer,
                      modifier = Modifier.size(72.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = spec.icon,
                          contentDescription = null,
                          modifier = Modifier.size(38.dp),
                          tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                      }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                      text = spec.subtitle,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }

                  Button(
                    onClick = { onResumeApp(appId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                  ) {
                    Text("Resume")
                  }
                }
              }
            }
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          if (recentsStack.isNotEmpty()) {
            OutlinedButton(
              onClick = onClearAll,
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier.testTag("clear_all_recents_btn"),
            ) {
              Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Clear all")
            }
          }
          Button(
            onClick = onCloseOverlay,
            shape = RoundedCornerShape(20.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
          ) {
            Text("Done")
          }
        }
      }
    }
  }
}
