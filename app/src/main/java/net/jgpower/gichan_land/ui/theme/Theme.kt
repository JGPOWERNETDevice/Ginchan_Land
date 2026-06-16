package net.jgpower.gichan_land.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = AppSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E7EB),
    onSecondaryContainer = Color(0xFF111827),
    background = AppBackground,
    onBackground = AppOnBackground,
    surface = AppSurface,
    onSurface = AppOnSurface,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = AppOnSurfaceVariant,
    outline = AppOutline,
    outlineVariant = AppOutlineVariant,
    error = AppError,
    onError = Color.White
)

@Composable
fun Gichan_LandTheme(
    // Force the app to the fixed light color scheme. Some One UI 8.5 builds can
    // report/apply dark dynamic colors while the screen background remains light,
    // making OutlinedButton text look very pale.
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
