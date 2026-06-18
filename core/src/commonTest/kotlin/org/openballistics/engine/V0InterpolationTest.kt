package org.openballistics.engine

import org.openballistics.model.VelocityEntry
import org.openballistics.units.Speed
import org.openballistics.units.Temperature
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class V0InterpolationTest {

    private val table = listOf(
        VelocityEntry(Temperature(-10.0), Speed(745.0), false),
        VelocityEntry(Temperature(5.0), Speed(762.0), false),
        VelocityEntry(Temperature(20.0), Speed(780.0), false),
        VelocityEntry(Temperature(35.0), Speed(798.0), false)
    )

    @Test
    fun exactMatchReturnsExactValue() {
        val v = V0Interpolation.interpolate(table, Temperature(20.0))
        assertApprox(780.0, v.metersPerSecond, 0.01)
    }

    @Test
    fun interpolatesBetweenPoints() {
        val v = V0Interpolation.interpolate(table, Temperature(12.5))
        assertApprox(771.0, v.metersPerSecond, 0.5)
    }

    @Test
    fun extrapolatesBelowRange() {
        val v = V0Interpolation.interpolate(table, Temperature(-20.0))
        assertTrue(v.metersPerSecond < 745.0, "Should extrapolate below: ${v.metersPerSecond}")
    }

    @Test
    fun extrapolatesAboveRange() {
        val v = V0Interpolation.interpolate(table, Temperature(50.0))
        assertTrue(v.metersPerSecond > 798.0, "Should extrapolate above: ${v.metersPerSecond}")
    }

    @Test
    fun singleEntryReturnsConstant() {
        val single = listOf(VelocityEntry(Temperature(20.0), Speed(780.0), true))
        val v = V0Interpolation.interpolate(single, Temperature(-10.0))
        assertApprox(780.0, v.metersPerSecond, 0.01)
    }

    @Test
    fun higherTempGivesHigherV0() {
        val vCold = V0Interpolation.interpolate(table, Temperature(0.0))
        val vHot = V0Interpolation.interpolate(table, Temperature(30.0))
        assertTrue(vHot.metersPerSecond > vCold.metersPerSecond)
    }

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(
            abs(expected - actual) < tolerance,
            "Expected $expected ± $tolerance, got $actual"
        )
    }
}
