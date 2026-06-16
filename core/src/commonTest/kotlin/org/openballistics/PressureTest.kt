package org.openballistics

import org.openballistics.units.Pressure
import kotlin.test.Test
import kotlin.test.assertEquals

class PressureTest {

    private val tolerance = 1e-8

    @Test
    fun constructionFromHpa() {
        val p = Pressure(1013.25)
        assertEquals(1013.25, p.hectopascals, absoluteTolerance = tolerance)
    }

    @Test
    fun mmHgRoundTrip() {
        val mmHg = 760.0
        assertEquals(mmHg, Pressure.fromMmHg(mmHg).mmHg, absoluteTolerance = tolerance)
    }

    @Test
    fun inHgRoundTrip() {
        val inHg = 29.92
        assertEquals(inHg, Pressure.fromInHg(inHg).inHg, absoluteTolerance = tolerance)
    }
}
