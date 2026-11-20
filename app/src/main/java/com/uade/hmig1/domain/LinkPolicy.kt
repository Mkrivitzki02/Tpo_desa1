package com.uade.hmig1.domain

/** Tiempos del enlace operador ↔ robot (compartidos por Repository y UI). */
object LinkPolicy {
    /** Sin telemetría nueva durante este tiempo → datos antiguos / conexión perdida. */
    const val STALE_AFTER_MS = 3_000L

    /** Sin respuesta del robot durante este tiempo → orden "sin respuesta". */
    const val COMMAND_TIMEOUT_MS = 5_000L
}
