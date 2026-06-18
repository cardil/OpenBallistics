package org.openballistics.engine

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.openballistics.model.*
import org.openballistics.units.*
import kotlin.math.abs
import kotlin.test.assertTrue

class FixtureTest {

    companion object {
        private const val VELOCITY_TOLERANCE = 1e-6
        private const val TOF_TOLERANCE = 1e-6
        private const val DROP_TOLERANCE = 1e-6
        private const val WINDAGE_TOLERANCE = 1e-6
        private const val ZERO_ANGLE_TOLERANCE = 1e-6
        private const val STABILITY_TOLERANCE = 1e-4
    }

    private data class SolvedFixture(
        val fixture: Fixture,
        val input: BallisticInput,
        val zeroAngle: Double,
        val sustained: List<TrajectoryPoint>,
        val gusts: List<TrajectoryPoint>?
    )

    private var seed: Int = 0

    private val solvedFixtures: List<SolvedFixture> by lazy {
        val resource = javaClass.getResourceAsStream("/fixtures.json")
            ?: error("fixtures.json not found")
        val json = Json { ignoreUnknownKeys = true }
        val ff = json.decodeFromString<FixtureFile>(resource.bufferedReader().readText())
        seed = ff.seed
        println("FIXTURE_SEED=${ff.seed}")
        ff.fixtures.mapNotNull { fixture ->
            val allCp = fixture.checkpoints + fixture.gusts_checkpoints
            if (allCp.isEmpty()) return@mapNotNull null
            val maxDist = allCp.maxOf { it.distance_m }
            val input = fixtureToInput(fixture.inputs, maxDist)
            val solver = TrajectorySolver(input)
            val zeroAngle = solver.findZeroAngle(input.zeroDistance)
            val sustained = solver.computeTrajectory(Distance(maxDist), Distance(1.0), zeroAngle)
            val gusts = if (fixture.gusts_checkpoints.isNotEmpty())
                solver.computeTrajectoryGusts(Distance(maxDist), Distance(1.0), zeroAngle)
            else null
            SolvedFixture(fixture, input, zeroAngle, sustained, gusts)
        }
    }

    @TestFactory
    fun zeroAngle(): List<DynamicTest> = solvedFixtures
        .filter { it.fixture.zero_angle_rad != 0.0 }
        .map { sf ->
            DynamicTest.dynamicTest("[seed=$seed] F${sf.fixture.id}(${sf.fixture.caliber}) zero_angle") {
                val delta = abs(sf.zeroAngle - sf.fixture.zero_angle_rad)
                assertTrue(delta <= ZERO_ANGLE_TOLERANCE,
                    "F${sf.fixture.id}(${sf.fixture.caliber}) zero_angle: want=${sf.fixture.zero_angle_rad} " +
                    "got=${sf.zeroAngle} Δ=${"%.4e".format(delta)} " +
                    "(${"%.1f".format(delta / ZERO_ANGLE_TOLERANCE)}× over tolerance)")
            }
        }

    @TestFactory
    fun stability(): List<DynamicTest> = solvedFixtures
        .filter { it.fixture.stability_coefficient != 0.0 }
        .map { sf ->
            DynamicTest.dynamicTest("[seed=$seed] F${sf.fixture.id}(${sf.fixture.caliber}) stability") {
                val sg = StabilityFactor.compute(
                    sf.input.twistRate, sf.input.bulletDiameter, sf.input.bulletLength,
                    sf.input.bulletWeight, sf.input.muzzleVelocity, sf.input.atmosphere
                )
                val delta = abs(sg - sf.fixture.stability_coefficient)
                assertTrue(delta <= STABILITY_TOLERANCE,
                    "F${sf.fixture.id}(${sf.fixture.caliber}) stability: want=${sf.fixture.stability_coefficient} " +
                    "got=${"%.6f".format(sg)} Δ=${"%.4e".format(delta)} " +
                    "(${"%.1f".format(delta / STABILITY_TOLERANCE)}× over tolerance)")
            }
        }

    @TestFactory
    fun velocity(): List<DynamicTest> = trajectoryTests("velocity", VELOCITY_TOLERANCE,
        expected = { it.velocity_mps },
        actual = { pt -> pt.velocity.metersPerSecond })

    @TestFactory
    fun tof(): List<DynamicTest> = trajectoryTests("tof", TOF_TOLERANCE,
        expected = { it.time_s },
        actual = { pt -> pt.timeOfFlight.toDouble(kotlin.time.DurationUnit.SECONDS) })

