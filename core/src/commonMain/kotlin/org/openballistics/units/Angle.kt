package org.openballistics.units

import kotlin.math.PI

private const val RADIANS_PER_DEGREE = PI / 180.0
private const val RADIANS_PER_MOA = PI / (180.0 * 60.0)

data class Angle(val radians: Double) {

    val degrees: Double get() = radians / RADIANS_PER_DEGREE
    val moa: Double get() = radians / RADIANS_PER_MOA

    companion object {
        fun fromDegrees(degrees: Double): Angle = Angle(degrees * RADIANS_PER_DEGREE)
        fun fromMoa(moa: Double): Angle = Angle(moa * RADIANS_PER_MOA)
    }
}
