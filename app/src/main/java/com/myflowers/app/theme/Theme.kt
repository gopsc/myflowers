package com.myflowers.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Sage,
    onPrimaryContainer = Color(0xFF153E2D),
    secondary = Color(0xFF586F52),
    secondaryContainer = Color(0xFFECF0E3),
    onSecondaryContainer = Ink,
    tertiary = Terracotta,
    tertiaryContainer = Color(0xFFF8E3D4),
    onTertiaryContainer = Color(0xFF61371F),
    background = Cream,
    onBackground = Ink,
    surface = Color(0xFFFEFEFA),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF0E7),
    onSurfaceVariant = Color(0xFF657064),
    outline = Color(0xFF7A8576),
    outlineVariant = Color(0xFFDDE3D6),
    error = Color(0xFFAC3B35),
    errorContainer = Color(0xFFFBE9E5),
    onErrorContainer = Color(0xFF782923)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6D3AC),
    onPrimary = Color(0xFF173B28),
    primaryContainer = Color(0xFF254F37),
    onPrimaryContainer = Color(0xFFD5EED5),
    secondary = Color(0xFFBACBB0),
    secondaryContainer = Color(0xFF354532),
    onSecondaryContainer = Color(0xFFE2EADB),
    tertiary = Color(0xFFEAB08E),
    tertiaryContainer = Color(0xFF594034),
    onTertiaryContainer = Color(0xFFFFDBBC),
    background = Color(0xFF121B16),
    onBackground = Color(0xFFE3E9DE),
    surface = Color(0xFF1C2720),
    onSurface = Color(0xFFE3E9DE),
    surfaceVariant = Color(0xFF29372C),
    onSurfaceVariant = Color(0xFFB3BEAF),
    outlineVariant = Color(0xFF3C4C3E)
)

@Composable
fun MyFlowersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}
