package org.openballistics.engine

import org.openballistics.model.*
import org.openballistics.units.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class IncrementalComparisonTest {

    private fun makeInput(
        slopeDeg: Double = 0.0,
        windClock: Int = 12,
        windSpeed: Double = 0.0
    ): BallisticInput = BallisticInput(
        zeroDistance = Distance(100.0),
        zeroAtmosphere = Atmosphere.STANDARD,
        sightHeight = Distance.fromMillimeters(90.0),
        twistRate = TwistRate(11.25, TwistRateUnit.INCHES, TwistDirection.RIGHT_HAND),
        barrelLength = null,
        dragModel = DragModel.G7,
        bulletBC = BallisticCoefficient.Single(0.260, DragModel.G7),
        bulletWeight = Mass.fromGrains(175.0),
        bulletDiameter = Distance.fromMillimeters(7.82),
        bulletLength = Distance.fromMillimeters(31.5),
        muzzleVelocity = Speed(780.0),
        targetDistance = Distance(400.0),
        slope = Angle.fromDegrees(slopeDeg),
        cant = Angle.fromDegrees(0.0),
        windZones = listOf(
            WindZone(Distance.ZERO, Distance(400.0), ClockDirection(windClock),
                Speed(windSpeed), Speed(windSpeed), DataSource.MANUAL)
        ),
        atmosphere = Atmosphere.STANDARD,
        latitude = Angle.fromDegrees(0.0),
        azimuth = Angle.fromDegrees(0.0)
    )

    // py-ballisticcalc reference at 400m slant range:
    // flat, no wind:           v=568.3003  h=-99.00cm  w=3.86cm  t=0.601134
    // slope +12, no wind:      v=563.7446  h=8398.30cm w=4.05cm  t=0.616846
    // slope -8, no wind:       v=566.5556  h=-5722.77  w=3.94cm  t=0.608103
    // flat, wind clock3 3mps:  v=568.3001  h=-99.00cm  w=-22.64  t=0.601134
    // slope+12, wind clock3:   v=563.7444  h=8398.30   w=-23.67  t=0.616847

    private fun solveAt400(slopeDeg: Double, windClock: Int = 12, windSpeed: Double = 0.0): Map<String, Double> {
        val input = makeInput(slopeDeg, windClock, windSpeed)
        val solution = BallisticEngine.solve(input, Distance(100.0))
        println("Range table distances: ${solution.rangeTable.map { "%.1f".format(it.distance.meters) }}")
        val entry = solution.rangeTable.minByOrNull { abs(it.distance.meters - 400.0) }!!
        return mapOf(
            "vel" to entry.velocity.metersPerSecond,
            "drop" to entry.drop.centimeters,
            "windage" to entry.drift.centimeters,
            "tof" to entry.timeOfFlight.toDouble(kotlin.time.DurationUnit.SECONDS)
        )
    }

    @Test
    fun step1_flatNoWind() {
        val r = solveAt400(0.0)
        val msg = "flat, no wind: v=${"%.4f".format(r["vel"])} h=${"%.2f".format(r["drop"])} w=${"%.2f".format(r["windage"])} t=${"%.6f".format(r["tof"])}"
        println(msg)
        assertTrue(abs(r["vel"]!! - 568.3003) < 1.0, "$msg | vel diff=${abs(r["vel"]!! - 568.3003)}")
        assertTrue(abs(r["tof"]!! - 0.601134) < 0.005, "$msg | tof diff=${abs(r["tof"]!! - 0.601134)}")
    }

    @Test
    fun step2_slopeUp12NoWind() {
        val r = solveAt400(12.0)
        println("slope+12, no wind: v=${"%.4f".format(r["vel"])} h=${"%.2f".format(r["drop"])} w=${"%.2f".format(r["windage"])} t=${"%.6f".format(r["tof"])}")
        assertTrue(abs(r["vel"]!! - 563.7446) < 1.0, "vel: ${r["vel"]}")
        assertTrue(abs(r["tof"]!! - 0.616846) < 0.005, "tof: ${r["tof"]}")
    }

    @Test
    fun step3_slopeDown8NoWind() {
        val r = solveAt400(-8.0)
        println("slope-8, no wind: v=${"%.4f".format(r["vel"])} h=${"%.2f".format(r["drop"])} w=${"%.2f".format(r["windage"])} t=${"%.6f".format(r["tof"])}")
        assertTrue(abs(r["vel"]!! - 566.5556) < 1.0, "vel: ${r["vel"]}")
        assertTrue(abs(r["tof"]!! - 0.608103) < 0.005, "tof: ${r["tof"]}")
    }

    @Test
    fun step4_flatWindClock3() {
        val r = solveAt400(0.0, windClock = 3, windSpeed = 3.0)
        println("flat, wind 3h 3mps: v=${"%.4f".format(r["vel"])} h=${"%.2f".format(r["drop"])} w=${"%.2f".format(r["windage"])} t=${"%.6f".format(r["tof"])}")
        assertTrue(abs(r["vel"]!! - 568.3001) < 1.0, "vel: ${r["vel"]}")
        assertTrue(abs(r["windage"]!! - (-22.64)) < 0.5, "windage: ${r["windage"]}")
    }

    @Test
    fun step5_slope12WindClock3() {
        val r = solveAt400(12.0, windClock = 3, windSpeed = 3.0)
        println("slope+12, wind 3h 3mps: v=${"%.4f".format(r["vel"])} h=${"%.2f".format(r["drop"])} w=${"%.2f".format(r["windage"])} t=${"%.6f".format(r["tof"])}")
        assertTrue(abs(r["vel"]!! - 563.7444) < 1.0, "vel: ${r["vel"]}")
        assertTrue(abs(r["tof"]!! - 0.616847) < 0.005, "tof: ${r["tof"]}")
    }
}
