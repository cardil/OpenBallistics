package org.openballistics.engine

import org.openballistics.model.AtmosphericData
import org.openballistics.units.Distance
import org.openballistics.units.Percentage
import org.openballistics.units.Pressure
import org.openballistics.units.Temperature
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class AtmosphereTest {

    @Test
    fun standardAtmosphereAtSeaLevel() {
        val rho = Atmosphere.airDensity(Atmosphere.STANDARD)
        assertApprox(1.225, rho, 0.005)
    }

    @Test
    fun standardPressureAtSeaLevel() {
        val p = Atmosphere.standardPressure(Distance.ZERO)
        assertApprox(1013.25, p.hectopascals, 0.1)
    }

    @Test
    fun standardPressureAt1500m() {
        val p = Atmosphere.standardPressure(Distance(1500.0))
        assertApprox(845.6, p.hectopascals, 1.0)
    }

    @Test
    fun standardTemperatureAtSeaLevel() {
        val t = Atmosphere.standardTemperature(Distance.ZERO)
        assertApprox(15.0, t.celsius, 0.01)
    }

    @Test
    fun densityRatioIsOneForIdenticalConditions() {
        val ratio = Atmosphere.densityRatio(Atmosphere.STANDARD, Atmosphere.STANDARD)
        assertApprox(1.0, ratio, 0.001)
    }

    @Test
    fun higherPressureIncreasesAirDensity() {
        val lowP = AtmosphericData(Temperature(15.0), Pressure(950.0), Percentage(0.5), Distance(0.0))
        val highP = AtmosphericData(Temperature(15.0), Pressure(1050.0), Percentage(0.5), Distance(0.0))
        assertTrue(Atmosphere.airDensity(highP) > Atmosphere.airDensity(lowP))
    }

    @Test
    fun higherTemperatureDecreasesAirDensity() {
        val cold = AtmosphericData(Temperature(-10.0), Pressure(1013.25), Percentage(0.5), Distance(0.0))
        val hot = AtmosphericData(Temperature(35.0), Pressure(1013.25), Percentage(0.5), Distance(0.0))
        assertTrue(Atmosphere.airDensity(cold) > Atmosphere.airDensity(hot))
    }

    @Test
    fun higherHumiditySlightlyDecreasesAirDensity() {
        val dry = AtmosphericData(Temperature(25.0), Pressure(1013.25), Percentage(0.0), Distance(0.0))
        val humid = AtmosphericData(Temperature(25.0), Pressure(1013.25), Percentage.fromPercent(90.0), Distance(0.0))
        assertTrue(Atmosphere.airDensity(dry) > Atmosphere.airDensity(humid))
    }

    @Test
    fun speedOfSoundAtStandardConditions() {
        val sos = Atmosphere.speedOfSound(Temperature(15.0))
        assertApprox(340.3, sos, 0.5)
    }

    @Test
    fun machNumberAtMuzzleVelocity() {
        val mach = Atmosphere.machNumber(800.0, Temperature(15.0))
        assertTrue(mach > 2.0)
        assertTrue(mach < 3.0)
    }

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(
            abs(expected - actual) < tolerance,
            "Expected $expected ± $tolerance, got $actual (diff=${abs(expected - actual)})"
        )
    }
}
