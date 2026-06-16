package org.openballistics.units

data class Percentage(val fraction: Double) {

    val percent: Double get() = fraction * 100.0

    companion object {
        fun fromPercent(percent: Double): Percentage = Percentage(percent / 100.0)
    }
}
