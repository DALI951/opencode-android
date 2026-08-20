package ai.opencode.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
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

private val DarkColorScheme = darkColorScheme(
    primary = OcPrimaryDarkTheme,
    onPrimary = OcOnPrimaryContainer,
    primaryContainer = OcPrimaryDark,
    onPrimaryContainer = OcPrimaryContainer,
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
    background = OcBackgroundDark,
    onBackground = OcOnBackgroundDark,
    surface = OcSurfaceDark,
    onSurface = OcOnSurfaceDark,
    surfaceVariant = OcSurfaceVariantDark,
    onSurfaceVariant = OcOnSurfaceVariantDark,
    outline = OcOutlineDark,
    outlineVariant = OcOutlineVariantDark
)

@Composable
fun OpenCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
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
