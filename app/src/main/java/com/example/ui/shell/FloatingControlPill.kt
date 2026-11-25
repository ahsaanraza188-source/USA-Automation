package com.example.ui.shell

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private tailrec fun Context.findHostActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
  }

@Composable
fun FloatingControlPill(
  onBack: () -> Unit,
  onHome: () -> Unit,
  onStatusMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  val audioManager =
    remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

  var isExpanded by remember { mutableStateOf(true) }
  var offsetY by remember { mutableFloatStateOf(0f) }
  var isMuted by remember(audioManager) {
    mutableStateOf(runCatching { audioManager.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(false))
  }

  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

  Box(
    modifier =
      modifier
        .offset { IntOffset(0, offsetY.roundToInt()) }
        .padding(end = 8.dp)
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            offsetY = (offsetY + dragAmount.y).coerceIn(-420f, 420f)
          }
        },
    contentAlignment = Alignment.CenterEnd,
  ) {
    if (!isExpanded) {
      Surface(
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 8.dp, bottomEnd = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 6.dp,
      ) {
        IconButton(
          onClick = { isExpanded = true },
          modifier = Modifier.size(48.dp).testTag("side_pill_toggle"),
        ) {
          Icon(
            imageVector = Icons.Rounded.ChevronLeft,
            contentDescription = "Expand control toolbar",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    } else {
      AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
      ) {
        Surface(
          shape = RoundedCornerShape(28.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
          shadowElevation = 8.dp,
          tonalElevation = 6.dp,
        ) {
          Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.size(48.dp).testTag("side_pill_back"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Floating Back",
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }

            IconButton(
              onClick = onHome,
              modifier = Modifier.size(48.dp).testTag("side_pill_home"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Home,
                contentDescription = "Floating Home",
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }

            IconButton(
              onClick = {
                val activity = context.findHostActivity()
                if (activity != null) {
                  val targetOrientation =
                    if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                  activity.requestedOrientation = targetOrientation
                  onStatusMessage(
                    if (isLandscape) "Rotated to Portrait" else "Rotated to Landscape"
                  )
                }
              },
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor =
                    if (isLandscape) MaterialTheme.colorScheme.primaryContainer
                    else androidx.compose.ui.graphics.Color.Transparent,
                  contentColor =
                    if (isLandscape) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                ),
              modifier = Modifier.size(48.dp).testTag("side_pill_rotate"),
            ) {
              Icon(
                imageVector = Icons.Rounded.ScreenRotation,
                contentDescription = "Rotate screen",
              )
            }

            HorizontalDivider(
              modifier = Modifier.size(width = 24.dp, height = 1.dp),
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )

            IconButton(
              onClick = {
                audioManager.adjustStreamVolume(
                  AudioManager.STREAM_MUSIC,
                  AudioManager.ADJUST_RAISE,
                  AudioManager.FLAG_SHOW_UI,
                )
                isMuted =
                  runCatching { audioManager.isStreamMute(AudioManager.STREAM_MUSIC) }
                    .getOrDefault(false)
              },
              modifier = Modifier.size(48.dp).testTag("side_pill_vol_up"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = "Volume Up",
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }

            IconButton(
              onClick = {
                audioManager.adjustStreamVolume(
                  AudioManager.STREAM_MUSIC,
                  AudioManager.ADJUST_LOWER,
                  AudioManager.FLAG_SHOW_UI,
                )
                isMuted =
                  runCatching { audioManager.isStreamMute(AudioManager.STREAM_MUSIC) }
                    .getOrDefault(false)
              },
              modifier = Modifier.size(48.dp).testTag("side_pill_vol_down"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeDown,
                contentDescription = "Volume Down",
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }

            IconButton(
              onClick = {
                audioManager.adjustStreamVolume(
                  AudioManager.STREAM_MUSIC,
                  AudioManager.ADJUST_TOGGLE_MUTE,
                  AudioManager.FLAG_SHOW_UI,
                )
                isMuted =
                  runCatching { audioManager.isStreamMute(AudioManager.STREAM_MUSIC) }
                    .getOrDefault(!isMuted)
                onStatusMessage(if (isMuted) "Audio muted" else "Audio unmuted")
              },
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor =
                    if (isMuted) MaterialTheme.colorScheme.errorContainer
                    else androidx.compose.ui.graphics.Color.Transparent,
                  contentColor =
                    if (isMuted) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface,
                ),
              modifier = Modifier.size(48.dp).testTag("side_pill_mute"),
            ) {
              Icon(
                imageVector =
                  if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff
                  else Icons.AutoMirrored.Rounded.VolumeMute,
                contentDescription = if (isMuted) "Unmute audio" else "Mute audio",
              )
            }

            HorizontalDivider(
              modifier = Modifier.size(width = 24.dp, height = 1.dp),
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )

            IconButton(
              onClick = { isExpanded = false },
              modifier = Modifier.size(48.dp).testTag("side_pill_toggle"),
            ) {
              Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Collapse control toolbar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
