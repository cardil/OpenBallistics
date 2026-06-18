package org.openballistics.engine

import org.openballistics.model.*
import org.openballistics.units.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class BallisticEngineTest {

    private fun make308Input(
        distance: Double = 1000.0,
        windClock: Int = 3,
        windSpeed: Double = 3.0,
        slopeDeg: Double = 0.0,
        cantDeg: Double = 0.0,
        latDeg: Double = 52.0,
        azDeg: Double = 90.0,
        tempC: Double = 15.0,
        pressureHpa: Double = 1013.25,
        humidityPct: Double = 50.0,
        altitudeM: Double = 0.0
    ): BallisticInput = BallisticInput(
        zeroDistance = Distance(100.0),
        zeroAtmosphere = Atmosphere.STANDARD,
        sightHeight = Distance.fromMillimeters(90.0),
        twistRate = TwistRate(11.0, TwistRateUnit.INCHES, TwistDirection.RIGHT_HAND),
        barrelLength = Distance.fromMillimeters(660.0),
        dragModel = DragModel.G7,
        bulletBC = BallisticCoefficient.Single(0.260, DragModel.G7),
        bulletWeight = Mass.fromGrains(175.0),
        bulletDiameter = Distance.fromMillimeters(7.82),
        bulletLength = Distance.fromMillimeters(31.5),
        muzzleVelocity = Speed(780.0),
        targetDistance = Distance(distance),
        slope = Angle.fromDegrees(slopeDeg),
        cant = Angle.fromDegrees(cantDeg),
        windZones = listOf(
            WindZone(
                rangeStart = Distance.ZERO,
                rangeEnd = Distance(distance),
                direction = ClockDirection(windClock),
                sustained = Speed(windSpeed),
                gusts = Speed(windSpeed * 1.5),
                source = DataSource.MANUAL
            )
        ),
        atmosphere = AtmosphericData(
            temperature = Temperature(tempC),
            pressure = Pressure(pressureHpa),
            humidity = Percentage.fromPercent(humidityPct),
            altitude = Distance(altitudeM)
        ),
        latitude = Angle.fromDegrees(latDeg),
        azimuth = Angle.fromDegrees(azDeg)
    )

    @Test
    fun solveProducesNonzeroElevationAndWindage() {
        val solution = BallisticEngine.solve(make308Input())
        assertTrue(solution.elevation.milliradians != 0.0, "Elevation should be nonzero")
        assertTrue(solution.windage.milliradians != 0.0, "Windage should be nonzero")
    }

    @Test
    fun dropIsNegativeAtLongRange() {
        val solution = BallisticEngine.solve(make308Input(distance = 800.0))
        assertTrue(solution.drop.meters < 0.0, "Drop should be negative (below sight line) at 800m")
    }

    @Test
    fun velocityDecreases() {
        val solution = BallisticEngine.solve(make308Input(distance = 600.0))
        assertTrue(
            solution.velocity.metersPerSecond < 780.0,
            "Velocity at 600m should be less than muzzle: ${solution.velocity.metersPerSecond}"
        )
        assertTrue(
            solution.velocity.metersPerSecond > 100.0,
            "Velocity should still be positive"
        )
    }

    @Test
    fun rangeCardHasMultipleEntries() {
        val solution = BallisticEngine.solve(make308Input(distance = 1000.0), rangeStep = Distance(100.0))
        assertTrue(
            solution.rangeTable.size >= 10,
            "Range card should have at least 10 entries for 1000m/100m step, got ${solution.rangeTable.size}"
        )
    }

    @Test
    fun rangeCardDistancesAreIncreasing() {
        val solution = BallisticEngine.solve(make308Input(distance = 500.0), rangeStep = Distance(50.0))
        for (i in 1 until solution.rangeTable.size) {
            assertTrue(
                solution.rangeTable[i].distance.meters >= solution.rangeTable[i - 1].distance.meters,
                "Range card distances should be increasing"
            )
        }
    }

    @Test
    fun gustsCorrectionsExist() {
        val solution = BallisticEngine.solve(make308Input())
        assertTrue(solution.elevationGusts.milliradians != 0.0)
        assertTrue(solution.windageGusts.milliradians != 0.0)
    }

    @Test
    fun slopeChangesElevation() {
        val flat = BallisticEngine.solve(make308Input(slopeDeg = 0.0, windClock = 12, windSpeed = 0.5))
        val uphill = BallisticEngine.solve(make308Input(slopeDeg = 15.0, windClock = 12, windSpeed = 0.5))
        assertTrue(flat.elevation.milliradians > 0, "Flat elevation should be positive: ${flat.elevation.milliradians}")
        assertTrue(uphill.elevation.milliradians > 0, "Uphill elevation should be positive: ${uphill.elevation.milliradians}")
        assertTrue(
            abs(flat.elevation.milliradians - uphill.elevation.milliradians) < abs(flat.elevation.milliradians),
            "Slope should change elevation but not dramatically: flat=${flat.elevation.milliradians}, uphill=${uphill.elevation.milliradians}"
        )
    }

    @Test
    fun timeOfFlightIsPositive() {
        val solution = BallisticEngine.solve(make308Input(distance = 500.0))
        assertTrue(solution.timeOfFlight > 0.milliseconds, "TOF should be positive")
    }

    @Test
    fun energyIsPositive() {
        val solution = BallisticEngine.solve(make308Input(distance = 500.0))
        assertTrue(solution.energy.joules > 0.0, "Energy should be positive")
    }

    @Test
    fun benchmarkSingleSolution() {
        val input = make308Input(distance = 1000.0)
        val mark = kotlin.time.TimeSource.Monotonic.markNow()
        repeat(10) {
            BallisticEngine.solve(input)
        }
        val elapsed = mark.elapsedNow().inWholeMilliseconds
        val perSolution = elapsed / 10
        assertTrue(perSolution < 200, "Single solution should be <200ms, got ${perSolution}ms")
    }

    @Test
    fun benchmarkRangeCard() {
        val input = make308Input(distance = 2000.0)
        val mark = kotlin.time.TimeSource.Monotonic.markNow()
        val solution = BallisticEngine.solve(input, rangeStep = Distance(25.0))
        val elapsed = mark.elapsedNow().inWholeMilliseconds
        assertTrue(solution.rangeTable.size >= 70, "Range card should have at least 70 entries for 2000m/25m")
        assertTrue(elapsed < 500, "Range card should be <500ms, got ${elapsed}ms")
    }
}
