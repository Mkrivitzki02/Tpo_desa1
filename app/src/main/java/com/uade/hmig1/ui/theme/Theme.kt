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
// DateTimeFormatter es inmutable y thread-safe: se crea una vez y no en cada recomposición.
private val clockFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss", esAR)
private val dateTimeFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm", esAR)
private fun Long.local() = java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault())
fun Long.clock(): String = clockFmt.format(local())
fun Long.dateTime(): String = dateTimeFmt.format(local())
