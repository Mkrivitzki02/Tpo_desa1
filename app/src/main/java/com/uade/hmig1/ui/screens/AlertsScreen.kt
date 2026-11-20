package com.uade.hmig1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.hmig1.domain.model.Alert
import com.uade.hmig1.domain.model.AlertStatus
import com.uade.hmig1.domain.model.AlertType
import com.uade.hmig1.domain.model.CommandType
import com.uade.hmig1.ui.HmiUiState
import com.uade.hmig1.ui.components.AlertCard
import com.uade.hmig1.ui.theme.HmiColors

/**
 * Alertas activas con la acción que corresponde a cada una:
 * reconocer + decisión del operador (reanudar, volver a punto seguro, pausar).
 */
@Composable
fun AlertsScreen(state: HmiUiState, onAck: (Alert) -> Unit, onCommand: (CommandType) -> Unit) {
    val active = state.alerts.filter { it.status != AlertStatus.RESOLVED }
    val resolved = state.alerts.filter { it.status == AlertStatus.RESOLVED }.take(10)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Text("Alertas activas", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (state.stale) Text("Sin conexión: la lista puede estar desactualizada.", color = HmiColors.Error, fontSize = 12.sp)
            }
        }
        if (active.isEmpty()) item { Text("No hay alertas activas.", color = HmiColors.Muted) }
        items(active, key = { it.id }) { alert ->
            AlertCard(alert) {
                if (alert.status == AlertStatus.ACTIVE) {
                    OutlinedButton(onClick = { onAck(alert) }) { Text("Reconocer") }
                }
                val action = suggestedAction(alert.type)
                val reason = state.disabledReason(action)
                Button(
                    onClick = { onCommand(action) },
                    enabled = reason == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (alert.type == AlertType.BATTERY_LOW) HmiColors.Primary else HmiColors.Error,
                    ),
                ) { Text(actionLabel(alert.type)) }
            }
        }
        item { Text("Resueltas", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        if (resolved.isEmpty()) item { Text("Sin alertas resueltas en esta sesión.", color = HmiColors.Muted) }
        items(resolved, key = { "r-" + it.id }) { AlertCard(it) }
    }
}

private fun suggestedAction(type: AlertType) = when (type) {
    AlertType.BATTERY_LOW -> CommandType.RETURN_TO_SAFE_POINT
    AlertType.OBSTACLE -> CommandType.RESUME_MISSION
    AlertType.PATIENT_SEPARATION -> CommandType.RESUME_MISSION
}

private fun actionLabel(type: AlertType) = when (type) {
    AlertType.BATTERY_LOW -> "Volver a punto seguro"
    AlertType.OBSTACLE -> "Ruta alternativa"
    AlertType.PATIENT_SEPARATION -> "Paciente reubicado"
}
