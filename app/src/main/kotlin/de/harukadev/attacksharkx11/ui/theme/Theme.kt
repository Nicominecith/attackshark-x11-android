package de.harukadev.attacksharkx11.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SharkOrange = Color(0xFFFF7A3D)
val SharkOrangeDark = Color(0xFFE8431F)
val SharkBackground = Color(0xFF121212)
val SharkSurface = Color(0xFF1C1C1C)
val SharkSurfaceVariant = Color(0xFF262626)

private val SharkColorScheme = darkColorScheme(
    primary = SharkOrange,
    onPrimary = Color(0xFF2A0A00),
    secondary = SharkOrangeDark,
    background = SharkBackground,
    surface = SharkSurface,
    surfaceVariant = SharkSurfaceVariant,
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED),
)

@Composable
fun AttackSharkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SharkColorScheme,
        content = content,
    )
}
