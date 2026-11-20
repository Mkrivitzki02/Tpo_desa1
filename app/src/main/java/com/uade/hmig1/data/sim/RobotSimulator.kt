package com.uade.hmig1.data.sim

import com.uade.hmig1.domain.CommandRules
import com.uade.hmig1.domain.model.AccompanimentState
import com.uade.hmig1.domain.model.Alert
import com.uade.hmig1.domain.model.AlertSeverity
import com.uade.hmig1.domain.model.AlertStatus
import com.uade.hmig1.domain.model.AlertType
import com.uade.hmig1.domain.model.Command
import com.uade.hmig1.domain.model.CommandResponse
import com.uade.hmig1.domain.model.CommandType
import com.uade.hmig1.domain.model.Connectivity
import com.uade.hmig1.domain.model.Destination
import com.uade.hmig1.domain.model.EventType
import com.uade.hmig1.domain.model.Mission
import com.uade.hmig1.domain.model.OperationalState
import com.uade.hmig1.domain.model.RobotSnapshot
import com.uade.hmig1.domain.model.Telemetry
import com.uade.hmig1.domain.model.VoiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.random.Random

/** Evento que el robot reporta para el historial. */
data class SimEvent(val type: EventType, val title: String, val detail: String, val missionId: String?)

/**
 * Robot G1 simulado. Corre en memoria, sin hardware ni red.
 * - Actualiza batería y telemetría cada segundo.
 * - Ejecuta misiones con estados coherentes (buscar paciente → acompañar → entregar → volver).
 * - Emite alertas y permite forzar desconexiones, rechazos e incidentes para la demo.
 * La UI nunca lo toca directamente: solo el Repository.
 */
