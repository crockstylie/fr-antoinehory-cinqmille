package fr.antoinehory.cinqmille.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Nouveau ColorScheme pour le thème Néon Rétro Futuriste
private val NeonColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = NeonDarkBackground,
    secondary = NeonMagenta,
    onSecondary = NeonDarkBackground,
    tertiary = NeonYellow,
    onTertiary = NeonDarkBackground,
    background = NeonDarkBackground,
    onBackground = NeonWhite,
    surface = NeonButtonBackground,
    onSurface = NeonWhite,
    error = NeonRed,
    onError = NeonDarkBackground,
    surfaceVariant = Color(0xFF1A1A3A),
    onSurfaceVariant = NeonWhite,
    outline = NeonCyan
)

// Le LofiCyberColorScheme a été supprimé.

private val DefaultLightColorScheme = lightColorScheme(
    primary = NeonCyan,
    onPrimary = NeonDarkBackground,
    secondary = NeonMagenta,
    onSecondary = NeonDarkBackground,
    background = Color(0xFFE0E0FF),
    onBackground = NeonDarkBackground,
    surface = Color(0xFFF0F0FF),
    onSurface = NeonDarkBackground,
    error = NeonRed,
    onError = Color.White
    // ... définir les autres couleurs pour un thème clair ...
)

@Composable
fun CinqMilleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> NeonColorScheme
        else -> DefaultLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

