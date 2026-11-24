package com.uade.hmig1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uade.hmig1.domain.model.CommandType
import com.uade.hmig1.domain.model.OperationalState
import com.uade.hmig1.ui.components.StatusChip
import com.uade.hmig1.ui.components.VisualState
import com.uade.hmig1.ui.screens.AlertsScreen
import com.uade.hmig1.ui.screens.ControlScreen
import com.uade.hmig1.ui.screens.HistoryScreen
import com.uade.hmig1.ui.screens.MissionsScreen
import com.uade.hmig1.ui.screens.PanelScreen
import com.uade.hmig1.ui.screens.RobotSelectionScreen
import com.uade.hmig1.ui.screens.SimulatorScreen
import com.uade.hmig1.ui.theme.HmiColors

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("panel", "Panel", Icons.Filled.Home),
    Tab("misiones", "Misiones", Icons.AutoMirrored.Filled.List),
    Tab("control", "Control", Icons.Filled.Settings),
    Tab("alertas", "Alertas", Icons.Filled.Notifications),
    Tab("historial", "Historial", Icons.Filled.DateRange),
)

@Composable
fun HmiApp(vm: HmiViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    if (state.selectedRobotId == null) {
        RobotSelectionScreen(vm.robots, onSelect = vm::selectRobot)
    } else {
        MainScaffold(vm, state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: HmiViewModel, state: HmiUiState) {
    val nav = rememberNavController()
    val history by vm.history.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var confirm by remember { mutableStateOf<CommandType?>(null) }

    // Órdenes con confirmación obligatoria (riesgo: órdenes accidentales)
    val onCommand: (CommandType) -> Unit = { type ->
        // Reanudar después de una parada operativa exige un desbloqueo explícito.
        val unlock = type == CommandType.RESUME_MISSION && state.telemetry?.state == OperationalState.STOPPED
        if (type.requiresConfirmation || unlock) confirm = type else vm.send(type)
    }

    Scaffold(
        containerColor = HmiColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${state.selectedRobotId} · Asistente de planta", fontSize = 12.sp, color = HmiColors.Muted)
                        Text(titleFor(route), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::disconnectFromRobot) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cambiar de robot")
                    }
                },
                actions = {
                    StatusChip(
                        if (state.stale) "Sin conexión" else "● Conectado",
                        if (state.stale) VisualState.ERROR else VisualState.NORMAL,
                    )
                    IconButton(onClick = { nav.navigate("simulador") { launchSingleTop = true } }) {
                        Icon(Icons.Filled.Build, contentDescription = "Simulador de escenarios")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (state.stale) HmiColors.ErrorSoft else Color.White,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            // El Simulador no pertenece a ninguna pestaña: se cierra antes de cambiar,
                            // si no quedaría guardado dentro de la pestaña y volvería a aparecer.
                            if (route == "simulador") nav.popBackStack()
                            if (tab.route == "panel") {
                                nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false)
                            } else {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            if (tab.route == "alertas" && state.activeAlerts.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${state.activeAlerts.size}") } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label, fontSize = 11.sp) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.stale) DisconnectedBanner(state)
            NavHost(nav, startDestination = "panel", modifier = Modifier.weight(1f)) {
                composable("panel") { PanelScreen(state, onOpenAlerts = { nav.navigate("alertas") }) }
                composable("misiones") {
                    MissionsScreen(state, history, onStart = { code, dest ->
                        vm.send(CommandType.START_MISSION, patientCode = code, destination = dest)
                    })
                }
                composable("control") { ControlScreen(state, onCommand) }
                composable("alertas") { AlertsScreen(state, onAck = vm::acknowledge, onCommand = onCommand) }
                composable("historial") { HistoryScreen(history, onClear = vm::clearHistory) }
                composable("simulador") { SimulatorScreen(state, vm) }
            }
        }
    }

    confirm?.let { type ->
        ConfirmDialog(
            type = type,
            robotId = state.selectedRobotId ?: "",
            onConfirm = { vm.send(type); confirm = null },
            onDismiss = { confirm = null },
        )
    }
}

private fun titleFor(route: String?) = when (route) {
    "misiones" -> "Misiones"
    "control" -> "Control"
    "alertas" -> "Alertas"
    "historial" -> "Historial"
    "simulador" -> "Simulador"
    else -> "Panel de supervisión"
}

/** Desconexión: visible en todas las pantallas. */
@Composable
private fun DisconnectedBanner(state: HmiUiState) {
    val pending = state.pendingCommands.size
    Column(
        Modifier
            .fillMaxWidth()
            .background(HmiColors.Error)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text("Robot desconectado", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "Último contacto hace ${state.secondsSinceUpdate ?: "-"} s · datos marcados como obsoletos" +
                (if (pending > 0) " · $pending orden(es) sin confirmar" else "") +
                ". Reintentando automáticamente.",
            color = Color.White, fontSize = 12.sp,
        )
        Text("Ante un riesgo, usá la parada física de emergencia.", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConfirmDialog(type: CommandType, robotId: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, body, cta) = when (type) {
        CommandType.CANCEL_MISSION -> Triple(
            "¿Cancelar la misión?",
            "El robot abandona el acompañamiento y regresa a la base. Avisá al personal para que asista al paciente.",
            "Cancelar misión",
        )
        CommandType.RETURN_TO_SAFE_POINT -> Triple(
            "¿Volver al punto seguro?",
            "El robot deja la misión y regresa a Recepción. Asegurate de dejar al paciente con un miembro del personal.",
            "Confirmar regreso",
        )
        CommandType.OPERATIONAL_STOP -> Triple(
            "¿Detener $robotId?",
            "Solicita una parada controlada y suspende la misión. Revisá el robot antes de reanudar.\n\n" +
                "No es una parada física de emergencia.",
            "Confirmar parada",
        )
        CommandType.RESUME_MISSION -> Triple(
            "¿Desbloquear y reanudar?",
            "$robotId está en parada operativa. Confirmá que revisaste el robot y su entorno antes de que retome el recorrido.",
            "Desbloquear y reanudar",
        )
        else -> Triple(type.label, "¿Confirmás la orden?", "Confirmar")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (type == CommandType.RETURN_TO_SAFE_POINT || type == CommandType.RESUME_MISSION) HmiColors.Primary else HmiColors.Error,
                ),
            ) { Text(cta, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } },
    )
}
