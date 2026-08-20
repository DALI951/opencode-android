package ai.opencode.android.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OcDarkColorScheme = darkColorScheme(
    primary = OcPrimary,
    onPrimary = OcOnPrimary,
    primaryContainer = OcPrimaryContainer,
    onPrimaryContainer = OcOnPrimaryContainer,
    secondary = OcSecondary,
    onSecondary = OcOnSecondary,
    secondaryContainer = OcSecondaryContainer,
    onSecondaryContainer = OcOnSecondaryContainer,
    tertiary = OcTertiary,
    onTertiary = OcOnTertiary,
    error = OcError,
    onError = OcOnError,
    errorContainer = OcErrorContainer,
    onErrorContainer = OcOnErrorContainer,
    background = OcBackground,
    onBackground = OcOnBackground,
    surface = OcSurface,
    onSurface = OcOnSurface,
    surfaceVariant = OcSurfaceVariant,
    onSurfaceVariant = OcOnSurfaceVariant,
    outline = OcOutline,
    outlineVariant = OcOutlineVariant
)

@Composable
fun OpenCodeTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = OcDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OcTypography,
        shapes = OcShapes,
        content = content
    )
}
