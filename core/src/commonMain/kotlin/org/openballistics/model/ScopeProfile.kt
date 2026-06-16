package org.openballistics.model

import org.openballistics.units.AngularValue
import org.openballistics.units.Distance

data class ScopeProfile(
    val model: String,
    val focalPlane: FocalPlane,
    val zoomRange: ClosedFloatingPointRange<Double>,
    val clickValue: AngularValue,
    val actualClickValue: AngularValue?,
    val maxElevation: AngularValue,
    val maxWindage: AngularValue,
    val tubeDiameter: Distance
)
