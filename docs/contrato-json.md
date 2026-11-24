# Contrato de mensajes propuesto

> **Aclaración:** este contrato es una propuesta del grupo para desacoplar la app del robot. **No corresponde al protocolo oficial de Unitree.** En la app lo implementa `SimulatedRobotRepository` con objetos Kotlin equivalentes (`Telemetry`, `Command`, `CommandResponse`, `Alert`).

## 1. Telemetría (robot → app, cada 1 s)

```json
{
  "robot_id": "G1-01",
  "timestamp": "2026-11-12T09:43:00Z",
  "connectivity": "strong",
  "wifi_dbm": -54,
  "battery_pct": 78.4,
  "operational_state": "accompanying",
  "mission": {
    "id": "M-1042",
    "patient_code": "P-2847",
    "destination": { "id": "C3B", "name": "Consultorio 3B", "floor": 1 },
    "progress_pct": 62
  },
  "location": { "zone": "Pasillo B", "floor": 1 },
  "speed_mps": 0.6,
  "active_alerts": 0,
  "service": {
    "patient_distance_m": 0.8,
    "accompaniment_state": "accompanying",
    "voice_system": "active"
  },
  "paused_reason": null
}
```

- `connectivity` puede ser `strong`, `weak` o `disconnected` (la app además marca *sin conexión* si el dato tiene más de 3 s).
- `operational_state` puede ser `at_base`, `going_to_patient`, `accompanying`, `waiting_handoff`, `returning`, `paused` o `stopped`.
- `accompaniment_state` puede ser `none`, `locating`, `accompanying`, `waiting_handoff` o `delivered`.
- `voice_system` puede ser `active`, `inactive` o `error`.
- `patient_distance_m` es `null` cuando no hay paciente acompañado; el límite es 2,0 m.
- La app considera **obsoleto** todo dato con `timestamp` de más de 3 s de antigüedad.

## 2. Comando (app → robot)

```json
{
  "command_id": "CMD-0091",
  "robot_id": "G1-01",
  "type": "start_mission",
  "issued_at": "2026-11-12T09:40:12Z",
  "issued_by": "operator_01",
  "params": { "patient_code": "P-2847", "destination_id": "C3B" }
}
```

- `type` puede ser `start_mission`, `pause_mission`, `resume_mission`, `cancel_mission`, `return_to_safe_point`, `operational_stop` o `ack_alert`.
- Para `ack_alert`, `params` lleva el identificador de la alerta: `{ "alert_id": "A-3" }`.

## 3. Respuestas (robot → app)

**Confirmada**

```json
{
  "command_id": "CMD-0092",
  "result": "confirmed",
  "received_at": "2026-11-12T09:44:01Z",
  "message": "Robot detenido en Pasillo B."
}
```

**Rechazada**

```json
{
  "command_id": "CMD-0093",
  "result": "rejected",
  "received_at": "2026-11-12T09:44:20Z",
  "message": "Batería insuficiente (≤ 20%)"
}
```

**Sin respuesta.** No hay mensaje: la app espera 5 s y, si el robot no responde, marca la orden como `no_response` del lado del operador. La orden **no se asume ejecutada**.

```json
{ "command_id": "CMD-0094", "local_status": "no_response", "timeout_ms": 5000 }
```

## 4. Alerta (robot → app, dentro del snapshot)

```json
{
  "id": "A-3",
  "type": "patient_separation",
  "severity": "critical",
  "status": "active",
  "created_at": "2026-11-12T09:42:06Z",
  "message": "Paciente a 2,4 m. Límite de seguridad: 2,0 m."
}
```

- `type` puede ser `battery_low` (warning), `obstacle` (critical) o `patient_separation` (critical).
- `status` puede ser `active`, `acknowledged` o `resolved`.
