package org.openballistics

import org.openballistics.units.AngularValue
import kotlin.test.Test
import kotlin.test.assertEquals

class AngularValueTest {

    private val tolerance = 1e-10

    @Test
    fun constructionFromMrad() {
        val av = AngularValue(1.0)
        assertEquals(1.0, av.milliradians, absoluteTolerance = tolerance)
    }

    @Test
    fun moaRoundTrip() {
        val moa = 3.5
        assertEquals(moa, AngularValue.fromMoa(moa).moa, absoluteTolerance = tolerance)
    }

    @Test
    fun radiansRoundTrip() {
        val radians = 0.001
        assertEquals(radians, AngularValue.fromRadians(radians).radians, absoluteTolerance = tolerance)
    }

    @Test
    fun fromRadiansToMrad() {
        val av = AngularValue.fromRadians(1.0)
        assertEquals(1000.0, av.milliradians, absoluteTolerance = tolerance)
    }
}
