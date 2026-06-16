package org.openballistics

import org.openballistics.units.Distance
import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceTest {

    private val tolerance = 1e-10

    @Test
    fun constructionFromSI() {
        val d = Distance(1.0)
        assertEquals(1.0, d.meters, absoluteTolerance = tolerance)
    }

    @Test
    fun yardsRoundTrip() {
        val yards = 100.0
        assertEquals(yards, Distance.fromYards(yards).yards, absoluteTolerance = tolerance)
    }

    @Test
    fun inchesRoundTrip() {
        val inches = 30.0
        assertEquals(inches, Distance.fromInches(inches).inches, absoluteTolerance = tolerance)
    }

    @Test
    fun millimetersRoundTrip() {
        val mm = 308.0
        assertEquals(mm, Distance.fromMillimeters(mm).millimeters, absoluteTolerance = tolerance)
    }

    @Test
    fun centimetersRoundTrip() {
        val cm = 50.0
        assertEquals(cm, Distance.fromCentimeters(cm).centimeters, absoluteTolerance = tolerance)
    }

    @Test
    fun metersToYards() {
        val d = Distance(0.9144)
        assertEquals(1.0, d.yards, absoluteTolerance = tolerance)
    }

    @Test
    fun metersToInches() {
        val d = Distance(0.0254)
        assertEquals(1.0, d.inches, absoluteTolerance = tolerance)
    }

    @Test
    fun fromYardsToMeters() {
        val d = Distance.fromYards(1.0)
        assertEquals(0.9144, d.meters, absoluteTolerance = tolerance)
    }
}
