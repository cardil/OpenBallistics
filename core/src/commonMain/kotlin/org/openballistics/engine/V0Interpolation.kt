package org.openballistics.engine

import org.openballistics.model.VelocityEntry
import org.openballistics.units.Speed
import org.openballistics.units.Temperature

object V0Interpolation {

    fun interpolate(velocityTable: List<VelocityEntry>, currentTemp: Temperature): Speed {
        require(velocityTable.isNotEmpty()) { "Velocity table must have at least one entry" }

        if (velocityTable.size == 1) {
            return velocityTable[0].velocity
        }

        val sorted = velocityTable.sortedBy { it.temperature.celsius }
        val t = currentTemp.celsius

        if (t <= sorted.first().temperature.celsius) {
            return extrapolate(sorted[0], sorted[1], t)
        }
        if (t >= sorted.last().temperature.celsius) {
            return extrapolate(sorted[sorted.size - 2], sorted[sorted.size - 1], t)
        }

        for (i in 0 until sorted.size - 1) {
            val lo = sorted[i]
            val hi = sorted[i + 1]
            if (t >= lo.temperature.celsius && t <= hi.temperature.celsius) {
                return linearInterpolate(lo, hi, t)
            }
        }

        return sorted.last().velocity
    }

    private fun linearInterpolate(lo: VelocityEntry, hi: VelocityEntry, tempC: Double): Speed {
        val tRange = hi.temperature.celsius - lo.temperature.celsius
        if (tRange == 0.0) return lo.velocity
        val fraction = (tempC - lo.temperature.celsius) / tRange
        val v = lo.velocity.metersPerSecond +
            fraction * (hi.velocity.metersPerSecond - lo.velocity.metersPerSecond)
        return Speed(v)
    }

    private fun extrapolate(a: VelocityEntry, b: VelocityEntry, tempC: Double): Speed {
        return linearInterpolate(a, b, tempC)
    }
}
