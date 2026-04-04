package com.paysetu.app.Core.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.paysetu.app.home.SlateBlue

// 💎 REFINED MONOCHROME + EMERALD COLOR SCHEME
private val PremiumFintechColorScheme = darkColorScheme(
    primary = EmeraldGreen,        // 0xFF10B981 - The only accent
    onPrimary = Color(0xFF020617), // Deep black-navy for high contrast
    primaryContainer = EmeraldGreen.copy(alpha = 0.1f),
    onPrimaryContainer = EmeraldGreen,

    // 💡 Secondary is now a muted Slate to maintain the monochrome feel
    secondary = SlateBlue,         // 0xFF94A3B8
    onSecondary = Color.White,
    secondaryContainer = Color.White.copy(alpha = 0.05f),
    onSecondaryContainer = Color.White,

    background = Color(0xFF020617), // Deepest Base (Matches bottom of gradient)
    surface = Color(0xFF0F172A),    // Card Base (Matches top of gradient)

    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = SlateBlue,   // 0xFF94A3B8 - Unified muted labels

    error = Color(0xFFF43F5E),      // Rose Error
    onError = Color.White
)

@Composable
fun PaySetuTheme(
    content: @Composable () -> Unit
) {
    // 💡 FORCE PREMIUM DARK
    // High-end fintech apps maintain a single polished dark mode for security and prestige.
    val colorScheme = PremiumFintechColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // 💡 System Bar Integration: Set to Deepest Navy (0xFF020617)
            // This makes the screen feel "infinite" on OLED devices.
            val backgroundColor = colorScheme.background.toArgb()
            window.statusBarColor = backgroundColor
            window.navigationBarColor = backgroundColor

            // Ensure system icons (Clock, Battery) are White
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}