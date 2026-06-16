package org.openballistics.units

data class Temperature(val celsius: Double) {

    val fahrenheit: Double get() = celsius * 9.0 / 5.0 + 32.0

    companion object {
        fun fromFahrenheit(fahrenheit: Double): Temperature = Temperature((fahrenheit - 32.0) * 5.0 / 9.0)
    }
}
