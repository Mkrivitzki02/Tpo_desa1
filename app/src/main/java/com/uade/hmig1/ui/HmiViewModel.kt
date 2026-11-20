package com.uade.hmig1.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uade.hmig1.data.RobotRepository
import com.uade.hmig1.domain.CommandRules
import com.uade.hmig1.domain.LinkPolicy
import com.uade.hmig1.domain.model.Alert
import com.uade.hmig1.domain.model.AlertStatus
import com.uade.hmig1.domain.model.CommandRecord
import com.uade.hmig1.domain.model.CommandStatus
import com.uade.hmig1.domain.model.CommandType
import com.uade.hmig1.domain.model.Destination
import com.uade.hmig1.domain.model.HistoryEvent
import com.uade.hmig1.domain.model.RobotSummary
import com.uade.hmig1.domain.model.Telemetry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado inmutable que observa la UI. */
data class HmiUiState(
    val selectedRobotId: String? = null,
    val telemetry: Telemetry? = null,
    val alerts: List<Alert> = emptyList(),
    val commands: List<CommandRecord> = emptyList(),
    val now: Long = System.currentTimeMillis(),
    val simLinkUp: Boolean = true,
) {
    val secondsSinceUpdate: Long? get() = telemetry?.let { ((now - it.timestamp) / 1000).coerceAtLeast(0) }

    /** Datos antiguos: no llegan actualizaciones desde hace más de 3 s. */
    val stale: Boolean get() = telemetry != null &&
        now - telemetry.timestamp > LinkPolicy.STALE_AFTER_MS

    val activeAlerts: List<Alert> get() = alerts.filter { it.status == AlertStatus.ACTIVE }
    val pendingCommands: List<CommandRecord> get() =
        commands.filter { it.status == CommandStatus.PENDING || it.status == CommandStatus.NO_RESPONSE }

    fun disabledReason(type: CommandType): String? = CommandRules.disabledReason(type, telemetry, stale)
}

class HmiViewModel(private val repo: RobotRepository) : ViewModel() {

    val robots: List<RobotSummary> = repo.robots

    private val selectedRobot = MutableStateFlow<String?>(null)

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val uiState: StateFlow<HmiUiState> = combine(
        selectedRobot, repo.snapshot, repo.commands, ticker, repo.simulation.linkUp,
    ) { robot, snap, cmds, now, link ->
        HmiUiState(
            selectedRobotId = robot,
            telemetry = snap?.telemetry,
            alerts = snap?.alerts.orEmpty().sortedByDescending { it.createdAt },
            commands = cmds,
            now = now,
            simLinkUp = link,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HmiUiState())

    val history: StateFlow<List<HistoryEvent>> =
        repo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Eventos de la UI ----

    fun selectRobot(id: String) { selectedRobot.value = id }
    fun disconnectFromRobot() { selectedRobot.value = null }

    /** Envía la orden solo si sus condiciones de habilitación se cumplen. */
    fun send(type: CommandType, patientCode: String? = null, destination: Destination? = null, alertId: String? = null) {
        if (uiState.value.disabledReason(type) != null) return
        repo.sendCommand(type, patientCode, destination, alertId)
    }

    fun acknowledge(alert: Alert) = send(CommandType.ACK_ALERT, alertId = alert.id)

    fun clearHistory() { viewModelScope.launch { repo.clearHistory() } }

    // ---- Simulación (demo de escenarios) ----
    fun simSetLink(up: Boolean) = repo.simulation.setLinkUp(up)
    fun simRejectNext() = repo.simulation.rejectNextCommand()
    fun simBatteryLow() = repo.simulation.forceBatteryLow()
    fun simObstacle() = repo.simulation.triggerObstacle()
    fun simPatientAway() = repo.simulation.patientWalksAway()
    fun simReset() = repo.simulation.reset()

    companion object {
        fun factory(repo: RobotRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HmiViewModel(repo) }
        }
    }
}
