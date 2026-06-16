package org.openballistics.model

import org.openballistics.units.Distance
import org.openballistics.units.Mass
import org.openballistics.units.Speed
import org.openballistics.units.Temperature

data class VelocityEntry(
    val temperature: Temperature,
    val velocity: Speed,
    val isEstimate: Boolean
)

data class AmmunitionProfile(
    val id: String,
    val name: String,
    val source: AmmunitionSource,
    val bullet: BulletProfile,
    val velocityTable: List<VelocityEntry>,
    val brass: String?,
    val primer: String?,
    val powder: String?,
    val charge: Mass?,
    val oal: Distance?,
    val cbto: Distance?
)