    @TestFactory
    fun drop(): List<DynamicTest> = trajectoryTests("drop", DROP_TOLERANCE,
        expected = { it.drop_cm },
        actual = { pt -> pt.drop.centimeters })

    @TestFactory
    fun windage(): List<DynamicTest> = trajectoryTests("windage", WINDAGE_TOLERANCE,
        expected = { it.windage_cm },
        actual = { pt -> pt.windage.centimeters })

    private fun trajectoryTests(
        fieldName: String,
        tolerance: Double,
        expected: (Checkpoint) -> Double,
        actual: (TrajectoryPoint) -> Double
    ): List<DynamicTest> {
        val tests = mutableListOf<DynamicTest>()
        for (sf in solvedFixtures) {
            fun addTests(checkpoints: List<Checkpoint>, trajectory: List<TrajectoryPoint>, type: String) {
                for (cp in checkpoints) {
                    val label = if (type == "gusts") "gusts_$fieldName" else fieldName
                    tests.add(DynamicTest.dynamicTest(
                        "[seed=$seed] F${sf.fixture.id}(${sf.fixture.caliber})@${cp.distance_m.toInt()}m $label"
                    ) {
                        val pt = trajectory.minByOrNull { abs(it.distance.meters - cp.distance_m) }
                            ?: error("No trajectory point near ${cp.distance_m}m")
                        assertTrue(abs(pt.distance.meters - cp.distance_m) <= 2.0,
                            "Nearest point ${pt.distance.meters}m too far from ${cp.distance_m}m")
                        val exp = expected(cp)
                        val got = actual(pt)
                        val delta = abs(got - exp)
                        assertTrue(delta <= tolerance,
                            "F${sf.fixture.id}(${sf.fixture.caliber})@${cp.distance_m}m $label: " +
                            "want=${"%.6f".format(exp)} got=${"%.6f".format(got)} Δ=${"%.4e".format(delta)} " +
                            "(${"%.1f".format(delta / tolerance)}× over tolerance)")
                    })
                }
            }
            addTests(sf.fixture.checkpoints, sf.sustained, "sustained")
            if (sf.gusts != null && sf.fixture.gusts_checkpoints.isNotEmpty()) {
                addTests(sf.fixture.gusts_checkpoints, sf.gusts!!, "gusts")
            }
        }
        return tests
    }

    private fun fixtureToInput(f: FixtureInputs, maxDistance: Double): BallisticInput {
        val dragModel = if (f.drag_model == "G7") DragModel.G7 else DragModel.G1
        val twistDir = if (f.twist_direction == "RH") TwistDirection.RIGHT_HAND else TwistDirection.LEFT_HAND
        val gustsSpeed = if (f.wind_gusts_mps > 0.0) f.wind_gusts_mps else f.wind_speed_mps
        return BallisticInput(
            zeroDistance = Distance(f.zero_distance_m),
            zeroAtmosphere = Atmosphere.STANDARD,
            sightHeight = Distance.fromMillimeters(f.sight_height_mm),
            twistRate = TwistRate(f.twist_inches, TwistRateUnit.INCHES, twistDir),
            barrelLength = null,
            dragModel = dragModel,
            bulletBC = BallisticCoefficient.Single(f.bc, dragModel),
            bulletWeight = Mass.fromGrains(f.bullet_weight_grains),
            bulletDiameter = Distance.fromMillimeters(f.bullet_diameter_mm),
            bulletLength = Distance.fromMillimeters(f.bullet_length_mm),
            muzzleVelocity = Speed(f.muzzle_velocity_mps),
            targetDistance = Distance(maxDistance),
            slope = Angle.fromDegrees(f.slope_deg),
            cant = Angle.fromDegrees(f.cant_deg),
            windZones = listOf(
                WindZone(
                    rangeStart = Distance.ZERO,
                    rangeEnd = Distance(maxDistance),
                    direction = ClockDirection(f.wind_clock),
                    sustained = Speed(f.wind_speed_mps),
                    gusts = Speed(gustsSpeed),
                    source = DataSource.MANUAL
                )
            ),
            atmosphere = AtmosphericData(
                temperature = Temperature(f.temperature_c),
                pressure = Pressure(f.pressure_hpa),
                humidity = Percentage.fromPercent(f.humidity_pct),
                altitude = Distance(f.altitude_m)
            ),
            latitude = Angle.fromDegrees(f.latitude_deg),
            azimuth = Angle.fromDegrees(f.azimuth_deg)
        )
    }
}
