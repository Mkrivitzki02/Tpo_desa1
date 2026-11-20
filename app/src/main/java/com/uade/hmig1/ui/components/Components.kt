package com.uade.hmig1.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.hmig1.domain.model.Alert
import com.uade.hmig1.domain.model.AlertSeverity
import com.uade.hmig1.domain.model.AlertStatus
import com.uade.hmig1.domain.model.CommandRecord
import com.uade.hmig1.domain.model.CommandStatus
import com.uade.hmig1.ui.theme.HmiColors
import com.uade.hmig1.ui.theme.clock

/** Estados visuales comunes a todos los componentes (ver hoja de estados en Figma). */
enum class VisualState { NORMAL, DISABLED, WARNING, ERROR }

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    trailing?.invoke()
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun StatusChip(text: String, state: VisualState, modifier: Modifier = Modifier) {
    val (bg, fg) = when (state) {
        VisualState.NORMAL -> HmiColors.OkSoft to HmiColors.Ok
        VisualState.DISABLED -> HmiColors.DisabledSoft to HmiColors.Muted
        VisualState.WARNING -> HmiColors.WarningSoft to HmiColors.WarningText
        VisualState.ERROR -> HmiColors.ErrorSoft to HmiColors.Error
    }
    Text(
        text = text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Dato de telemetría. Si [stale] es true se muestra en gris: no usar para decisiones operativas. */
@Composable
fun TelemetryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    state: VisualState = VisualState.NORMAL,
    stale: Boolean = false,
) {
    val color = when {
        stale -> HmiColors.Disabled
        state == VisualState.WARNING -> Color(0xFFC77700)
        state == VisualState.ERROR -> HmiColors.Error
        state == VisualState.DISABLED -> HmiColors.Disabled
        else -> HmiColors.Ink
    }
    Column(modifier.padding(vertical = 6.dp)) {
        Text(label, color = HmiColors.Muted, fontSize = 12.sp)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Botón de orden: muestra la condición de habilitación debajo.
 * [disabledReason] null = habilitado.
 */
@Composable
fun CommandButton(
    label: String,
    disabledReason: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    filled: Boolean = false,
) {
    val enabled = disabledReason == null
    Column(modifier) {
        if (filled) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (danger) HmiColors.Error else HmiColors.Primary,
                    disabledContainerColor = Color(0xFFD5DBE3),
                    disabledContentColor = Color(0xFF8C96A3),
                ),
            ) { Text(label, fontWeight = FontWeight.Bold) }
        } else {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, if (!enabled) HmiColors.DisabledSoft else if (danger) Color(0xFFE3A1A1) else Color(0xFFBFD0EA)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (enabled) Color.White else HmiColors.DisabledSoft,
                    contentColor = if (danger) HmiColors.Error else HmiColors.Primary,
                    disabledContentColor = HmiColors.Disabled,
                ),
            ) { Text(label, fontWeight = FontWeight.Bold) }
        }
        Text(
            text = disabledReason ?: "Disponible",
            fontSize = 11.sp,
            color = if (enabled) HmiColors.Muted else HmiColors.Disabled,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    }
}

@Composable
fun CommandStatusRow(record: CommandRecord, modifier: Modifier = Modifier) {
    val (color, soft, icon) = when (record.status) {
        CommandStatus.PENDING -> Triple(HmiColors.Primary, HmiColors.PrimarySoft, "…")
        CommandStatus.CONFIRMED -> Triple(HmiColors.Ok, HmiColors.OkSoft, "✓")
        CommandStatus.REJECTED -> Triple(HmiColors.Error, HmiColors.ErrorSoft, "✕")
        CommandStatus.NO_RESPONSE -> Triple(HmiColors.WarningText, HmiColors.WarningSoft, "!")
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.2.dp, color),
        color = Color.White,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(soft),
                contentAlignment = Alignment.Center,
            ) { Text(icon, color = color, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.command.type.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(record.updatedAt.clock(), fontSize = 11.sp, color = HmiColors.Muted)
                }
                Text(record.status.label.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                record.message?.let { Text(it, fontSize = 12.sp, color = HmiColors.Muted) }
            }
        }
    }
}

@Composable
fun AlertCard(
    alert: Alert,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null,
) {
    val resolved = alert.status != AlertStatus.ACTIVE
    val bar = when {
        alert.status == AlertStatus.RESOLVED -> HmiColors.Ok
        alert.status == AlertStatus.ACKNOWLEDGED -> HmiColors.Disabled
        alert.severity == AlertSeverity.CRITICAL -> HmiColors.Error
        else -> HmiColors.Warning
    }
    val chipState = when {
        alert.status == AlertStatus.RESOLVED -> VisualState.NORMAL
        alert.status == AlertStatus.ACKNOWLEDGED -> VisualState.DISABLED
        alert.severity == AlertSeverity.CRITICAL -> VisualState.ERROR
        else -> VisualState.WARNING
    }
    val chipText = when (alert.status) {
        AlertStatus.ACTIVE -> if (alert.severity == AlertSeverity.CRITICAL) "! CRÍTICA" else "! AVISO"
        AlertStatus.ACKNOWLEDGED -> "RECONOCIDA"
        AlertStatus.RESOLVED -> "✓ RESUELTA"
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(bar))
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(chipText, chipState)
                    Spacer(Modifier.weight(1f))
                    Text(alert.createdAt.clock(), fontSize = 11.sp, color = HmiColors.Muted)
                }
                Spacer(Modifier.height(6.dp))
                Text(alert.type.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    alert.message,
                    fontSize = 13.sp,
                    color = if (resolved) HmiColors.Muted else MaterialTheme.colorScheme.onSurface,
                )
                if (actions != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
                }
            }
        }
    }
}

@Composable
fun StaleTag() = StatusChip("DATOS OBSOLETOS", VisualState.WARNING)
