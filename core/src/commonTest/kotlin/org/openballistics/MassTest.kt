package org.openballistics

import org.openballistics.units.Mass
import kotlin.test.Test
import kotlin.test.assertEquals

class MassTest {

    private val tolerance = 1e-12

    @Test
    fun constructionFromSI() {
        val m = Mass(1.0)
        assertEquals(1.0, m.kilograms, absoluteTolerance = tolerance)
    }

    @Test
    fun grainsRoundTrip() {
        val grains = 175.0
        assertEquals(grains, Mass.fromGrains(grains).grains, absoluteTolerance = tolerance)
    }

    @Test
    fun gramsRoundTrip() {
        val grams = 11.34
        assertEquals(grams, Mass.fromGrams(grams).grams, absoluteTolerance = tolerance)
    }

    @Test
    fun grainsToKg() {
        val m = Mass.fromGrains(1.0)
        assertEquals(6.479891e-5, m.kilograms, absoluteTolerance = 1e-15)
    }
}
