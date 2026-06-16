package org.openballistics.model

import org.openballistics.units.Distance
import org.openballistics.units.Percentage
import org.openballistics.units.Pressure
import org.openballistics.units.Temperature

data class AtmosphericData(
    val temperature: Temperature,
    val pressure: Pressure,
    val humidity: Percentage,
    val altitude: Distance
)
