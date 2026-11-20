package com.uade.hmig1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.util.Locale

/** Misma paleta que la hoja "00 · Estados visuales" del Figma. */
object HmiColors {
    val Primary = Color(0xFF1E63C4)
    val PrimarySoft = Color(0xFFE8F0FB)
    val Ok = Color(0xFF1E7A45)
    val OkSoft = Color(0xFFE3F4EA)
    val Warning = Color(0xFFE8A33D)
    val WarningText = Color(0xFF8A5300)
    val WarningSoft = Color(0xFFFFF1D6)
    val Error = Color(0xFFB3261E)
    val ErrorSoft = Color(0xFFFCE4E4)
    val Disabled = Color(0xFF9AA3AE)
    val DisabledSoft = Color(0xFFE4E8EE)
    val Background = Color(0xFFEEF2F7)
    val Ink = Color(0xFF14213D)
    val Muted = Color(0xFF6B7685)
}

private val scheme = lightColorScheme(
    primary = HmiColors.Primary,
    onPrimary = Color.White,
    error = HmiColors.Error,
    background = HmiColors.Background,
    surface = Color.White,
    onSurface = HmiColors.Ink,
    onBackground = HmiColors.Ink,
    surfaceContainer = Color.White,
)

@Composable
fun HmiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

private val esAR: Locale = Locale.forLanguageTag("es-AR")
fun Double.fmt1(): String = String.format(esAR, "%.1f", this)
fun Long.clock(): String = java.text.SimpleDateFormat("HH:mm:ss", esAR).format(java.util.Date(this))
fun Long.dateTime(): String = java.text.SimpleDateFormat("dd/MM HH:mm", esAR).format(java.util.Date(this))
