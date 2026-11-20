package com.uade.hmig1.domain.model

// ---------- Telemetría ----------

enum class Connectivity(val label: String) {
    STRONG("Señal fuerte"),
    WEAK("Señal débil"),
    DISCONNECTED("Sin conexión"),
}

enum class OperationalState(val label: String) {
    AT_BASE("En base"),
    GOING_TO_PATIENT("Buscando paciente"),
    ACCOMPANYING("Acompañando"),
    WAITING_HANDOFF("Esperando entrega"),
    RETURNING("Regresando a base"),
    PAUSED("Pausado"),
    STOPPED("Detenido"),
}

/** Variable propia del servicio 1: estado del acompañamiento. */
enum class AccompanimentState(val label: String) {
    NONE("Sin paciente"),
    LOCATING("Localizando paciente"),
    ACCOMPANYING("Acompañando"),
    WAITING_HANDOFF("Esperando entrega"),
    DELIVERED("Entregado"),
}

/** Variable propia del servicio 3: sistema de anuncios por voz. */
enum class VoiceStatus(val label: String) {
    ACTIVE("Activo"),
    INACTIVE("Inactivo"),
    ERROR("Error"),
}

data class Destination(val id: String, val name: String, val floor: Int)

object Destinations {
    val all = listOf(
        Destination("FAR", "Farmacia", 0),
        Destination("C1A", "Consultorio 1A", 1),
        Destination("C2B", "Consultorio 2B", 1),
        Destination("C3B", "Consultorio 3B", 1),
        Destination("LAB", "Laboratorio de análisis", 1),
        Destination("IMG", "Diagnóstico por imágenes", 1),
    )
}

data class Mission(
    val id: String,
    val patientCode: String, // código interno: nunca nombre, DNI ni diagnóstico (riesgo de privacidad)
    val destination: Destination,
    val progressPct: Int,
    val startedAt: Long,
)

data class Telemetry(
    val robotId: String,
    val connectivity: Connectivity,
    val wifiDbm: Int,
    val batteryPct: Double,
    val state: OperationalState,
    val mission: Mission?,
    val zone: String,
    val floor: Int,
    val speedMps: Double,
    val activeAlerts: Int,
    val timestamp: Long, // momento en que el robot generó el dato ("última actualización")
    val patientDistanceM: Double?, // variable propia del servicio 2
    val accompaniment: AccompanimentState,
    val voice: VoiceStatus,
    val pausedReason: String? = null,
)

// ---------- Alertas ----------

enum class AlertSeverity { WARNING, CRITICAL }

enum class AlertType(val title: String) {
    BATTERY_LOW("Batería baja"),
    OBSTACLE("Obstáculo persistente"),
    PATIENT_SEPARATION("Separación del paciente"),
}

enum class AlertStatus(val label: String) {
    ACTIVE("Sin reconocer"),
    ACKNOWLEDGED("Reconocida"),
    RESOLVED("Resuelta"),
}

data class Alert(
    val id: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val createdAt: Long,
    val status: AlertStatus,
)

/** Lo que el robot envía en cada ciclo: telemetría + alertas. */
data class RobotSnapshot(val telemetry: Telemetry, val alerts: List<Alert>)

// ---------- Comandos ----------

enum class CommandType(val label: String, val requiresConfirmation: Boolean) {
    START_MISSION("Iniciar misión", false),
    PAUSE_MISSION("Pausar misión", false),
    RESUME_MISSION("Reanudar misión", false),
    CANCEL_MISSION("Cancelar misión", true),
    RETURN_TO_SAFE_POINT("Volver a punto seguro", true),
    OPERATIONAL_STOP("Parada operativa", true),
    ACK_ALERT("Reconocer alerta", false),
}

/** Una orden enviada no equivale a una orden ejecutada. */
enum class CommandStatus(val label: String) {
    PENDING("Pendiente"),
    CONFIRMED("Confirmada"),
    REJECTED("Rechazada"),
    NO_RESPONSE("Sin respuesta"),
}

data class Command(
    val id: String,
    val type: CommandType,
    val issuedAt: Long,
    val patientCode: String? = null,
    val destination: Destination? = null,
    val alertId: String? = null,
)

data class CommandResponse(
    val commandId: String,
    val accepted: Boolean,
    val message: String,
    val receivedAt: Long,
)

data class CommandRecord(
    val command: Command,
    val status: CommandStatus,
    val message: String?,
    val updatedAt: Long,
)

// ---------- Robots e historial ----------

enum class RobotAvailability(val label: String) {
    AVAILABLE("Disponible"),
    IN_MISSION("En misión"),
    OFFLINE("Sin conexión"),
}

data class RobotSummary(
    val id: String,
    val role: String,
    val location: String,
    val batteryPct: Int?,
    val availability: RobotAvailability,
)

enum class EventType(val label: String) {
    MISSION_STARTED("Misión iniciada"),
    MISSION_COMPLETED("Misión completada"),
    MISSION_CANCELLED("Misión cancelada"),
    ALERT("Alerta"),
    COMMAND("Comando"),
    CONNECTION("Conexión"),
}

data class HistoryEvent(
    val id: Long,
    val timestamp: Long,
    val type: EventType,
    val title: String,
    val detail: String,
    val missionId: String?,
)
