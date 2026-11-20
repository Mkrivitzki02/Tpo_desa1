package com.uade.hmig1.data

import com.uade.hmig1.domain.model.CommandRecord
import com.uade.hmig1.domain.model.CommandType
import com.uade.hmig1.domain.model.Destination
import com.uade.hmig1.domain.model.HistoryEvent
import com.uade.hmig1.domain.model.RobotSnapshot
import com.uade.hmig1.domain.model.RobotSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Punto único de acceso a telemetría, comandos y eventos.
 * La UI y el ViewModel dependen solo de esta interfaz: hoy la implementa
 * un robot simulado; mañana podría implementarla un cliente real (WebSocket, ROS 2, etc.).
 */
interface RobotRepository {
    val robots: List<RobotSummary>

    /** Último dato recibido del robot. Puede estar desactualizado (ver timestamp). */
    val snapshot: StateFlow<RobotSnapshot?>

    /** Órdenes enviadas en la sesión con su estado (pendiente / confirmada / rechazada / sin respuesta). */
    val commands: StateFlow<List<CommandRecord>>

    /** Historial persistido. */
    val history: Flow<List<HistoryEvent>>

    fun start()

    fun sendCommand(
        type: CommandType,
        patientCode: String? = null,
        destination: Destination? = null,
        alertId: String? = null,
    )

    suspend fun clearHistory()

    /** Controles para demostrar escenarios. No forman parte del contrato con un robot real. */
    val simulation: SimulationControls
}

interface SimulationControls {
    val linkUp: StateFlow<Boolean>
    fun setLinkUp(up: Boolean)
    fun rejectNextCommand()
    fun forceBatteryLow()
    fun triggerObstacle()
    fun patientWalksAway()
    fun reset()
}
