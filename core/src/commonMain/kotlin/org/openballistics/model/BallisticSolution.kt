package org.openballistics.model

import org.openballistics.units.AngularValue
import org.openballistics.units.Distance
import org.openballistics.units.Energy
import org.openballistics.units.Speed
import kotlin.time.Duration

data class RangeEntry(
    val distance: Distance,
    val elevation: AngularValue,
    val windage: AngularValue,
    val drop: Distance,
    val drift: Distance,
    val velocity: Speed,
    val energy: Energy,
    val timeOfFlight: Duration
)

data class BallisticSolution(
    val elevation: AngularValue,
    val windage: AngularValue,
    val elevationGusts: AngularValue,
    val windageGusts: AngularValue,
    val drop: Distance,
    val drift: Distance,
    val velocity: Speed,
    val energy: Energy,
    val timeOfFlight: Duration,
    val rangeTable: List<RangeEntry>
)
