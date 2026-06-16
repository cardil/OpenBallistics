package org.openballistics.model

enum class TwistRateUnit {
    INCHES,
    MILLIMETERS
}

enum class TwistDirection {
    RIGHT_HAND,
    LEFT_HAND
}

data class TwistRate(
    val rate: Double,
    val unit: TwistRateUnit,
    val direction: TwistDirection
)
