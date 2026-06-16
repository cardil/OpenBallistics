package org.openballistics.model

import org.openballistics.units.Distance

data class RifleProfile(
    val id: String,
    val name: String,
    val zeroDistance: Distance,
    val zeroAtmosphere: AtmosphericData,
    val sightHeight: Distance,
    val twistRate: TwistRate,
    val barrelLength: Distance?,
    val scope: ScopeProfile,
    val ammunition: List<AmmunitionProfile>
)
