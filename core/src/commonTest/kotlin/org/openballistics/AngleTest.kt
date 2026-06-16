package org.openballistics

import org.openballistics.units.Angle
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class AngleTest {

    private val tolerance = 1e-12

    @Test
    fun constructionFromSI() {
        val a = Angle(PI)
        assertEquals(PI, a.radians, absoluteTolerance = tolerance)
    }

    @Test
    fun degreesRoundTrip() {
        val degrees = 45.0
        assertEquals(degrees, Angle.fromDegrees(degrees).degrees, absoluteTolerance = tolerance)
    }

    @Test
    fun moaRoundTrip() {
        val moa = 10.0
        assertEquals(moa, Angle.fromMoa(moa).moa, absoluteTolerance = tolerance)
    }

    @Test
    fun degreesToRadians() {
        val a = Angle.fromDegrees(180.0)
        assertEquals(PI, a.radians, absoluteTolerance = tolerance)
    }

    @Test
    fun sixtyMoaIsOneDegree() {
        val a = Angle.fromMoa(60.0)
        assertEquals(1.0, a.degrees, absoluteTolerance = 1e-10)
    }
}
