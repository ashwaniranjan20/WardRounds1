package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.preferences.AppThemeMode

private val WardRoundLightColorScheme = lightColorScheme(
    primary = DeepRoyalNavy,
    onPrimary = PureWhite,
    primaryContainer = PeriwinkleBlue.copy(alpha = 0.18f),
    onPrimaryContainer = DeepRoyalNavy,
    secondary = PeriwinkleBlue,
    onSecondary = DeepRoyalNavy, // High contrast text on secondary
    secondaryContainer = MutedSurface,
    onSecondaryContainer = PrimaryText,
    tertiary = ElectricLime,
    onTertiary = OnLimeText,     // High contrast dark navy on lime, NEVER white
    background = SoftCloudWhite,
    onBackground = PrimaryText,
    surface = PureWhite,
    onSurface = PrimaryText,
    surfaceVariant = MutedSurface,
    onSurfaceVariant = SecondaryText,
    outline = CardBorder,
    outlineVariant = CardBorder.copy(alpha = 0.6f),
    error = StatusAlert,
    onError = PureWhite
)

private val WardRoundDarkColorScheme = darkColorScheme(
    primary = DarkPrimaryBlue,         // #82A9FF
    onPrimary = Color(0xFF0A1838),      // Deep navy on light blue
    primaryContainer = Color(0xFF192C56),
    onPrimaryContainer = DarkSecondaryBlue, // #A9C3FF
    secondary = DarkSecondaryBlue,     // #A9C3FF
    onSecondary = Color(0xFF0B1B3C),
    secondaryContainer = DarkSecondaryBackground, // #171E2E
    onSecondaryContainer = DarkPrimaryText,
    tertiary = DarkLimeAccent,         // #C7EF66
    onTertiary = Color(0xFF0F2B00),     // Very dark green/navy on lime
    background = DarkBackground,       // #101522 (Main background)
    onBackground = DarkPrimaryText,    // #F4F6FC (Primary text)
    surface = DarkCardSurface,         // #1D273A (Cards / surfaces)
    onSurface = DarkPrimaryText,       // #F4F6FC
    surfaceVariant = DarkSecondaryBackground, // #171E2E
    onSurfaceVariant = DarkSecondaryText,     // #B9C3D6
    outline = DarkBorders,             // #344158 (Borders)
    outlineVariant = DarkBorders.copy(alpha = 0.7f),
    error = StatusAlertDark,
    onError = PureWhite
)

@Composable
fun WardRoundTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WardRoundDarkColorScheme else WardRoundLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward-compatible alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    WardRoundTheme(
        darkTheme = darkTheme,
        content = content
    )
}
