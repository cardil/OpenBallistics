package org.openballistics.units

private const val JOULES_PER_FOOT_POUND = 1.3558179483

data class Energy(val joules: Double) {

    val footPounds: Double get() = joules / JOULES_PER_FOOT_POUND

    companion object {
        val ZERO = Energy(0.0)
        fun fromFootPounds(footPounds: Double): Energy = Energy(footPounds * JOULES_PER_FOOT_POUND)
    }
}
