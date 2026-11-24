package com.uade.hmig1

import com.uade.hmig1.domain.CommandRules
import com.uade.hmig1.domain.model.AccompanimentState
import com.uade.hmig1.domain.model.CommandType
import com.uade.hmig1.domain.model.Connectivity
import com.uade.hmig1.domain.model.Destinations
import com.uade.hmig1.domain.model.Mission
import com.uade.hmig1.domain.model.OperationalState
import com.uade.hmig1.domain.model.Telemetry
import com.uade.hmig1.domain.model.VoiceStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CommandRulesTest {

    private fun telemetry(
        state: OperationalState = OperationalState.AT_BASE,
        battery: Double = 80.0,
        withMission: Boolean = false,
    ) = Telemetry(
        robotId = "G1-01", connectivity = Connectivity.STRONG, wifiDbm = -54, batteryPct = battery,
        state = state,
        mission = if (withMission) Mission("M-1", "P-0001", Destinations.all.first(), 10, 0) else null,
        zone = "Recepción", floor = 0, speedMps = 0.0, activeAlerts = 0, timestamp = 0,
        patientDistanceM = null, accompaniment = AccompanimentState.NONE, voice = VoiceStatus.INACTIVE,
    )

    @Test fun startAllowedAtBaseWithBattery() =
        assertNull(CommandRules.disabledReason(CommandType.START_MISSION, telemetry(), stale = false))

    @Test fun startBlockedWithLowBattery() =
        assertNotNull(CommandRules.disabledReason(CommandType.START_MISSION, telemetry(battery = 18.0), stale = false))

    @Test fun startBlockedWhenDataIsStale() =
        assertNotNull(CommandRules.disabledReason(CommandType.START_MISSION, telemetry(), stale = true))

    @Test fun pauseOnlyWhileMoving() {
        assertNotNull(CommandRules.disabledReason(CommandType.PAUSE_MISSION, telemetry(), false))
        assertNull(CommandRules.disabledReason(CommandType.PAUSE_MISSION,
            telemetry(OperationalState.ACCOMPANYING, withMission = true), false))
    }

    @Test fun resumeOnlyWhenPausedOrStopped() {
        assertNull(CommandRules.disabledReason(CommandType.RESUME_MISSION, telemetry(OperationalState.PAUSED, withMission = true), false))
        assertNull(CommandRules.disabledReason(CommandType.RESUME_MISSION, telemetry(OperationalState.STOPPED, withMission = true), false))
        assertNotNull(CommandRules.disabledReason(CommandType.RESUME_MISSION, telemetry(OperationalState.ACCOMPANYING, withMission = true), false))
    }

    @Test fun safetyOrdersStayAvailableWhileDisconnected() {
        // Con datos viejos, las órdenes de seguridad se pueden enviar (quedarán pendientes / sin respuesta).
        val t = telemetry(OperationalState.ACCOMPANYING, withMission = true)
        assertNull(CommandRules.disabledReason(CommandType.OPERATIONAL_STOP, t, stale = true))
        assertNull(CommandRules.disabledReason(CommandType.RETURN_TO_SAFE_POINT, t, stale = true))
    }

    @Test fun resumeBlockedUntilCriticalAlertIsAcknowledged() {
        val t = telemetry(OperationalState.PAUSED, withMission = true)
        assertNotNull(CommandRules.disabledReason(CommandType.RESUME_MISSION, t, false, unackedCritical = true))
        assertNull(CommandRules.disabledReason(CommandType.RESUME_MISSION, t, false, unackedCritical = false))
    }

    @Test fun forcedLowBatteryBlocksStart() {
        // El botón "Forzar batería baja" del simulador debe mostrar también el bloqueo de inicio.
        val t = telemetry(battery = com.uade.hmig1.data.sim.RobotSimulator.FORCED_LOW_BATTERY)
        assertNotNull(CommandRules.disabledReason(CommandType.START_MISSION, t, stale = false))
    }

    @Test fun startAllowedJustAboveMinimum() {
        assertNull(CommandRules.disabledReason(CommandType.START_MISSION, telemetry(battery = 20.5), stale = false))
        assertNotNull(CommandRules.disabledReason(CommandType.START_MISSION, telemetry(battery = 20.0), stale = false))
    }
}
