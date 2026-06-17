package org.openballistics.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.openballistics.model.*
import org.openballistics.units.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

@Serializable
data class FixtureFile(val seed: Int, val fixtures: List<Fixture>)

@Serializable
data class Fixture(
    val id: Int,
    val caliber: String,
    val inputs: FixtureInputs,
    val checkpoints: List<Checkpoint>,
    val gusts_checkpoints: List<Checkpoint> = emptyList(),
    val zero_angle_rad: Double = 0.0,
    val stability_coefficient: Double = 0.0
)

@Serializable
data class FixtureInputs(
    val bc: Double,
    val drag_model: String,
    val bullet_weight_grains: Double,
    val bullet_diameter_mm: Double,
    val bullet_length_mm: Double,
    val muzzle_velocity_mps: Double,
    val zero_distance_m: Double,
    val sight_height_mm: Double,
    val twist_inches: Double,
    val twist_direction: String,
    val temperature_c: Double,
    val pressure_hpa: Double,
    val humidity_pct: Double,
    val altitude_m: Double,
    val wind_clock: Int,
    val wind_speed_mps: Double,
    val wind_gusts_mps: Double = 0.0,
    val slope_deg: Double,
    val cant_deg: Double,
    val latitude_deg: Double,
    val azimuth_deg: Double
)

@Serializable
data class Checkpoint(
    val distance_m: Double,
    val drop_cm: Double,
    val windage_cm: Double,
    val velocity_mps: Double,
    val time_s: Double
)

class FixtureTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadFixtures(): FixtureFile? {
        val resource = this::class.java.getResourceAsStream("/fixtures.json") ?: return null
        val content = resource.bufferedReader().readText()
        return json.decodeFromString<FixtureFile>(content)
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

