package org.openballistics.engine

import org.openballistics.model.ClockDirection
import org.openballistics.model.WindZone
import org.openballistics.units.Distance
import org.openballistics.units.Speed
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES_PER_HOUR = 30.0
private const val DEG_TO_RAD = kotlin.math.PI / 180.0

data class WindComponents(
    val crosswind: Double,
    val headwind: Double
)

object WindDecomposition {

    /**
     * Decompose clock direction + speed into crosswind and headwind (m/s).
     *
     * Convention (spec §3.3):
     *   crosswind = speed × sin((clock − 12) × 30°)
     *   headwind  = speed × cos((clock − 12) × 30°)
     *   crosswind > 0 = wind from shooter's right (3 o'clock)
     */
    fun decompose(clock: ClockDirection, speed: Speed): WindComponents {
        val angleRad = (clock.hour - 12) * DEGREES_PER_HOUR * DEG_TO_RAD
        return WindComponents(
            crosswind = speed.metersPerSecond * sin(angleRad),
            headwind = speed.metersPerSecond * cos(angleRad)
        )
    }

    fun crosswindAt(zones: List<WindZone>, rangeMeters: Double, useGusts: Boolean): Double {
        val zone = zoneAt(zones, rangeMeters) ?: return 0.0
        val speed = if (useGusts) zone.gusts else zone.sustained
        return decompose(zone.direction, speed).crosswind
    }

    fun headwindAt(zones: List<WindZone>, rangeMeters: Double, useGusts: Boolean): Double {
        val zone = zoneAt(zones, rangeMeters) ?: return 0.0
        val speed = if (useGusts) zone.gusts else zone.sustained
        return decompose(zone.direction, speed).headwind
    }

    private fun zoneAt(zones: List<WindZone>, rangeMeters: Double): WindZone? {
        return zones.firstOrNull { rangeMeters >= it.rangeStart.meters && rangeMeters < it.rangeEnd.meters }
    }
}
