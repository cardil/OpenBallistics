package org.openballistics.model

data class ClockDirection(val hour: Int) {
    init {
        require(hour in 1..12) { "Clock direction must be between 1 and 12, got $hour" }
    }
}