    @Test
    fun fixtureVelocityWithinTolerance() {
        val fixtureFile = loadFixtures() ?: run {
            println("fixtures.json not found, skipping fixture tests")
            return
        }

        println("FIXTURE_SEED=${fixtureFile.seed}")
        val velocityTolerance = 0.01
        val tofTolerance = 0.00005
        val dropTolerance = 0.01
        val windageTolerance = 0.01

        val reportDir = java.io.File("build/reports/tests")
        reportDir.mkdirs()
        val csv = java.io.PrintWriter(java.io.File(reportDir, "parity.csv").bufferedWriter())
        csv.println("fixture_id,caliber,type,distance_m,field,expected,got,delta")

        var totalCheckpoints = 0
        val failures = mutableMapOf(
            "velocity" to 0, "tof" to 0, "drop" to 0, "windage" to 0,
            "gusts_velocity" to 0, "gusts_tof" to 0, "gusts_drop" to 0, "gusts_windage" to 0
        )
        val failureDetails = mutableListOf<String>()

        for (fixture in fixtureFile.fixtures) {
            val allCheckpoints = fixture.checkpoints + fixture.gusts_checkpoints
            if (allCheckpoints.isEmpty()) continue
            val maxDist = allCheckpoints.maxOf { it.distance_m }
            val input = fixtureToInput(fixture.inputs, maxDist)
            val solver = TrajectorySolver(input)
            val zeroAngle = solver.findZeroAngle(input.zeroDistance)

            if (fixture.zero_angle_rad != 0.0) {
                val zaDiff = abs(zeroAngle - fixture.zero_angle_rad)
                csv.println("${fixture.id},${fixture.caliber},sustained,0.0,zero_angle,${fixture.zero_angle_rad},$zeroAngle,$zaDiff")
                if (zaDiff > 1e-6) {
                    failures["zero_angle"] = (failures["zero_angle"] ?: 0) + 1
                    if (failureDetails.size < 15) {
                        failureDetails.add(
                            "F${fixture.id}(${fixture.caliber}) zero_angle: " +
                                "exp=${"%.8f".format(fixture.zero_angle_rad)} got=${"%.8f".format(zeroAngle)} Δ=${"%.8f".format(zaDiff)}"
                        )
                    }
                }
            }

            if (fixture.stability_coefficient != 0.0) {
                val sg = StabilityFactor.compute(
                    input.twistRate, input.bulletDiameter, input.bulletLength,
                    input.bulletWeight, input.muzzleVelocity, input.atmosphere
                )
                val sgDiff = abs(sg - fixture.stability_coefficient)
                csv.println("${fixture.id},${fixture.caliber},sustained,0.0,stability,${"%.6f".format(fixture.stability_coefficient)},${"%.6f".format(sg)},$sgDiff")
                if (sgDiff > 1e-4) {
                    failures["stability"] = (failures["stability"] ?: 0) + 1
                    if (failureDetails.size < 15) {
                        failureDetails.add(
                            "F${fixture.id}(${fixture.caliber}) stability: " +
                                "exp=${"%.6f".format(fixture.stability_coefficient)} got=${"%.6f".format(sg)} Δ=${"%.6f".format(sgDiff)}"
                        )
                    }
                }
            }

            fun verify(
                checkpoints: List<Checkpoint>,
                trajectory: List<TrajectoryPoint>,
                prefix: String
            ) {
                for (cp in checkpoints) {
                    totalCheckpoints++
                    val nearest = trajectory.minByOrNull { abs(it.distance.meters - cp.distance_m) }
                        ?: fail("F${fixture.id}(${fixture.caliber}): no trajectory point for ${prefix}checkpoint at ${cp.distance_m}m")
                    if (abs(nearest.distance.meters - cp.distance_m) > 2.0) {
                        fail("F${fixture.id}(${fixture.caliber}): nearest point ${nearest.distance.meters}m too far from ${prefix}checkpoint ${cp.distance_m}m")
                    }

                    val trajType = if (prefix.isEmpty()) "sustained" else "gusts"

                    fun check(field: String, got: Double, expected: Double, tolerance: Double) {
                        val diff = abs(got - expected)
                        csv.println("${fixture.id},${fixture.caliber},$trajType,${cp.distance_m},$field,$expected,$got,$diff")
                        if (diff > tolerance) {
                            val key = "$prefix$field"
                            failures[key] = (failures[key] ?: 0) + 1
                            if (failureDetails.size < 15) {
                                failureDetails.add(
                                    "F${fixture.id}(${fixture.caliber})@${cp.distance_m}m $key: " +
                                        "exp=${"%.4f".format(expected)} got=${"%.4f".format(got)} Δ=${"%.4f".format(diff)}"
                                )
                            }
                        }
                    }

                    check("velocity", nearest.velocity.metersPerSecond, cp.velocity_mps, velocityTolerance)
                    val tofSec = nearest.timeOfFlight.toDouble(kotlin.time.DurationUnit.SECONDS)
                    check("tof", tofSec, cp.time_s, tofTolerance)
                    check("drop", nearest.drop.centimeters, cp.drop_cm, maxOf(dropTolerance, abs(cp.drop_cm) * 0.02))
                    check("windage", nearest.windage.centimeters, cp.windage_cm, maxOf(windageTolerance, abs(cp.windage_cm) * 0.02))
                }
            }

            val sustained = solver.computeTrajectory(Distance(maxDist), Distance(1.0), zeroAngle)
            verify(fixture.checkpoints, sustained, "")

            if (fixture.gusts_checkpoints.isNotEmpty()) {
                val gusts = solver.computeTrajectoryGusts(Distance(maxDist), Distance(1.0), zeroAngle)
                verify(fixture.gusts_checkpoints, gusts, "gusts_")
            }
        }

        csv.close()
        println("Total checkpoints: $totalCheckpoints")
        println("Parity report: ${reportDir.resolve("parity.csv").absolutePath}")
        failures.filter { it.value > 0 }.forEach { (k, v) -> println("  $k failures: $v") }

        if (failureDetails.isNotEmpty()) {
            println("\nFirst failures:")
            failureDetails.forEach { println("  $it") }
        }

        val totalFailures = failures.values.sum()
        assertTrue(
            totalFailures == 0,
            "$totalFailures of ${totalCheckpoints * 4} checks failed ($failures)"
        )
    }
}
