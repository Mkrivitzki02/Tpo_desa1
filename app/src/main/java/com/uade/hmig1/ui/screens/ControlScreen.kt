package com.uade.hmig1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.hmig1.domain.model.CommandType
import com.uade.hmig1.ui.HmiUiState
import com.uade.hmig1.ui.components.CommandButton
import com.uade.hmig1.ui.components.CommandStatusRow
import com.uade.hmig1.ui.components.SectionCard
import com.uade.hmig1.ui.components.StaleTag
import com.uade.hmig1.ui.components.StatusChip
import com.uade.hmig1.ui.theme.HmiColors

@Composable
fun ControlScreen(state: HmiUiState, onCommand: (CommandType) -> Unit) {
    val t = state.telemetry
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(title = t?.robotId ?: "Robot", trailing = {
                if (state.stale) StaleTag() else t?.let { StatusChip(it.state.label, stateVisual(it.state)) }
            }) {
                Text(
                    t?.mission?.let { "Acompañar a ${it.destination.name} · ${it.id}" } ?: "Sin misión activa",
                    color = HmiColors.Muted, fontSize = 13.sp,
                )
                if (state.stale) {
                    Text(
                        "Sin conexión: las órdenes quedarán pendientes y pueden no llegar al robot.",
                        color = HmiColors.Error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        item {
            SectionCard(title = "Órdenes de misión") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CommandButton("Pausar", state.disabledReason(CommandType.PAUSE_MISSION),
                        { onCommand(CommandType.PAUSE_MISSION) }, Modifier.weight(1f))
                    CommandButton("Reanudar", state.disabledReason(CommandType.RESUME_MISSION),
                        { onCommand(CommandType.RESUME_MISSION) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                CommandButton("Cancelar misión", state.disabledReason(CommandType.CANCEL_MISSION),
                    { onCommand(CommandType.CANCEL_MISSION) }, danger = true)
                Text("Para iniciar una misión usá la pestaña Misiones.", fontSize = 12.sp, color = HmiColors.Muted)
            }
        }

        item {
            SectionCard(title = "Órdenes de seguridad") {
                CommandButton("Volver a punto seguro", state.disabledReason(CommandType.RETURN_TO_SAFE_POINT),
                    { onCommand(CommandType.RETURN_TO_SAFE_POINT) }, filled = true)
                Spacer(Modifier.height(10.dp))
                CommandButton("■  Parada operativa", state.disabledReason(CommandType.OPERATIONAL_STOP),
                    { onCommand(CommandType.OPERATIONAL_STOP) }, filled = true, danger = true)
                Spacer(Modifier.height(10.dp))
                Text(
                    "RESPONSABILIDAD DEL OPERADOR\nLa parada operativa es un comando de software simulado: " +
                        "no sustituye la parada física de emergencia. Una orden enviada no equivale a una orden ejecutada.",
                    fontSize = 12.sp,
                    color = HmiColors.Muted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HmiColors.Background)
                        .padding(10.dp),
                )
            }
        }

        item { Text("Órdenes enviadas", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        if (state.commands.isEmpty()) {
            item { Text("Todavía no se enviaron órdenes.", color = HmiColors.Muted) }
        }
        items(state.commands.take(8), key = { it.command.id }) { CommandStatusRow(it) }
    }
}
