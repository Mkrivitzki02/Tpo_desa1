package com.uade.hmig1.data

import com.uade.hmig1.data.local.EventDao
import com.uade.hmig1.data.local.EventEntity
import com.uade.hmig1.data.sim.RobotSimulator
import com.uade.hmig1.domain.LinkPolicy.COMMAND_TIMEOUT_MS
import com.uade.hmig1.domain.LinkPolicy.STALE_AFTER_MS
import com.uade.hmig1.domain.model.Command
import com.uade.hmig1.domain.model.CommandRecord
import com.uade.hmig1.domain.model.CommandStatus
import com.uade.hmig1.domain.model.CommandType
import com.uade.hmig1.domain.model.Destination
import com.uade.hmig1.domain.model.EventType
import com.uade.hmig1.domain.model.HistoryEvent
import com.uade.hmig1.domain.model.RobotAvailability
import com.uade.hmig1.domain.model.RobotSnapshot
import com.uade.hmig1.domain.model.RobotSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

class SimulatedRobotRepository(
    private val simulator: RobotSimulator,
    private val dao: EventDao,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : RobotRepository {

    override val robots = listOf(
        RobotSummary("G1-01", "Asistente de planta", "Sector B · Base de carga", 78, RobotAvailability.AVAILABLE),
        RobotSummary("G1-02", "Asistente de reparto", "Sector A · Pasillo 2", 64, RobotAvailability.IN_MISSION),
        RobotSummary("G1-03", "Asistente de recepción", "Última conexión 09:12", null, RobotAvailability.OFFLINE),
    )

    private val _snapshot = MutableStateFlow<RobotSnapshot?>(null)
    override val snapshot: StateFlow<RobotSnapshot?> = _snapshot.asStateFlow()

    private val _commands = MutableStateFlow<List<CommandRecord>>(emptyList())
    override val commands: StateFlow<List<CommandRecord>> = _commands.asStateFlow()

    override val history: Flow<List<HistoryEvent>> = dao.observeAll().map { list ->
        list.map {
            HistoryEvent(it.id, it.timestamp, EventType.valueOf(it.type), it.title, it.detail, it.missionId)
        }
    }

    private val seq = AtomicInteger(91)
    private var started = false

    override fun start() {
        if (started) return
        started = true
        simulator.start()
        scope.launch { simulator.snapshots.collect { _snapshot.value = it } }
        scope.launch { simulator.events.collect { log(it.type, it.title, it.detail, it.missionId) } }
        scope.launch { connectionWatchdog() }
    }

    /** Detecta la pérdida de enlace solo por la antigüedad de los datos, como haría con un robot real. */
    private suspend fun connectionWatchdog() {
        var lost = false
        while (scope.isActive) {
            delay(1_000)
            val last = _snapshot.value?.telemetry?.timestamp ?: continue
            val stale = clock() - last > STALE_AFTER_MS
            if (stale && !lost) {
                lost = true
                log(EventType.CONNECTION, "Conexión perdida", "Sin telemetría desde hace más de ${STALE_AFTER_MS / 1000} s", null)
            } else if (!stale && lost) {
                lost = false
                log(EventType.CONNECTION, "Conexión recuperada", "Telemetría restablecida", null)
            }
        }
    }

    override fun sendCommand(type: CommandType, patientCode: String?, destination: Destination?, alertId: String?) {
        val cmd = Command(
            id = "CMD-%04d".format(seq.getAndIncrement()),
            type = type,
            issuedAt = clock(),
            patientCode = patientCode,
            destination = destination,
            alertId = alertId,
        )
        upsert(CommandRecord(cmd, CommandStatus.PENDING, "Enviada · esperando confirmación del robot", clock()))

        scope.launch {
            val response = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { simulator.handle(cmd) }
            val record = when {
                response == null -> CommandRecord(
                    cmd, CommandStatus.NO_RESPONSE,
                    "Sin confirmación tras ${COMMAND_TIMEOUT_MS / 1000} s. No se asume ejecutada.", clock(),
                )
                response.accepted -> CommandRecord(cmd, CommandStatus.CONFIRMED, response.message, response.receivedAt)
                else -> CommandRecord(cmd, CommandStatus.REJECTED, response.message, response.receivedAt)
            }
            upsert(record)
            log(EventType.COMMAND, "${type.label} · ${record.status.label}", record.message ?: "", _snapshot.value?.telemetry?.mission?.id)
        }
    }

    private fun upsert(record: CommandRecord) {
        _commands.update { list ->
            (listOf(record) + list.filterNot { it.command.id == record.command.id }).take(30)
        }
    }

    private suspend fun log(type: EventType, title: String, detail: String, missionId: String?) {
        dao.insert(EventEntity(timestamp = clock(), type = type.name, title = title, detail = detail, missionId = missionId))
    }

    override suspend fun clearHistory() = dao.clear()

    override val simulation: SimulationControls = object : SimulationControls {
        override val linkUp: StateFlow<Boolean> = simulator.connected
        override fun setLinkUp(up: Boolean) = simulator.setConnected(up)
        override fun rejectNextCommand() = simulator.rejectNextCommand()
        override fun forceBatteryLow() { scope.launch { simulator.forceBatteryLow() } }
        override fun triggerObstacle() { scope.launch { simulator.triggerObstacle() } }
        override fun patientWalksAway() { scope.launch { simulator.patientWalksAway() } }
        override fun reset() {
            scope.launch {
                simulator.reset()
                _commands.value = emptyList()
            }
        }
    }
}
