package org.openballistics.engine

import org.openballistics.model.TwistDirection
import org.openballistics.model.TwistRate
import org.openballistics.model.TwistRateUnit
import org.openballistics.units.Distance
import org.openballistics.units.Mass
import org.openballistics.units.Speed
import kotlin.test.Test
import kotlin.test.assertTrue

class StabilityFactorTest {

    @Test
    fun stableWithNormalTwist308() {
        val sg = StabilityFactor.compute(
            twistRate = TwistRate(11.0, TwistRateUnit.INCHES, TwistDirection.RIGHT_HAND),
            bulletDiameter = Distance.fromMillimeters(7.82),
            bulletLength = Distance.fromMillimeters(31.5),
            bulletWeight = Mass.fromGrains(175.0),
            muzzleVelocity = Speed(780.0),
            atmosphere = Atmosphere.STANDARD
        )
        assertTrue(sg >= 1.5, "SF should be >= 1.5 (stable) for .308/1:11\", got $sg")
    }

    @Test
    fun unstableWithSlowTwist308() {
        val sg = StabilityFactor.compute(
            twistRate = TwistRate(20.0, TwistRateUnit.INCHES, TwistDirection.RIGHT_HAND),
            bulletDiameter = Distance.fromMillimeters(7.82),
            bulletLength = Distance.fromMillimeters(31.5),
            bulletWeight = Mass.fromGrains(175.0),
            muzzleVelocity = Speed(780.0),
            atmosphere = Atmosphere.STANDARD
        )
        assertTrue(sg < 1.0, "SF should be < 1.0 (unstable) for .308/1:20\", got $sg")
    }

    @Test
    fun higherVelocityIncreasesStability() {
        val twist = TwistRate(11.0, TwistRateUnit.INCHES, TwistDirection.RIGHT_HAND)
        val bullet = Triple(Distance.fromMillimeters(7.82), Distance.fromMillimeters(31.5), Mass.fromGrains(175.0))

        val sgLow = StabilityFactor.compute(twist, bullet.first, bullet.second, bullet.third, Speed(600.0), Atmosphere.STANDARD)
        val sgHigh = StabilityFactor.compute(twist, bullet.first, bullet.second, bullet.third, Speed(900.0), Atmosphere.STANDARD)

        assertTrue(sgHigh > sgLow, "Higher V0 should increase stability: $sgHigh > $sgLow")
    }

    @Test
    fun thinnerAirIncreasesStability() {
        val twist = TwistRate(11.0, TwistRateUnit.INCHES, TwistDirection.RIGHT_HAND)
        val diam = Distance.fromMillimeters(7.82)
        val length = Distance.fromMillimeters(31.5)
        val weight = Mass.fromGrains(175.0)
        val v0 = Speed(780.0)

        val sgSeaLevel = StabilityFactor.compute(twist, diam, length, weight, v0, Atmosphere.STANDARD)

        val highAlt = org.openballistics.model.AtmosphericData(
            temperature = org.openballistics.units.Temperature(15.0),
            pressure = org.openballistics.units.Pressure(850.0),
            humidity = org.openballistics.units.Percentage(0.0),
            altitude = Distance(1500.0)
        )
        val sgHighAlt = StabilityFactor.compute(twist, diam, length, weight, v0, highAlt)

        assertTrue(sgHighAlt > sgSeaLevel, "Thinner air should increase stability: $sgHighAlt > $sgSeaLevel")
    }
}
