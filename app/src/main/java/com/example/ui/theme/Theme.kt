package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PolishedPrimaryDark,
    onPrimary = PolishedOnPrimaryDark,
    primaryContainer = PolishedPrimaryContainerDark,
    onPrimaryContainer = PolishedOnPrimaryContainerDark,
    secondary = PolishedSecondaryDark,
    background = PolishedBackgroundDark,
    surface = PolishedSurfaceDark,
    surfaceVariant = PolishedSurfaceVariantDark,
    outline = PolishedOutlineDark,
    onBackground = PolishedOnBackgroundDark,
    onSurface = PolishedOnSurfaceDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PolishedPrimaryLight,
    onPrimary = PolishedOnPrimaryLight,
    primaryContainer = PolishedPrimaryContainerLight,
    onPrimaryContainer = PolishedOnPrimaryContainerLight,
    secondary = PolishedSecondaryLight,
    background = PolishedBackgroundLight,
    surface = PolishedSurfaceLight,
    surfaceVariant = PolishedSurfaceVariantLight,
    outline = PolishedOutlineLight,
    onBackground = PolishedOnBackgroundLight,
    onSurface = PolishedOnSurfaceLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Override this to keep the custom palette consistent
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
