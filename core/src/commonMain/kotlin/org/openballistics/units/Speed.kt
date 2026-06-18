package org.openballistics.units

private const val MPS_PER_FPS = 0.3048
private const val MPS_PER_MPH = 0.44704
private const val MPS_PER_KNOT = 0.5144444444
private const val MPS_PER_KMH = 1.0 / 3.6

data class Speed(val metersPerSecond: Double) : Comparable<Speed> {

    val fps: Double get() = metersPerSecond / MPS_PER_FPS
    val mph: Double get() = metersPerSecond / MPS_PER_MPH
    val knots: Double get() = metersPerSecond / MPS_PER_KNOT
    val kmh: Double get() = metersPerSecond / MPS_PER_KMH

    operator fun plus(other: Speed): Speed = Speed(metersPerSecond + other.metersPerSecond)
    operator fun minus(other: Speed): Speed = Speed(metersPerSecond - other.metersPerSecond)
    operator fun times(scalar: Double): Speed = Speed(metersPerSecond * scalar)
    operator fun div(scalar: Double): Speed = Speed(metersPerSecond / scalar)
    override fun compareTo(other: Speed): Int = metersPerSecond.compareTo(other.metersPerSecond)

    companion object {
        val ZERO = Speed(0.0)
        fun fromFps(fps: Double): Speed = Speed(fps * MPS_PER_FPS)
        fun fromMph(mph: Double): Speed = Speed(mph * MPS_PER_MPH)
        fun fromKnots(knots: Double): Speed = Speed(knots * MPS_PER_KNOT)
        fun fromKmh(kmh: Double): Speed = Speed(kmh * MPS_PER_KMH)
    }
}
