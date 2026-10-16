package de.letheapp.nocturn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Neon-Gelb-Akzent (statt des Material-You-Standardblaus) passend zum NocTurn-Branding.
private val NeonYellow = Color(0xFFD7FF00)

private val DarkColors = darkColorScheme(
    primary = NeonYellow,
    onPrimary = Color(0xFF1A1E00),
    primaryContainer = Color(0xFF333D00),
    onPrimaryContainer = NeonYellow,
    secondary = Color(0xFFCBD9A0),
    onSecondary = Color(0xFF1A1E00),
    secondaryContainer = Color(0xFF333D1A),
    onSecondaryContainer = Color(0xFFE2EFC0),
    tertiary = Color(0xFFB8F27A),
    onTertiary = Color(0xFF1A2400),
    tertiaryContainer = Color(0xFF2A3A10),
    onTertiaryContainer = Color(0xFFD4F2A8),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF161616),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF262621),
    onSurfaceVariant = Color(0xFFC9C9BE),
    outline = Color(0xFF8E8E82),
    outlineVariant = Color(0xFF454540)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B7A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8FF8F),
    onPrimaryContainer = Color(0xFF202400),
    secondary = Color(0xFF5E6B44),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3F0C2),
    onSecondaryContainer = Color(0xFF1B2200),
    tertiary = Color(0xFF3C6B2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBEF2A8),
    onTertiaryContainer = Color(0xFF0C2000)
)

@Composable
fun NocTurnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
