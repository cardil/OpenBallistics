package org.openballistics.units

private const val METERS_PER_YARD = 0.9144
private const val METERS_PER_INCH = 0.0254
private const val METERS_PER_MM = 0.001
private const val METERS_PER_CM = 0.01

data class Distance(val meters: Double) {

    val yards: Double get() = meters / METERS_PER_YARD
    val inches: Double get() = meters / METERS_PER_INCH
    val millimeters: Double get() = meters / METERS_PER_MM
    val centimeters: Double get() = meters / METERS_PER_CM

    companion object {
        fun fromYards(yards: Double): Distance = Distance(yards * METERS_PER_YARD)
        fun fromInches(inches: Double): Distance = Distance(inches * METERS_PER_INCH)
        fun fromMillimeters(mm: Double): Distance = Distance(mm * METERS_PER_MM)
        fun fromCentimeters(cm: Double): Distance = Distance(cm * METERS_PER_CM)
    }
}