class RobotSimulator(
    private val scope: CoroutineScope,
    private val robotId: String = "G1-01",
    private val tickMs: Long = 1_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class SimState(
        val battery: Double = 78.0,
        val state: OperationalState = OperationalState.AT_BASE,
        val mission: Mission? = null,
        val progress: Double = 0.0,
        val returnProgress: Double = 0.0,
        val handoffTicks: Int = 0,
        val zone: String = "Recepción principal",
        val floor: Int = 0,
        val patientDistance: Double? = null,
        val accompaniment: AccompanimentState = AccompanimentState.NONE,
        val voice: VoiceStatus = VoiceStatus.INACTIVE,
        val obstacle: Boolean = false,
        val patientDrifting: Boolean = false,
        val stateBeforePause: OperationalState? = null,
        val pausedReason: String? = null,
        val alerts: List<Alert> = emptyList(),
    )

    private val mutex = Mutex()
    private var s = SimState()
    private var missionSeq = 1042
    private var alertSeq = 1

    private val _connected = MutableStateFlow(true)
    /** Enlace simulado robot ↔ app. Si es false, el robot no envía datos ni responde. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private var rejectNext = false

    private val _snapshots = MutableSharedFlow<RobotSnapshot>(replay = 1, extraBufferCapacity = 8)
    val snapshots: SharedFlow<RobotSnapshot> = _snapshots.asSharedFlow()

    private val _events = MutableSharedFlow<SimEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SimEvent> = _events.asSharedFlow()

    fun start() {
        scope.launch {
            emitSnapshot()
            while (isActive) {
                delay(tickMs)
                mutex.withLock { s = tick(s) }
                emitSnapshot()
            }
        }
    }

    // ------------------------------------------------------------------
    // Ciclo de simulación
    // ------------------------------------------------------------------

    private fun tick(prev: SimState): SimState {
        var st = prev
        val movingNow = st.state in setOf(
            OperationalState.GOING_TO_PATIENT, OperationalState.ACCOMPANYING, OperationalState.RETURNING,
        )
        // Batería: consume en movimiento, carga en base.
        val battery = when {
            st.state == OperationalState.AT_BASE -> (st.battery + 0.15).coerceAtMost(100.0)
            movingNow -> st.battery - 0.08
            else -> st.battery - 0.02
        }.coerceAtLeast(0.0)
        st = st.copy(battery = battery)

        st = when (st.state) {
            OperationalState.GOING_TO_PATIENT -> {
                val p = st.progress + 2.5
                if (p >= 25) {
                    emit(EventType.MISSION_STARTED, "Paciente localizado", "${st.mission?.patientCode} · inicia acompañamiento", st.mission?.id)
                    st.copy(
                        progress = 25.0,
                        state = OperationalState.ACCOMPANYING,
                        accompaniment = AccompanimentState.ACCOMPANYING,
                        patientDistance = 0.8,
                        voice = VoiceStatus.ACTIVE,
                    )
                } else st.copy(progress = p)
            }
            OperationalState.ACCOMPANYING -> tickAccompanying(st)
            OperationalState.WAITING_HANDOFF -> {
                val ticks = st.handoffTicks + 1
                if (ticks >= 5) {
                    val m = st.mission
                    emit(EventType.MISSION_COMPLETED, "Misión completada", "${m?.patientCode} entregado en ${m?.destination?.name}", m?.id)
                    st.copy(
                        state = OperationalState.RETURNING,
                        mission = null,
                        progress = 0.0,
                        returnProgress = 0.0,
                        handoffTicks = 0,
                        accompaniment = AccompanimentState.DELIVERED,
                        patientDistance = null,
                        voice = VoiceStatus.INACTIVE,
                    )
                } else st.copy(handoffTicks = ticks)
            }
            OperationalState.RETURNING -> {
                val rp = st.returnProgress + 6
                if (rp >= 100) st.copy(
                    state = OperationalState.AT_BASE,
                    returnProgress = 0.0,
                    accompaniment = AccompanimentState.NONE,
                ) else st.copy(returnProgress = rp)
            }
            else -> st
        }

        st = st.copy(mission = st.mission?.copy(progressPct = st.progress.toInt()))
        st = updateZone(st)
        st = updateBatteryAlert(st)
        return st
    }

    private fun tickAccompanying(st: SimState): SimState {
        // Distancia al paciente: normal ≈ 0,6–1,1 m; si el paciente se aleja, crece.
        val dist = if (st.patientDrifting) (st.patientDistance ?: 1.0) + 0.35
        else 0.6 + Random.nextDouble() * 0.5
        if (dist > 2.0) {
            val alert = newAlert(
                AlertType.PATIENT_SEPARATION, AlertSeverity.CRITICAL,
                "Paciente a ${fmt(dist)} m. Límite de seguridad: 2,0 m. Compruebe su estado antes de reanudar.",
            )
            emit(EventType.ALERT, alert.type.title, alert.message, st.mission?.id)
            return st.copy(
                patientDistance = dist,
                stateBeforePause = OperationalState.ACCOMPANYING,
                state = OperationalState.PAUSED,
                pausedReason = "Pausa automática: paciente fuera del límite",
                alerts = st.alerts + alert,
            )
        }
        if (st.obstacle) {
            val alert = newAlert(
                AlertType.OBSTACLE, AlertSeverity.CRITICAL,
                "Obstáculo persistente en ${st.zone}. El robot se detuvo y espera una decisión del operador.",
            )
            emit(EventType.ALERT, alert.type.title, alert.message, st.mission?.id)
            return st.copy(
                patientDistance = dist,
                stateBeforePause = OperationalState.ACCOMPANYING,
                state = OperationalState.PAUSED,
                pausedReason = "Pausa automática: obstáculo",
                alerts = st.alerts + alert,
            )
        }
        val p = st.progress + 1.5
        return if (p >= 100) st.copy(
            progress = 100.0,
            patientDistance = dist,
            state = OperationalState.WAITING_HANDOFF,
            accompaniment = AccompanimentState.WAITING_HANDOFF,
            handoffTicks = 0,
        ) else st.copy(progress = p, patientDistance = dist)
    }

    private fun updateZone(st: SimState): SimState {
        val dest = st.mission?.destination
        val (zone, floor) = when (st.state) {
            OperationalState.AT_BASE -> "Recepción principal" to 0
            OperationalState.GOING_TO_PATIENT ->
                (if (st.progress < 12) "Recepción principal" else "Sala de espera") to 0
            OperationalState.ACCOMPANYING -> zoneOnRoute(st.progress, dest)
            OperationalState.WAITING_HANDOFF -> (dest?.name ?: st.zone) to (dest?.floor ?: st.floor)
            OperationalState.RETURNING -> when {
                st.returnProgress < 30 && st.floor == 1 -> "Pasillo B" to 1
                st.returnProgress < 50 && st.floor == 1 -> "Ascensor" to 1
                st.returnProgress < 85 -> "Pasillo PB" to 0
                else -> "Recepción principal" to 0
            }
            else -> st.zone to st.floor // pausado / detenido: queda donde está
        }
        return st.copy(zone = zone, floor = floor)
    }

    private fun zoneOnRoute(progress: Double, dest: Destination?): Pair<String, Int> {
        val upstairs = (dest?.floor ?: 0) == 1
        return when {
            progress < 40 -> "Sala de espera" to 0
            upstairs && progress < 55 -> "Ascensor" to 0
            progress < 90 -> (if (upstairs) "Pasillo B" else "Pasillo PB") to (if (upstairs) 1 else 0)
            else -> (dest?.name ?: "Destino") to (dest?.floor ?: 0)
        }
    }

    private fun updateBatteryAlert(st: SimState): SimState {
        val existing = st.alerts.firstOrNull { it.type == AlertType.BATTERY_LOW && it.status != AlertStatus.RESOLVED }
        return when {
            st.battery < 25 && existing == null -> {
                val a = newAlert(
                    AlertType.BATTERY_LOW, AlertSeverity.WARNING,
                    "${st.battery.toInt()}% restante. Umbral: 25%. Planifique el regreso a la base de carga.",
                )
                emit(EventType.ALERT, a.type.title, a.message, st.mission?.id)
                st.copy(alerts = st.alerts + a)
            }
            st.battery >= 30 && existing != null -> st.copy(alerts = resolve(st.alerts, AlertType.BATTERY_LOW))
            else -> st
        }
    }

    // ------------------------------------------------------------------
    // Comandos (respuesta simulada)
    // ------------------------------------------------------------------

    /**
     * Procesa una orden. Si el enlace está caído, el robot nunca responde
     * (el Repository la marcará "sin respuesta" por timeout).
     */
    suspend fun handle(cmd: Command): CommandResponse {
        delay(Random.nextLong(500, 1300)) // latencia de red simulada
        if (!_connected.value) awaitCancellation()
        val response = mutex.withLock {
            if (rejectNext) {
                rejectNext = false
                return@withLock reply(cmd, false, "Rechazo forzado desde el simulador")
            }
            val reason = validate(cmd)
            if (reason != null) reply(cmd, false, reason)
            else reply(cmd, true, applyCommand(cmd))
        }
        emitSnapshot()
        return response
    }

    private fun validate(cmd: Command): String? {
        if (cmd.type == CommandType.START_MISSION && (cmd.patientCode.isNullOrBlank() || cmd.destination == null)) {
            return "Faltan código de paciente o destino"
        }
        if (cmd.type == CommandType.ACK_ALERT) {
            val a = s.alerts.firstOrNull { it.id == cmd.alertId } ?: return "Alerta inexistente"
            return if (a.status == AlertStatus.ACTIVE) null else "La alerta ya fue reconocida"
        }
        return CommandRules.disabledReason(cmd.type, telemetryOf(s), stale = false)
    }

    /** Aplica la orden y devuelve el mensaje de confirmación. */
    private fun applyCommand(cmd: Command): String {
        val now = clock()
        return when (cmd.type) {
            CommandType.START_MISSION -> {
                val m = Mission("M-${missionSeq++}", cmd.patientCode!!, cmd.destination!!, 0, now)
                s = s.copy(
                    mission = m, state = OperationalState.GOING_TO_PATIENT, progress = 0.0,
                    accompaniment = AccompanimentState.LOCATING, patientDrifting = false, obstacle = false,
                )
                emit(EventType.MISSION_STARTED, "Misión iniciada", "${m.patientCode} → ${m.destination.name}", m.id)
                "Misión ${m.id} iniciada. Robot en camino a la sala de espera."
            }
            CommandType.PAUSE_MISSION -> {
                s = s.copy(stateBeforePause = s.state, state = OperationalState.PAUSED, pausedReason = "Pausa del operador")
                "Robot detenido en ${s.zone}."
            }
            CommandType.RESUME_MISSION -> {
                val back = s.stateBeforePause
                    ?: if (s.mission != null) OperationalState.ACCOMPANYING else OperationalState.RETURNING
                val hadObstacle = s.obstacle
                s = s.copy(
                    state = back, stateBeforePause = null, pausedReason = null,
                    obstacle = false, patientDrifting = false,
                    patientDistance = if (back == OperationalState.ACCOMPANYING) 0.8 else s.patientDistance,
                    alerts = resolve(resolve(s.alerts, AlertType.OBSTACLE), AlertType.PATIENT_SEPARATION),
                )
                if (hadObstacle) "Retomando por ruta alternativa." else "Recorrido retomado."
            }
            CommandType.CANCEL_MISSION -> {
                abortMission("Cancelada por el operador")
                "Misión cancelada. Robot regresando a la base."
            }
            CommandType.RETURN_TO_SAFE_POINT -> {
                if (s.mission != null) {
                    abortMission("Regreso a punto seguro")
                } else {
                    s = s.copy(state = OperationalState.RETURNING, returnProgress = 0.0, stateBeforePause = null, pausedReason = null)
                }
                "Robot regresando a Recepción (punto seguro). Avise al personal cercano."
            }
            CommandType.OPERATIONAL_STOP -> {
                val before = if (s.state == OperationalState.PAUSED) s.stateBeforePause else s.state
                s = s.copy(stateBeforePause = before, state = OperationalState.STOPPED, pausedReason = "Parada operativa solicitada")
                "Parada controlada ejecutada en ${s.zone}."
            }
            CommandType.ACK_ALERT -> {
                s = s.copy(alerts = s.alerts.map {
                    if (it.id == cmd.alertId) it.copy(status = AlertStatus.ACKNOWLEDGED) else it
                })
                "Alerta reconocida."
            }
        }
    }

    private fun abortMission(reason: String) {
        val m = s.mission
        emit(EventType.MISSION_CANCELLED, "Misión cancelada", "${m?.patientCode} · $reason", m?.id)
        s = s.copy(
            mission = null, progress = 0.0, state = OperationalState.RETURNING, returnProgress = 0.0,
            accompaniment = AccompanimentState.NONE, patientDistance = null, voice = VoiceStatus.INACTIVE,
            obstacle = false, patientDrifting = false, stateBeforePause = null, pausedReason = null,
            alerts = resolve(resolve(s.alerts, AlertType.OBSTACLE), AlertType.PATIENT_SEPARATION),
        )
    }

    private fun reply(cmd: Command, accepted: Boolean, msg: String) =
        CommandResponse(cmd.id, accepted, msg, clock())

    // ------------------------------------------------------------------
    // Controles de escenario (solo demo)
    // ------------------------------------------------------------------

    fun setConnected(value: Boolean) { _connected.value = value }

    fun rejectNextCommand() { rejectNext = true }

    suspend fun forceBatteryLow() {
        mutex.withLock { s = updateBatteryAlert(s.copy(battery = 24.0)) }
        emitSnapshot()
    }

    /** Obstáculo persistente: solo tiene efecto mientras acompaña a un paciente. */
    suspend fun triggerObstacle(): Boolean {
        val ok = mutex.withLock {
            if (s.state == OperationalState.ACCOMPANYING) { s = s.copy(obstacle = true); true } else false
        }
        return ok
    }

    /** Escenario propio: el paciente se separa del robot. */
    suspend fun patientWalksAway(): Boolean = mutex.withLock {
        if (s.state == OperationalState.ACCOMPANYING) { s = s.copy(patientDrifting = true); true } else false
    }

    suspend fun reset() {
        mutex.withLock { s = SimState() }
        rejectNext = false
        _connected.value = true
        emitSnapshot()
    }

    // ------------------------------------------------------------------

    private suspend fun emitSnapshot() {
        if (!_connected.value) return // sin enlace no llega nada a la app
        val snap = mutex.withLock { RobotSnapshot(telemetryOf(s), s.alerts.takeLast(20)) }
        _snapshots.emit(snap)
    }

    private fun telemetryOf(st: SimState) = Telemetry(
        robotId = robotId,
        connectivity = if (_connected.value) Connectivity.STRONG else Connectivity.DISCONNECTED,
        wifiDbm = -52 - Random.nextInt(0, 8),
        batteryPct = st.battery,
        state = st.state,
        mission = st.mission,
        zone = st.zone,
        floor = st.floor,
        speedMps = when (st.state) {
            OperationalState.GOING_TO_PATIENT -> 0.8
            OperationalState.ACCOMPANYING -> 0.6
            OperationalState.RETURNING -> 0.9
            else -> 0.0
        },
        activeAlerts = st.alerts.count { it.status == AlertStatus.ACTIVE },
        timestamp = clock(),
        patientDistanceM = st.patientDistance,
        accompaniment = st.accompaniment,
        voice = st.voice,
        pausedReason = st.pausedReason,
    )

    private fun newAlert(type: AlertType, severity: AlertSeverity, msg: String) =
        Alert("A-${alertSeq++}", type, severity, msg, clock(), AlertStatus.ACTIVE)

    private fun resolve(list: List<Alert>, type: AlertType) =
        list.map { if (it.type == type && it.status != AlertStatus.RESOLVED) it.copy(status = AlertStatus.RESOLVED) else it }

    private fun emit(type: EventType, title: String, detail: String, missionId: String?) {
        _events.tryEmit(SimEvent(type, title, detail, missionId))
    }

    private fun fmt(d: Double) = String.format(Locale.forLanguageTag("es-AR"), "%.1f", d)
}
