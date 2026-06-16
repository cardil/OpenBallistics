package org.openballistics.model

import org.openballistics.units.Speed

data class BcSegment(
    val velocity: Speed,
    val bc: Double
)

sealed class BallisticCoefficient {
    data class Single(val value: Double, val model: DragModel) : BallisticCoefficient()
    data class Segmented(val segments: List<BcSegment>, val model: DragModel) : BallisticCoefficient()
}
