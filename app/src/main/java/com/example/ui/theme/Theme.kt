package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val PixelShapes =
  Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

@Composable
fun UsaVirtualOsTheme(
  paletteKey: OsPaletteKey = OsPaletteKey.COBALT,
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = colorSchemeFor(paletteKey, darkTheme)
  val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors

  CompositionLocalProvider(LocalOsExtendedColors provides extended) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = PixelShapes,
      content = content,
    )
  }
}

object OsTheme {
  val extended: OsExtendedColors
    @Composable get() = LocalOsExtendedColors.current
}
