package org.openballistics.engine

import org.openballistics.model.*
import org.openballistics.units.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class BaselineComparisonTest {

    private fun make65cmInput(): BallisticInput = BallisticInput(
        zeroDistance = Distance(100.0),
        zeroAtmosphere = Atmosphere.STANDARD,
        sightHeight = Distance.fromMillimeters(90.0),
        twistRate = TwistRate(8.0, TwistRateUnit.INCHES, TwistDirection.RIGHT_HAND),
        barrelLength = null,
        dragModel = DragModel.G7,
        bulletBC = BallisticCoefficient.Single(0.314, DragModel.G7),
        bulletWeight = Mass.fromGrains(140.0),
        bulletDiameter = Distance.fromMillimeters(6.71),
        bulletLength = Distance.fromMillimeters(35.2),
        muzzleVelocity = Speed(870.0),
        targetDistance = Distance(1000.0),
        slope = Angle.fromDegrees(0.0),
        cant = Angle.fromDegrees(0.0),
        windZones = listOf(
            WindZone(Distance.ZERO, Distance(1000.0), ClockDirection(3),
                Speed(3.0), Speed(3.0), DataSource.MANUAL)
        ),
        atmosphere = Atmosphere.STANDARD,
        latitude = Angle.fromDegrees(0.0),
        azimuth = Angle.fromDegrees(0.0)
    )

    // py-ballisticcalc reference: 6.5 CM, flat, clock 3, 3 m/s, ICAO, no Coriolis
    private data class Ref(val dist: Double, val dropCm: Double, val windageCm: Double, val vel: Double, val tof: Double)
    private val reference = listOf(
        Ref(0.0, -9.00, 0.00, 870.00, 0.0),
        Ref(100.0, 0.00, -0.85, 820.17, 0.1184),
        Ref(200.0, -5.60, -3.56, 771.86, 0.2441),
        Ref(300.0, -27.69, -8.34, 725.15, 0.3777),
        Ref(500.0, -130.46, -24.81, 636.79, 0.6721),
        Ref(800.0, -476.60, -70.17, 515.73, 1.1956),
        Ref(1000.0, -885.45, -117.71, 441.37, 1.6148)
    )

    @Test
    fun velocityMatchesReference() {
        val input = make65cmInput()
        val solution = BallisticEngine.solve(input, Distance(100.0))

        println("Our engine vs py-ballisticcalc (6.5 CM, flat, clock 3, 3 m/s):")
        for (entry in solution.rangeTable) {
            val d = entry.distance.meters.toInt()
            val ref = reference.firstOrNull { it.dist.toInt() == d }
            val refStr = if (ref != null) "ref_vel=${ref.vel}" else ""
            println("  ${d}m: vel=${"%.2f".format(entry.velocity.metersPerSecond)} drop=${"%.2f".format(entry.drop.centimeters)}cm windage=${"%.2f".format(entry.drift.centimeters)}cm tof=${"%.4f".format(entry.timeOfFlight.toDouble(kotlin.time.DurationUnit.SECONDS))} $refStr")
        }

        for (ref in reference.drop(1)) {
            val entry = solution.rangeTable.minByOrNull { abs(it.distance.meters - ref.dist) } ?: continue
            if (abs(entry.distance.meters - ref.dist) > 5.0) continue

            val velDiff = abs(entry.velocity.metersPerSecond - ref.vel)
            assertTrue(velDiff < 1.0,
                "${ref.dist}m: velocity diff=${velDiff} (got=${entry.velocity.metersPerSecond}, expected=${ref.vel})")
        }
    }

    @Test
    fun windageMatchesReference() {
        val input = make65cmInput()
        val solution = BallisticEngine.solve(input, Distance(100.0))

        for (ref in reference.drop(1)) {
            val entry = solution.rangeTable.minByOrNull { abs(it.distance.meters - ref.dist) } ?: continue
            if (abs(entry.distance.meters - ref.dist) > 5.0) continue

            val windDiff = abs(entry.drift.centimeters - ref.windageCm)
            assertTrue(windDiff < 0.5,
                "${ref.dist}m: windage diff=${windDiff}cm (got=${entry.drift.centimeters}, expected=${ref.windageCm})")
        }
    }
}
