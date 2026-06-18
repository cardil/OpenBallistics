package org.openballistics.engine

import org.openballistics.model.TwistDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CorrectionsTest {

    @Test
    fun coriolisNonzeroAt45N() {
        val latRad = 45.0 * PI / 180.0
        val azRad = 90.0 * PI / 180.0
        val (ax, ay, az) = Corrections.coriolisAcceleration(800.0, 10.0, 0.0, latRad, azRad)
        assertTrue(abs(ax) > 0.0 || abs(ay) > 0.0 || abs(az) > 0.0,
            "Coriolis should be nonzero at 45°N")
    }

    @Test
    fun coriolisZeroAtEquatorWithNoVerticalVelocity() {
        val (_, _, az) = Corrections.coriolisAcceleration(800.0, 0.0, 0.0, 0.0, 0.0)
        assertApprox(0.0, az, 1e-10)
    }

    @Test
    fun slopeReducesHorizontalRange() {
        val slant = 1000.0
        val slopeRad = 15.0 * PI / 180.0
        val horiz = Corrections.horizontalRange(slant, slopeRad)
        assertTrue(horiz < slant, "Horizontal range should be less than slant range with slope")
        assertTrue(horiz > 0, "Horizontal range should be positive")
    }

    @Test
    fun slopeZeroPreservesRange() {
        val slant = 1000.0
        val horiz = Corrections.horizontalRange(slant, 0.0)
        assertApprox(1000.0, horiz, 0.001)
    }

    @Test
    fun spinDriftPositiveForRightHandTwist() {
        val drift = Corrections.spinDrift(1.5, 1.0, TwistDirection.RIGHT_HAND)
        assertTrue(drift > 0, "Spin drift should be positive (right) for RH twist, got $drift")
    }

    @Test
    fun spinDriftNegativeForLeftHandTwist() {
        val drift = Corrections.spinDrift(1.5, 1.0, TwistDirection.LEFT_HAND)
        assertTrue(drift < 0, "Spin drift should be negative (left) for LH twist, got $drift")
    }

    @Test
    fun spinDriftIncreasesWithTimeOfFlight() {
        val short = Corrections.spinDrift(1.5, 0.5, TwistDirection.RIGHT_HAND)
        val long = Corrections.spinDrift(1.5, 2.0, TwistDirection.RIGHT_HAND)
        assertTrue(long > short, "Longer TOF should produce more spin drift: $long > $short")
    }

    @Test
    fun aerodynamicJumpNonzeroWithCrosswind() {
        val aj = Corrections.aerodynamicJump(3.0, 1.5, 800.0, TwistDirection.RIGHT_HAND)
        assertTrue(abs(aj) > 0.0, "AJ should be nonzero with crosswind")
    }

    @Test
    fun aerodynamicJumpZeroWithNoCrosswind() {
        val aj = Corrections.aerodynamicJump(0.0, 1.5, 800.0, TwistDirection.RIGHT_HAND)
        assertApprox(0.0, aj, 1e-10)
    }

    @Test
    fun cantLateralShiftNonzeroWithCant() {
        val cantRad = 5.0 * PI / 180.0
        val lateral = Corrections.cantLateralShift(-1.0, cantRad)
        assertTrue(abs(lateral) > 0.0, "Cant should produce lateral shift")
    }

    @Test
    fun cantNoEffectWithZeroCant() {
        val lateral = Corrections.cantLateralShift(-1.0, 0.0)
        assertApprox(0.0, lateral, 1e-10)
    }

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(
            abs(expected - actual) < tolerance,
            "Expected $expected ± $tolerance, got $actual"
        )
    }
}
