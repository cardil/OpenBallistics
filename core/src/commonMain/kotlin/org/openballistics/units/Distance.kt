package org.openballistics.units

private const val METERS_PER_YARD = 0.9144
private const val METERS_PER_INCH = 0.0254
private const val METERS_PER_MM = 0.001
private const val METERS_PER_CM = 0.01
private const val METERS_PER_FOOT = 0.3048

data class Distance(val meters: Double) : Comparable<Distance> {

    val yards: Double get() = meters / METERS_PER_YARD
    val inches: Double get() = meters / METERS_PER_INCH
    val millimeters: Double get() = meters / METERS_PER_MM
    val centimeters: Double get() = meters / METERS_PER_CM
    val feet: Double get() = meters / METERS_PER_FOOT

    operator fun plus(other: Distance): Distance = Distance(meters + other.meters)
    operator fun minus(other: Distance): Distance = Distance(meters - other.meters)
    operator fun times(scalar: Double): Distance = Distance(meters * scalar)
    operator fun div(scalar: Double): Distance = Distance(meters / scalar)
    operator fun div(other: Distance): Double = meters / other.meters
    operator fun unaryMinus(): Distance = Distance(-meters)
    override fun compareTo(other: Distance): Int = meters.compareTo(other.meters)

    companion object {
        val ZERO = Distance(0.0)
        fun fromYards(yards: Double): Distance = Distance(yards * METERS_PER_YARD)
        fun fromInches(inches: Double): Distance = Distance(inches * METERS_PER_INCH)
        fun fromMillimeters(mm: Double): Distance = Distance(mm * METERS_PER_MM)
        fun fromCentimeters(cm: Double): Distance = Distance(cm * METERS_PER_CM)
        fun fromFeet(feet: Double): Distance = Distance(feet * METERS_PER_FOOT)
    }
}
