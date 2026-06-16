package org.openballistics

import org.openballistics.units.Speed
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeedTest {

    private val tolerance = 1e-9

    @Test
    fun constructionFromSI() {
        val s = Speed(1.0)
        assertEquals(1.0, s.metersPerSecond, absoluteTolerance = tolerance)
    }

    @Test
    fun fpsRoundTrip() {
        val fps = 2800.0
        assertEquals(fps, Speed.fromFps(fps).fps, absoluteTolerance = tolerance)
    }

    @Test
    fun mphRoundTrip() {
        val mph = 60.0
        assertEquals(mph, Speed.fromMph(mph).mph, absoluteTolerance = tolerance)
    }

    @Test
    fun knotsRoundTrip() {
        val knots = 10.0
        assertEquals(knots, Speed.fromKnots(knots).knots, absoluteTolerance = tolerance)
    }

    @Test
    fun kmhRoundTrip() {
        val kmh = 100.0
        assertEquals(kmh, Speed.fromKmh(kmh).kmh, absoluteTolerance = tolerance)
    }

    @Test
    fun fpsToMps() {
        val s = Speed.fromFps(1.0)
        assertEquals(0.3048, s.metersPerSecond, absoluteTolerance = tolerance)
    }
}
