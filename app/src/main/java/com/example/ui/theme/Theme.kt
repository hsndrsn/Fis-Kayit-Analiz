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

private val DarkColorScheme = darkColorScheme(
    primary = GeoPrimaryDark,
    onPrimary = GeoOnPrimaryDark,
    primaryContainer = GeoPrimaryContainerDark,
    onPrimaryContainer = GeoOnPrimaryContainerDark,
    background = GeoNeutralDark,
    onBackground = GeoOnNeutralDark,
    surface = GeoSurfaceDark,
    onSurface = GeoOnSurfaceDark,
    secondaryContainer = GeoSecondaryContainerDark,
    outline = GeoOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = GeoPrimaryLight,
    onPrimary = GeoOnPrimaryLight,
    primaryContainer = GeoPrimaryContainerLight,
    onPrimaryContainer = GeoOnPrimaryContainerLight,
    background = GeoNeutralLight,
    onBackground = GeoOnNeutralLight,
    surface = GeoSurfaceLight,
    onSurface = GeoOnSurfaceLight,
    secondaryContainer = GeoSecondaryContainerLight,
    outline = GeoOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to enforce our custom themed colors consistently
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
