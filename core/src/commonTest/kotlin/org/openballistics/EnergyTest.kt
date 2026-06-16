package org.openballistics

import org.openballistics.units.Energy
import kotlin.test.Test
import kotlin.test.assertEquals

class EnergyTest {

    private val tolerance = 1e-8

    @Test
    fun constructionFromJoules() {
        val e = Energy(1000.0)
        assertEquals(1000.0, e.joules, absoluteTolerance = tolerance)
    }

    @Test
    fun footPoundsRoundTrip() {
        val fp = 2500.0
        assertEquals(fp, Energy.fromFootPounds(fp).footPounds, absoluteTolerance = tolerance)
    }

    @Test
    fun footPoundsToJoules() {
        val e = Energy.fromFootPounds(1.0)
        assertEquals(1.3558179483, e.joules, absoluteTolerance = 1e-9)
    }
}
