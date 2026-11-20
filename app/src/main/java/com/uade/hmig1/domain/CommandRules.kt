package com.uade.hmig1.domain

import com.uade.hmig1.domain.model.CommandType
import com.uade.hmig1.domain.model.OperationalState
import com.uade.hmig1.domain.model.Telemetry

/**
 * Condiciones de habilitación de cada orden.
 * La UI las usa para deshabilitar botones y mostrar el motivo;
 * el simulador las vuelve a validar del lado del robot (puede rechazar).
 */
object CommandRules {
    const val MIN_BATTERY_TO_START = 20.0

    private val moving = setOf(
        OperationalState.GOING_TO_PATIENT,
        OperationalState.ACCOMPANYING,
        OperationalState.RETURNING,
    )

    /** Devuelve null si la orden está habilitada, o el motivo por el que no lo está. */
    fun disabledReason(type: CommandType, t: Telemetry?, stale: Boolean): String? {
        if (t == null) return "Sin datos del robot"
        return when (type) {
            CommandType.START_MISSION -> when {
                stale -> "Sin datos actualizados del robot"
                t.mission != null -> "Ya hay una misión activa"
                t.state != OperationalState.AT_BASE -> "El robot debe estar en base"
                t.batteryPct <= MIN_BATTERY_TO_START -> "Batería insuficiente (≤ 20%)"
                else -> null
            }
            CommandType.PAUSE_MISSION ->
                if (t.state in moving) null else "No hay desplazamiento para pausar"
            CommandType.RESUME_MISSION ->
                if (t.state == OperationalState.PAUSED || t.state == OperationalState.STOPPED) null
                else "La misión no está pausada"
            CommandType.CANCEL_MISSION ->
                if (t.mission != null) null else "No hay misión activa"
            CommandType.RETURN_TO_SAFE_POINT ->
                if (t.state != OperationalState.AT_BASE && t.state != OperationalState.RETURNING) null
                else "El robot ya está en base o regresando"
            CommandType.OPERATIONAL_STOP ->
                if (t.state != OperationalState.AT_BASE && t.state != OperationalState.STOPPED) null
                else "El robot ya está detenido"
            CommandType.ACK_ALERT -> null
        }
    }
}
