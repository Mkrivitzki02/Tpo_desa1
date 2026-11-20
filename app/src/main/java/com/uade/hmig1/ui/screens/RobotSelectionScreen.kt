package com.uade.hmig1.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uade.hmig1.domain.model.RobotAvailability
import com.uade.hmig1.domain.model.RobotSummary
import com.uade.hmig1.ui.components.StatusChip
import com.uade.hmig1.ui.components.VisualState
import com.uade.hmig1.ui.theme.HmiColors

@Composable
fun RobotSelectionScreen(robots: List<RobotSummary>, onSelect: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("G1-01") }
    Column(
        Modifier
            .fillMaxSize()
            .background(HmiColors.Background)
            .systemBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("RoboAsist · Flota", color = HmiColors.Muted, fontSize = 13.sp)
        Text("Selección de robot", fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Text("Flota hospitalaria · ${robots.size} unidades Unitree G1 (simuladas)", color = HmiColors.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        robots.forEach { r ->
            val available = r.availability == RobotAvailability.AVAILABLE
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(if (r.id == selected) 2.dp else 0.dp, if (r.id == selected) HmiColors.Primary else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = available) { selected = r.id },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.id, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                                color = if (available) HmiColors.Ink else HmiColors.Disabled)
                            Text(r.role, color = HmiColors.Muted, fontSize = 13.sp)
                        }
                        StatusChip(
                            r.availability.label,
                            when (r.availability) {
                                RobotAvailability.AVAILABLE -> VisualState.NORMAL
                                RobotAvailability.IN_MISSION -> VisualState.WARNING
                                RobotAvailability.OFFLINE -> VisualState.DISABLED
                            },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(r.location, fontSize = 12.sp, color = HmiColors.Muted, modifier = Modifier.weight(1f))
                        Text(r.batteryPct?.let { "$it% de batería" } ?: "—", fontSize = 12.sp)
                    }
                }
            }
        }
        Text(
            "Red hospitalaria segura · solo se pueden seleccionar robots disponibles.",
            fontSize = 12.sp, color = HmiColors.Muted,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onSelect(selected) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Seleccionar $selected  →", fontWeight = FontWeight.Bold) }
    }
}
