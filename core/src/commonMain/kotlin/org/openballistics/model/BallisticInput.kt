package org.openballistics.model

import org.openballistics.units.Angle
import org.openballistics.units.AngularValue
import org.openballistics.units.Distance
import org.openballistics.units.Mass
import org.openballistics.units.Speed

data class BallisticInput(
    val zeroDistance: Distance,
    val zeroAtmosphere: AtmosphericData,
    val sightHeight: Distance,
    val twistRate: TwistRate,
    val barrelLength: Distance?,
    val dragModel: DragModel,
    val bulletBC: BallisticCoefficient,
    val bulletWeight: Mass,
    val bulletDiameter: Distance,
    val bulletLength: Distance,
    val muzzleVelocity: Speed,
    val targetDistance: Distance,
    val slope: Angle,
    val cant: Angle,
    val windZones: List<WindZone>,
    val atmosphere: AtmosphericData,
    val latitude: Angle,
    val azimuth: Angle
)
