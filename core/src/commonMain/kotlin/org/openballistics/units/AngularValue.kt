package org.openballistics.units

import kotlin.math.PI

private const val MRAD_PER_MOA = PI / (180.0 * 60.0) * 1000.0
private const val MRAD_PER_RADIAN = 1000.0

data class AngularValue(val milliradians: Double) {

    val moa: Double get() = milliradians / MRAD_PER_MOA
    val radians: Double get() = milliradians / MRAD_PER_RADIAN

    companion object {
        fun fromMoa(moa: Double): AngularValue = AngularValue(moa * MRAD_PER_MOA)
        fun fromRadians(radians: Double): AngularValue = AngularValue(radians * MRAD_PER_RADIAN)
    }
}
