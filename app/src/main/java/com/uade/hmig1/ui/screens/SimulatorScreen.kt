package com.uade.hmig1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.hmig1.domain.model.Connectivity
import com.uade.hmig1.domain.model.OperationalState
import com.uade.hmig1.domain.model.VoiceStatus
import com.uade.hmig1.ui.HmiViewModel
import com.uade.hmig1.ui.HmiUiState
import com.uade.hmig1.ui.components.SectionCard
import com.uade.hmig1.ui.theme.HmiColors

/**
 * Panel de simulación para la demo y la defensa.
 * No forma parte de la HMI real: reemplaza las situaciones que en el hospital ocurrirían solas.
 */
@Composable
fun SimulatorScreen(state: HmiUiState, vm: HmiViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Herramienta de demostración. Fuerza situaciones del robot simulado para mostrar los cinco escenarios.",
                fontSize = 13.sp, color = HmiColors.Muted,
            )
        }
        item {
            SectionCard(title = "Enlace con el robot") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (state.simLinkUp) "Conectado" else "Desconectado (forzado)", fontWeight = FontWeight.SemiBold)
                        Text("Escenario 3: datos antiguos, órdenes sin confirmar y recuperación.", fontSize = 12.sp, color = HmiColors.Muted)
                    }
                    Switch(checked = state.simLinkUp, onCheckedChange = { vm.simSetLink(it) })
                }
            }
        }
        item {
            SectionCard(title = "Señal WiFi") {
                val weak = state.telemetry?.connectivity == Connectivity.WEAK
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (weak) "Débil (forzada)" else "Fuerte", fontWeight = FontWeight.SemiBold)
                        Text("Zona con mala cobertura: el enlace sigue, pero con poco margen.", fontSize = 12.sp, color = HmiColors.Muted)
                    }
                    Switch(checked = !weak, onCheckedChange = { vm.simWeakSignal(!it) })
                }
            }
        }
        item {
            ScenarioCard("2 · Batería baja", "Lleva la batería a 18%: aparece la advertencia con la acción “Volver a punto seguro” y no se puede iniciar otra misión (≤ 20%).", "Forzar batería baja") { vm.simBatteryLow() }
        }
        item {
            ScenarioCard("4 · Incidente: obstáculo", "Durante el acompañamiento, un obstáculo persistente detiene al robot y exige una decisión.", "Generar obstáculo", accompanyingHint(state)) { vm.simObstacle() }
        }
        item {
            ScenarioCard("5 · Escenario propio: separación del paciente", "El paciente se aleja más de 2,0 m: pausa automática y alerta crítica.", "Paciente se aleja", accompanyingHint(state)) { vm.simPatientAway() }
        }
        item {
            SectionCard(title = "Sistema de voz") {
                val fault = state.telemetry?.voice == VoiceStatus.ERROR
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (fault) "Con falla (forzada)" else "Funcionando", fontWeight = FontWeight.SemiBold)
                        Text("Variable propia: muestra el estado Error del canal de voz.", fontSize = 12.sp, color = HmiColors.Muted)
                    }
                    Switch(checked = !fault, onCheckedChange = { vm.simVoiceFault(!it) })
                }
            }
        }
        item {
            ScenarioCard("Rechazo de orden", "La próxima orden enviada será rechazada por el robot.", "Rechazar próxima orden") { vm.simRejectNext() }
        }
        item {
            ScenarioCard("Reiniciar simulación", "Robot en base, batería 78%, sin alertas. El historial no se borra.", "Reiniciar") { vm.simReset() }
        }
        item {
            Text(
                "Escenario 1 (misión normal): iniciá una misión desde la pestaña Misiones y seguí el progreso en el Panel.",
                fontSize = 12.sp, color = HmiColors.Muted, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ScenarioCard(title: String, description: String, action: String, disabledReason: String? = null, onClick: () -> Unit) {
    SectionCard(title = title) {
        Text(description, fontSize = 13.sp, color = HmiColors.Muted)
        Spacer(Modifier.padding(4.dp))
        OutlinedButton(onClick = onClick, enabled = disabledReason == null, modifier = Modifier.fillMaxWidth()) { Text(action) }
        disabledReason?.let { Text(it, fontSize = 12.sp, color = HmiColors.WarningText) }
    }
}

/** Obstáculo y separación solo tienen sentido mientras el robot acompaña a un paciente. */
private fun accompanyingHint(state: HmiUiState): String? = when {
    state.stale -> "Sin conexión con el robot"
    state.telemetry?.state != OperationalState.ACCOMPANYING -> "Solo durante el acompañamiento: iniciá una misión primero"
    else -> null
}
