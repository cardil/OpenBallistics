package org.openballistics.engine

import org.openballistics.model.TwistDirection
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

private const val EARTH_ROTATION_RAD_PER_SEC = 7.2921159e-5

object Corrections {

    fun spinDrift(
        sg: Double,
        timeOfFlight: Double,
        twistDirection: TwistDirection
    ): Double {
        val sign = StabilityFactor.twistSign(twistDirection)
        return sign * 1.25 * (sg + 1.2) * pow(timeOfFlight, 1.83) * 0.0254
    }

    fun coriolisAcceleration(
        vx: Double, vy: Double, vz: Double,
        latRad: Double, azRad: Double
    ): Triple<Double, Double, Double> {
        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val sinAz = sin(azRad)
        val cosAz = cos(azRad)

        val rangeEast = sinAz
        val rangeNorth = cosAz
        val crossEast = cosAz
        val crossNorth = -sinAz

        val velEast = vx * rangeEast + vz * crossEast
        val velNorth = vx * rangeNorth + vz * crossNorth
        val velUp = vy

        val factor = -2.0 * EARTH_ROTATION_RAD_PER_SEC

        val accelEast = factor * (cosLat * velUp - sinLat * velNorth)
        val accelNorth = factor * (sinLat * velEast)
        val accelUp = factor * (-cosLat * velEast)

        val accelRange = accelEast * rangeEast + accelNorth * rangeNorth
        val accelCross = accelEast * crossEast + accelNorth * crossNorth

        return Triple(accelRange, accelUp, accelCross)
    }

    fun aerodynamicJump(
        crosswindMps: Double,
        sg: Double,
        muzzleVelocityMps: Double,
        twistDirection: TwistDirection
    ): Double {
        if (sg <= 0.0 || muzzleVelocityMps <= 0.0) return 0.0
        val sign = StabilityFactor.twistSign(twistDirection)
        return sign * 0.0001 * crosswindMps / (sg * muzzleVelocityMps)
    }

    fun horizontalRange(slantRange: Double, slopeRadians: Double): Double {
        return slantRange * cos(slopeRadians)
    }

    fun cantVerticalShift(dropMeters: Double, cantRadians: Double): Double {
        return dropMeters * (1.0 - cos(cantRadians))
    }

    fun cantLateralShift(dropMeters: Double, cantRadians: Double): Double {
        return dropMeters * sin(cantRadians)
    }
}

private fun pow(base: Double, exponent: Double): Double {
    if (base <= 0.0) return 0.0
    return exp(exponent * ln(base))
}
