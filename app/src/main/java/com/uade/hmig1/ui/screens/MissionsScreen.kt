package com.uade.hmig1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.hmig1.domain.model.CommandType
import com.uade.hmig1.domain.model.Destination
import com.uade.hmig1.domain.model.Destinations
import com.uade.hmig1.domain.model.EventType
import com.uade.hmig1.domain.model.HistoryEvent
import com.uade.hmig1.ui.HmiUiState
import com.uade.hmig1.ui.components.CommandButton
import com.uade.hmig1.ui.components.SectionCard
import com.uade.hmig1.ui.components.StatusChip
import com.uade.hmig1.ui.components.VisualState
import com.uade.hmig1.ui.theme.HmiColors
import com.uade.hmig1.ui.theme.clock

private val PATIENT_CODE = Regex("^P-\\d{4}$")

@Composable
fun MissionsScreen(
    state: HmiUiState,
    history: List<HistoryEvent>,
    onStart: (patientCode: String, destination: Destination) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("P-") }
    var destId by rememberSaveable { mutableStateOf<String?>(null) }
    val dest = Destinations.all.firstOrNull { it.id == destId }
    val codeOk = PATIENT_CODE.matches(code)

    val ruleReason = state.disabledReason(CommandType.START_MISSION)
    val reason = ruleReason ?: when {
        !codeOk -> "Ingresá un código de paciente válido (P-0000)"
        dest == null -> "Elegí un destino"
        else -> null
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.telemetry?.mission?.let { m ->
            item {
                SectionCard(title = "Misión activa", trailing = { StatusChip("En curso", VisualState.NORMAL) }) {
                    Text("Acompañar a ${m.destination.name}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("${m.id} · Paciente ${m.patientCode}", color = HmiColors.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { m.progressPct / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${m.progressPct}% · inicio ${m.startedAt.clock()}", fontSize = 12.sp, color = HmiColors.Muted)
                }
            }
        }

        item {
            SectionCard(title = "Nueva misión") {
                Text(
                    "Identificá al paciente solo con su código interno: sin nombre, DNI ni diagnóstico.",
                    fontSize = 12.sp, color = HmiColors.Muted,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(6) },
                    label = { Text("Código de paciente") },
                    placeholder = { Text("P-2847") },
                    singleLine = true,
                    isError = code.length >= 6 && !codeOk,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Destino", fontWeight = FontWeight.SemiBold)
                Destinations.all.forEach { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = d.id == destId, onClick = { destId = d.id })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = d.id == destId, onClick = { destId = d.id })
                        Text("${d.name} · ${if (d.floor == 0) "PB" else "Piso ${d.floor}"}")
                    }
                }
                Spacer(Modifier.height(8.dp))
                CommandButton(
                    label = "Iniciar misión",
                    disabledReason = reason,
                    filled = true,
                    onClick = {
                        if (dest != null) {
                            onStart(code, dest)
                            code = "P-"
                            destId = null
                        }
                    },
                )
            }
        }

        val done = history.filter {
            it.type == EventType.MISSION_COMPLETED || it.type == EventType.MISSION_CANCELLED
        }.take(10)
        item {
            SectionCard(title = "Finalizadas recientemente") {
                if (done.isEmpty()) Text("Todavía no hay misiones finalizadas.", color = HmiColors.Muted)
                done.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (e.type == EventType.MISSION_COMPLETED) "✓" else "✕",
                            color = if (e.type == EventType.MISSION_COMPLETED) HmiColors.Ok else HmiColors.Error,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(e.detail, fontSize = 14.sp)
                            Text("${e.missionId ?: ""} · ${e.timestamp.clock()}", fontSize = 12.sp, color = HmiColors.Muted)
                        }
                    }
                }
            }
        }
    }
}
