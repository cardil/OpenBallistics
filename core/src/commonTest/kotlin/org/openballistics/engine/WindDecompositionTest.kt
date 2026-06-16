package org.openballistics.engine

import org.openballistics.model.ClockDirection
import org.openballistics.units.Speed
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class WindDecompositionTest {

    @Test
    fun clock3IsFullCrosswindFromRight() {
        val result = WindDecomposition.decompose(ClockDirection(3), Speed(5.0))
        assertApprox(5.0, result.crosswind, 0.01)
        assertApprox(0.0, result.headwind, 0.01)
    }

    @Test
    fun clock9IsFullCrosswindFromLeft() {
        val result = WindDecomposition.decompose(ClockDirection(9), Speed(5.0))
        assertApprox(-5.0, result.crosswind, 0.01)
        assertApprox(0.0, result.headwind, 0.01)
    }

    @Test
    fun clock12IsPureHeadwind() {
        val result = WindDecomposition.decompose(ClockDirection(12), Speed(5.0))
        assertApprox(0.0, result.crosswind, 0.01)
        assertApprox(5.0, result.headwind, 0.01)
    }

    @Test
    fun clock6IsPureTailwind() {
        val result = WindDecomposition.decompose(ClockDirection(6), Speed(5.0))
        assertApprox(0.0, result.crosswind, 0.01)
        assertApprox(-5.0, result.headwind, 0.01)
    }

    @Test
    fun clock2IsMixedHeadwindAndRightCrosswind() {
        val result = WindDecomposition.decompose(ClockDirection(2), Speed(5.0))
        assertTrue(result.crosswind > 0, "Crosswind should be positive (from right)")
        assertTrue(result.headwind > 0, "Headwind should be positive (from front)")
    }

    @Test
    fun clock10IsMixedHeadwindAndLeftCrosswind() {
        val result = WindDecomposition.decompose(ClockDirection(10), Speed(5.0))
        assertTrue(result.crosswind < 0, "Crosswind should be negative (from left)")
        assertTrue(result.headwind > 0, "Headwind should be positive (from front)")
    }

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(
            abs(expected - actual) < tolerance,
            "Expected $expected ± $tolerance, got $actual"
        )
    }
}
