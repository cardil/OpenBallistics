package org.openballistics

import org.openballistics.units.Temperature
import kotlin.test.Test
import kotlin.test.assertEquals

class TemperatureTest {

    private val tolerance = 1e-10

    @Test
    fun constructionFromCelsius() {
        val t = Temperature(20.0)
        assertEquals(20.0, t.celsius, absoluteTolerance = tolerance)
    }

    @Test
    fun celsiusToFahrenheit() {
        val t = Temperature(0.0)
        assertEquals(32.0, t.fahrenheit, absoluteTolerance = tolerance)
    }

    @Test
    fun boilingPoint() {
        val t = Temperature(100.0)
        assertEquals(212.0, t.fahrenheit, absoluteTolerance = tolerance)
    }

    @Test
    fun fromFahrenheitRoundTrip() {
        val f = 98.6
        assertEquals(f, Temperature.fromFahrenheit(f).fahrenheit, absoluteTolerance = tolerance)
    }

    @Test
    fun fromFahrenheitToCelsius() {
        val t = Temperature.fromFahrenheit(32.0)
        assertEquals(0.0, t.celsius, absoluteTolerance = tolerance)
    }
}
