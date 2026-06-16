package org.openballistics.model

import org.openballistics.units.Distance
import org.openballistics.units.Mass

data class BulletProfile(
    val name: String,
    val bcG1: Double?,
    val bcG7: Double?,
    val bcG7Segmented: List<BcSegment>?,
    val preferredDragModel: DragModel,
    val weight: Mass,
    val diameter: Distance,
    val length: Distance
)
