package com.uade.hmig1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.hmig1.domain.model.AlertSeverity
import com.uade.hmig1.domain.model.OperationalState
import com.uade.hmig1.domain.model.VoiceStatus
import com.uade.hmig1.ui.HmiUiState
import com.uade.hmig1.ui.components.AlertCard
import com.uade.hmig1.ui.components.SectionCard
import com.uade.hmig1.ui.components.StaleTag
import com.uade.hmig1.ui.components.StatusChip
import com.uade.hmig1.ui.components.TelemetryTile
import com.uade.hmig1.ui.components.VisualState
import com.uade.hmig1.ui.theme.HmiColors
import com.uade.hmig1.ui.theme.fmt1

fun stateVisual(state: OperationalState): VisualState = when (state) {
    OperationalState.PAUSED -> VisualState.WARNING
    OperationalState.STOPPED -> VisualState.ERROR
    OperationalState.AT_BASE -> VisualState.DISABLED
    else -> VisualState.NORMAL
}

/** Panel de supervisión: telemetría estándar + 3 variables del servicio. */
@Composable
fun PanelScreen(state: HmiUiState, onOpenAlerts: () -> Unit) {
    val t = state.telemetry
    if (t == null) {
        Text("Esperando telemetría del robot…", Modifier.fillMaxSize(), color = HmiColors.Muted)
        return
    }
    val stale = state.stale
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Alerta crítica más reciente arriba de todo
        state.activeAlerts.firstOrNull { it.severity == AlertSeverity.CRITICAL }?.let { a ->
            item {
                AlertCard(a) { TextButton(onClick = onOpenAlerts) { Text("Ver alertas →") } }
            }
        }

        item {
            SectionCard(title = "Estado del robot", trailing = {
                if (stale) StaleTag() else StatusChip(t.state.label, stateVisual(t.state))
            }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        if (stale) "Sin conexión" else "● ${t.connectivity.label}",
                        if (stale) VisualState.ERROR else VisualState.NORMAL,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Última actualización: hace ${state.secondsSinceUpdate ?: "-"} s",
                        fontSize = 12.sp,
                        color = if (stale) HmiColors.Error else HmiColors.Muted,
                    )
                }
                t.pausedReason?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = HmiColors.WarningText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            SectionCard(title = "Misión") {
                val m = t.mission
                if (m == null) {
                    Text(
                        if (t.state == OperationalState.RETURNING) "Sin misión · regresando a la base" else "Sin misión activa",
                        color = HmiColors.Muted,
                    )
                } else {
                    Text("${m.id} · Acompañar a ${m.destination.name}", fontWeight = FontWeight.Bold)
                    Text("Paciente ${m.patientCode}", color = HmiColors.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { m.progressPct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (stale) HmiColors.Disabled else HmiColors.Primary,
                    )
                    Text("${m.progressPct}% completado", fontSize = 12.sp, color = HmiColors.Muted)
                }
            }
        }

        item {
            SectionCard(title = "Telemetría") {
                Row(Modifier.fillMaxWidth()) {
                    TelemetryTile(
                        "Batería", "${t.batteryPct.toInt()}%", Modifier.weight(1f),
                        state = when {
                            t.batteryPct < 15 -> VisualState.ERROR
                            t.batteryPct < 25 -> VisualState.WARNING
                            else -> VisualState.NORMAL
                        },
                        stale = stale,
                    )
                    TelemetryTile(
                        "WiFi", if (stale) "Sin señal" else "${t.wifiDbm} dBm", Modifier.weight(1f),
                        state = if (stale) VisualState.ERROR else VisualState.NORMAL,
                    )
                    TelemetryTile("Estado", t.state.label, Modifier.weight(1f), stale = stale)
                }
                Row(Modifier.fillMaxWidth()) {
                    TelemetryTile("Ubicación", "${t.zone} · ${if (t.floor == 0) "PB" else "Piso ${t.floor}"}", Modifier.weight(2f), stale = stale)
                    TelemetryTile("Movimiento", "${t.speedMps.fmt1()} m/s", Modifier.weight(1f), stale = stale)
                }
                Row(Modifier.fillMaxWidth()) {
                    TelemetryTile(
                        "Alertas activas", "${state.activeAlerts.size}", Modifier.weight(1f),
                        state = if (state.activeAlerts.isEmpty()) VisualState.NORMAL else VisualState.ERROR,
                    )
                    TelemetryTile("Robot", t.robotId, Modifier.weight(1f))
                }
                if (stale) {
                    Text("Sin actualizar · no usar para decisiones operativas.", fontSize = 12.sp, color = HmiColors.WarningText)
                }
            }
        }

        item {
            SectionCard(title = "Variables del servicio") {
                Row(Modifier.fillMaxWidth()) {
                    val d = t.patientDistanceM
                    TelemetryTile(
                        "Dist. al paciente", d?.let { "${it.fmt1()} m" } ?: "—", Modifier.weight(1f),
                        state = when {
                            d == null -> VisualState.DISABLED
                            d > 2.0 -> VisualState.ERROR
                            d > 1.5 -> VisualState.WARNING
                            else -> VisualState.NORMAL
                        },
                        stale = stale,
                    )
                    TelemetryTile("Acompañamiento", t.accompaniment.label, Modifier.weight(1.4f), stale = stale)
                    TelemetryTile(
                        "Voz", t.voice.label, Modifier.weight(0.8f),
                        state = when (t.voice) {
                            VoiceStatus.ERROR -> VisualState.ERROR
                            VoiceStatus.INACTIVE -> VisualState.DISABLED
                            VoiceStatus.ACTIVE -> VisualState.NORMAL
                        },
                        stale = stale,
                    )
                }
                Text("Límite de separación: 2,0 m", fontSize = 12.sp, color = HmiColors.Muted)
            }
        }
        item { Column { Spacer(Modifier.height(8.dp)) } }
    }
}
