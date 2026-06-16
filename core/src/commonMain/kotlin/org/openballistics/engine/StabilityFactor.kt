package org.openballistics.engine

import org.openballistics.model.AtmosphericData
import org.openballistics.model.TwistDirection
import org.openballistics.model.TwistRate
import org.openballistics.model.TwistRateUnit
import org.openballistics.units.Distance
import org.openballistics.units.Mass
import org.openballistics.units.Speed
import kotlin.math.pow

/**
 * Miller's gyroscopic stability formula.
 *
 * Sg = (30 · m) / (t² · d³ · l · (1 + l²)) · (V / 2800)^(1/3) · ftp
 *
 * where m = bullet mass (grains), t = twist (calibers/turn),
 * d = bullet diameter (inches), l = bullet length / diameter (calibers).
 * ftp = ((T_F + 460) / (59 + 460)) * (29.92 / P_inHg) — temperature/pressure correction
 * matching py-ballisticcalc's _calc_stability_coefficient().
 */
object StabilityFactor {

    private const val STANDARD_VELOCITY_FPS = 2800.0

    fun compute(
        twistRate: TwistRate,
        bulletDiameter: Distance,
        bulletLength: Distance,
        bulletWeight: Mass,
        muzzleVelocity: Speed,
        atmosphere: AtmosphericData
    ): Double {
        val dInches = bulletDiameter.inches
        val lInches = bulletLength.inches
        val mGrains = bulletWeight.grains
        val vFps = muzzleVelocity.fps

        val twistInches = when (twistRate.unit) {
            TwistRateUnit.INCHES -> twistRate.rate
            TwistRateUnit.MILLIMETERS -> twistRate.rate / 25.4
        }

        // t = twist rate in calibers per turn
        val tCalibers = twistInches / dInches

        // l = bullet length in calibers
        val lCalibers = lInches / dInches

        // Miller's formula
        val sg = (30.0 * mGrains) /
            (tCalibers.pow(2) * dInches.pow(3) * lCalibers * (1.0 + lCalibers.pow(2)))

        // Velocity correction factor
        val velocityCorrection = (vFps / STANDARD_VELOCITY_FPS).pow(1.0 / 3.0)

        val tempFahrenheit = atmosphere.temperature.celsius * 9.0 / 5.0 + 32.0
        val pressureInHg = atmosphere.pressure.hectopascals / 33.86389
        val ftp = ((tempFahrenheit + 460.0) / (59.0 + 460.0)) * (29.92 / pressureInHg)

        return sg * velocityCorrection * ftp
    }

    fun twistSign(direction: TwistDirection): Double = when (direction) {
        TwistDirection.RIGHT_HAND -> 1.0
        TwistDirection.LEFT_HAND -> -1.0
    }
}
