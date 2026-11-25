package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class OsPaletteKey(val label: String) {
  COBALT("Pixel Cobalt"),
  MEADOW("Sage Meadow"),
  TERRACOTTA("Terracotta Dusk"),
  AMETHYST("Amethyst Studio"),
}

@Immutable
data class OsExtendedColors(
  val verifiedEmerald: Color,
  val onVerifiedEmerald: Color,
  val warningAmber: Color,
  val onWarningAmber: Color,
  val statusBarScrim: Color,
  val shellGlass: Color,
  val dockSurface: Color,
)

val LightExtendedColors =
  OsExtendedColors(
    verifiedEmerald = Color(0xFF0E7C4D),
    onVerifiedEmerald = Color(0xFFFFFFFF),
    warningAmber = Color(0xFFB45309),
    onWarningAmber = Color(0xFFFFFFFF),
    statusBarScrim = Color(0xCC0D1B2A),
    shellGlass = Color(0xE6F4F7FC),
    dockSurface = Color(0xD9E8EEF8),
  )

val DarkExtendedColors =
  OsExtendedColors(
    verifiedEmerald = Color(0xFF34D399),
    onVerifiedEmerald = Color(0xFF052E1C),
    warningAmber = Color(0xFFFBBF24),
    onWarningAmber = Color(0xFF331B00),
    statusBarScrim = Color(0xE609111E),
    shellGlass = Color(0xE6141C2B),
    dockSurface = Color(0xD91E293B),
  )

val LocalOsExtendedColors = staticCompositionLocalOf { LightExtendedColors }

private val CobaltLight =
  lightColorScheme(
    primary = Color(0xFF1A56DB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF475D8F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E2F8),
    onSecondaryContainer = Color(0xFF0B1C3D),
    tertiary = Color(0xFF0B7285),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC5F6FA),
    onTertiaryContainer = Color(0xFF002026),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF131B29),
    surface = Color(0xFFF6F8FC),
    onSurface = Color(0xFF131B29),
    surfaceVariant = Color(0xFFE2E8F4),
    onSurfaceVariant = Color(0xFF434C5E),
    outline = Color(0xFF737C90),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
  )

private val CobaltDark =
  darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E6E),
    primaryContainer = Color(0xFF0A43A8),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFB8C7E8),
    onSecondary = Color(0xFF213152),
    secondaryContainer = Color(0xFF36476B),
    onSecondaryContainer = Color(0xFFD9E2F8),
    tertiary = Color(0xFF7AD9EB),
    onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF004F5D),
    onTertiaryContainer = Color(0xFFC5F6FA),
    background = Color(0xFF0D131F),
    onBackground = Color(0xFFE3E8F3),
    surface = Color(0xFF121927),
    onSurface = Color(0xFFE3E8F3),
    surfaceVariant = Color(0xFF253044),
    onSurfaceVariant = Color(0xFFC1C9DB),
    outline = Color(0xFF8B94A8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
  )

private val MeadowLight =
  lightColorScheme(
    primary = Color(0xFF1F6E4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA7F3C9),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4B6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8D7),
    onSecondaryContainer = Color(0xFF092015),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEEAF7),
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFF4F9F5),
    onBackground = Color(0xFF161D19),
    surface = Color(0xFFF4F9F5),
    onSurface = Color(0xFF161D19),
    surfaceVariant = Color(0xFFDDE7DF),
    onSurfaceVariant = Color(0xFF404942),
    outline = Color(0xFF707971),
  )

private val MeadowDark =
  darkColorScheme(
    primary = Color(0xFF8CD6AE),
    onPrimary = Color(0xFF003823),
    primaryContainer = Color(0xFF055234),
    onPrimaryContainer = Color(0xFFA7F3C9),
    secondary = Color(0xFFB2CCBC),
    onSecondary = Color(0xFF1E3529),
    secondaryContainer = Color(0xFF344C3F),
    onSecondaryContainer = Color(0xFFCDE8D7),
    tertiary = Color(0xFFA3CEDB),
    onTertiary = Color(0xFF023640),
    tertiaryContainer = Color(0xFF224C58),
    onTertiaryContainer = Color(0xFFBEEAF7),
    background = Color(0xFF0E1511),
    onBackground = Color(0xFFDEE5DF),
    surface = Color(0xFF131B16),
    onSurface = Color(0xFFDEE5DF),
    surfaceVariant = Color(0xFF28352D),
    onSurfaceVariant = Color(0xFFBEC9C1),
    outline = Color(0xFF88938B),
  )

private val TerracottaLight =
  lightColorScheme(
    primary = Color(0xFF9C4221),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF380C00),
    secondary = Color(0xFF77574D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBD0),
    onSecondaryContainer = Color(0xFF2C160E),
    tertiary = Color(0xFF6A5E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4E2A7),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231916),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231916),
    surfaceVariant = Color(0xFFF5DDD6),
    onSurfaceVariant = Color(0xFF53433E),
    outline = Color(0xFF85736D),
  )

private val TerracottaDark =
  darkColorScheme(
    primary = Color(0xFFFFB59D),
    onPrimary = Color(0xFF5D1900),
    primaryContainer = Color(0xFF7D2C0D),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFFFDBD0),
    tertiary = Color(0xFFD7C68D),
    onTertiary = Color(0xFF3A3005),
    tertiaryContainer = Color(0xFF51471A),
    onTertiaryContainer = Color(0xFFF4E2A7),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF0DFDA),
    surface = Color(0xFF201512),
    onSurface = Color(0xFFF0DFDA),
    surfaceVariant = Color(0xFF3C2B26),
    onSurfaceVariant = Color(0xFFD8C2BB),
    outline = Color(0xFFA08C86),
  )

private val AmethystLight =
  lightColorScheme(
    primary = Color(0xFF6442A8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF21005E),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7E5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF31101D),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F),
  )

private val AmethystDark =
  darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF361077),
    primaryContainer = Color(0xFF4C298E),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF19171E),
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF302C39),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF948F99),
  )

fun colorSchemeFor(palette: OsPaletteKey, darkTheme: Boolean): ColorScheme =
  when (palette) {
    OsPaletteKey.COBALT -> if (darkTheme) CobaltDark else CobaltLight
    OsPaletteKey.MEADOW -> if (darkTheme) MeadowDark else MeadowLight
    OsPaletteKey.TERRACOTTA -> if (darkTheme) TerracottaDark else TerracottaLight
    OsPaletteKey.AMETHYST -> if (darkTheme) AmethystDark else AmethystLight
  }

// Note card semantic accents (light/dark aware)
@ComposableCompatMarker
fun noteCardTint(colorKey: String, darkTheme: Boolean): Color =
  when (colorKey) {
    "COBALT" -> if (darkTheme) Color(0xFF1B2B48) else Color(0xFFE1ECFF)
    "SAGE" -> if (darkTheme) Color(0xFF193226) else Color(0xFFDCF5E7)
    "AMBER" -> if (darkTheme) Color(0xFF362814) else Color(0xFFFFF1D6)
    "ROSE" -> if (darkTheme) Color(0xFF3A1E2A) else Color(0xFFFFE4EC)
    else -> if (darkTheme) Color(0xFF212633) else Color(0xFFF0F2F7)
  }

@Target(AnnotationTarget.FUNCTION)
annotation class ComposableCompatMarker
