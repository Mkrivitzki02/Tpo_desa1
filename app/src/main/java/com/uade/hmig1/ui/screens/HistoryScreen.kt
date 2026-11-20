package com.uade.hmig1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.hmig1.domain.model.EventType
import com.uade.hmig1.domain.model.HistoryEvent
import com.uade.hmig1.ui.components.SectionCard
import com.uade.hmig1.ui.components.TelemetryTile
import com.uade.hmig1.ui.components.VisualState
import com.uade.hmig1.ui.theme.HmiColors
import com.uade.hmig1.ui.theme.dateTime

/** Historial persistido en Room: sobrevive al cierre de la app. */
@Composable
fun HistoryScreen(events: List<HistoryEvent>, onClear: () -> Unit) {
    val completed = events.count { it.type == EventType.MISSION_COMPLETED }
    val cancelled = events.count { it.type == EventType.MISSION_CANCELLED }
    val alerts = events.count { it.type == EventType.ALERT }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionCard(title = "Resumen", trailing = { TextButton(onClick = onClear) { Text("Borrar") } }) {
                Row(Modifier.fillMaxWidth()) {
                    TelemetryTile("Completadas", "$completed", Modifier.weight(1f))
                    TelemetryTile("Canceladas", "$cancelled", Modifier.weight(1f),
                        state = if (cancelled > 0) VisualState.WARNING else VisualState.NORMAL)
                    TelemetryTile("Alertas", "$alerts", Modifier.weight(1f),
                        state = if (alerts > 0) VisualState.ERROR else VisualState.NORMAL)
                }
            }
        }
        item { Text("Cronología", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
        if (events.isEmpty()) item { Text("Sin eventos registrados.", color = HmiColors.Muted) }
        items(events, key = { it.id }) { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorFor(e)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (e.detail.isNotBlank()) Text(e.detail, fontSize = 12.sp, color = HmiColors.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(e.timestamp.dateTime(), fontSize = 11.sp, color = HmiColors.Muted)
                    e.missionId?.let { Text(it, fontSize = 11.sp, color = HmiColors.Muted) }
                }
            }
        }
    }
}

private fun colorFor(e: HistoryEvent) = when (e.type) {
    EventType.MISSION_COMPLETED -> HmiColors.Ok
    EventType.MISSION_STARTED -> HmiColors.Primary
    EventType.MISSION_CANCELLED -> HmiColors.Warning
    EventType.ALERT -> HmiColors.Error
    EventType.CONNECTION -> if (e.title.contains("perdida")) HmiColors.Error else HmiColors.Ok
    EventType.COMMAND -> when {
        e.title.contains("Confirmada") -> HmiColors.Ok
        e.title.contains("Rechazada") -> HmiColors.Error
        else -> HmiColors.Warning
    }
}
