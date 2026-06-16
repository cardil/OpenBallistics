package org.openballistics.model

import org.openballistics.units.Distance
import org.openballistics.units.Speed

data class WindZone(
    val rangeStart: Distance,
    val rangeEnd: Distance,
    val direction: ClockDirection,
    val sustained: Speed,
    val gusts: Speed,
    val source: DataSource
)
