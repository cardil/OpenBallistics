package org.openballistics

import org.openballistics.units.Percentage
import kotlin.test.Test
import kotlin.test.assertEquals

class PercentageTest {

    private val tolerance = 1e-12

    @Test
    fun fractionToPercent() {
        val p = Percentage(0.45)
        assertEquals(45.0, p.percent, absoluteTolerance = tolerance)
    }

    @Test
    fun fromPercentToFraction() {
        val p = Percentage.fromPercent(45.0)
        assertEquals(0.45, p.fraction, absoluteTolerance = tolerance)
    }

    @Test
    fun zeroPercent() {
        val p = Percentage.fromPercent(0.0)
        assertEquals(0.0, p.fraction, absoluteTolerance = tolerance)
    }

    @Test
    fun hundredPercent() {
        val p = Percentage.fromPercent(100.0)
        assertEquals(1.0, p.fraction, absoluteTolerance = tolerance)
    }

    @Test
    fun roundTrip() {
        val percent = 72.5
        assertEquals(percent, Percentage.fromPercent(percent).percent, absoluteTolerance = tolerance)
    }
}
