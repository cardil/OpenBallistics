package org.openballistics.engine

import org.openballistics.model.BallisticInput
import org.openballistics.model.WindZone
import org.openballistics.units.Distance
import org.openballistics.units.Energy
import org.openballistics.units.Speed
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

data class TrajectoryPoint(
    val distance: Distance,
    val drop: Distance,
    val windage: Distance,
    val velocity: Speed,
    val energy: Energy,
    val timeOfFlight: kotlin.time.Duration,
    val mach: Double
)

internal data class State(
    val x: Double,
    val y: Double,
    val z: Double,
    val vx: Double,
    val vy: Double,
    val vz: Double,
    val t: Double
)

internal data class Derivatives(
    val dx: Double,
    val dy: Double,
    val dz: Double,
    val dvx: Double,
    val dvy: Double,
    val dvz: Double
)

class TrajectorySolver(private val input: BallisticInput) {

    private val baseDensityRatio = Atmosphere.airDensity(input.atmosphere) / RHO_STANDARD
    private val zeroDensityRatio = Atmosphere.airDensity(input.zeroAtmosphere) / RHO_STANDARD
    private val baseSpeedOfSound = Atmosphere.speedOfSound(input.atmosphere.temperature)
    private val zeroSpeedOfSound = Atmosphere.speedOfSound(input.zeroAtmosphere.temperature)
    private val baseAltitude = input.atmosphere.altitude.meters
    private val slopeRad = input.slope.radians
    private val cantRad = input.cant.radians
    private val latRad = input.latitude.radians
    private val azRad = input.azimuth.radians
    private val v0 = input.muzzleVelocity.metersPerSecond
    private val sightHeight = input.sightHeight.meters

    private val sg: Double by lazy {
        StabilityFactor.compute(
            input.twistRate, input.bulletDiameter, input.bulletLength,
            input.bulletWeight, input.muzzleVelocity, input.atmosphere
        )
    }

    private val altThresholdFt = 30.0
    private val baseAltitudeFt = baseAltitude * M_TO_FT
    private val baseTemperatureC = input.atmosphere.temperature.celsius
    private val basePressureHpa = input.atmosphere.pressure.hectopascals

    private fun densityAndMachAtAltitude(y: Double): Pair<Double, Double> {
        val yFt = y * M_TO_FT
        if (kotlin.math.abs(yFt) < altThresholdFt) {
            return Pair(baseDensityRatio, baseSpeedOfSound)
        }

        val altFt = baseAltitudeFt + yFt
        val tC = temperatureAtAltitude(altFt)
        val tK = tC + 273.15
        val pHpa = pressureAtAltitude(altFt)
        val densityDelta = ((baseTemperatureC + 273.15) * pHpa) / (basePressureHpa * tK)
        val densityRatio = baseDensityRatio * densityDelta
        val speedOfSound = kotlin.math.sqrt(GAMMA_AIR * R_DRY * tK)
        return Pair(densityRatio, speedOfSound)
    }

    private fun temperatureAtAltitude(altFt: Double): Double {
        val t = (altFt - baseAltitudeFt) * LAPSE_RATE_K_PER_FT + baseTemperatureC
        return maxOf(t, LOWEST_TEMP_C)
    }

    private fun pressureAtAltitude(altFt: Double): Double {
        return basePressureHpa * ((1.0 + LAPSE_RATE_K_PER_FT * (altFt - baseAltitudeFt) /
                (baseTemperatureC + 273.15)).pow(PRESSURE_EXPONENT))
    }

    fun computeTrajectory(
        maxRange: Distance,
        step: Distance,
        zeroAngleRad: Double
    ): List<TrajectoryPoint> {
        return integrate(maxRange, step, zeroAngleRad, useGusts = false)
    }

    fun computeTrajectoryGusts(
        maxRange: Distance,
        step: Distance,
        zeroAngleRad: Double
    ): List<TrajectoryPoint> {
        return integrate(maxRange, step, zeroAngleRad, useGusts = true)
    }

     private fun integrate(
         maxRange: Distance,
         step: Distance,
         zeroAngleRad: Double,
         useGusts: Boolean
     ): List<TrajectoryPoint> {
         val stepX = step.meters
         require(stepX > 0.0) { "Step distance must be positive" }
         val points = mutableListOf<TrajectoryPoint>()
         val maxX = maxRange.meters
        var nextRecordX = 0.0

        val barrelElevation = slopeRad + cos(cantRad) * zeroAngleRad
        val barrelAzimuth = sin(cantRad) * zeroAngleRad
        var state = State(
            x = 0.0,
            y = -cos(cantRad) * sightHeight,
            z = -sin(cantRad) * sightHeight,
            vx = v0 * cos(barrelElevation) * cos(barrelAzimuth),
            vy = v0 * sin(barrelElevation),
            vz = v0 * cos(barrelElevation) * sin(barrelAzimuth),
            t = 0.0
        )

        val dt = 0.0025
        var prevState = state
        points.add(stateToPoint(state))
        nextRecordX += stepX

        while (state.vx > 0.0) {
            prevState = state
            state = rk4Step(state, dt, input.windZones, useGusts)

            while (state.x >= nextRecordX && nextRecordX <= maxX) {
                val interp = interpolateAtDistance(prevState, state, nextRecordX)
                points.add(stateToPoint(interp))
                nextRecordX += stepX
            }

            if (state.x > maxX) break
        }

        return points
    }

    private fun interpolateAtDistance(s0: State, s1: State, targetX: Double): State {
        if (s1.x == s0.x) return s1
        val frac = (targetX - s0.x) / (s1.x - s0.x)
        return State(
            x = targetX,
            y = s0.y + frac * (s1.y - s0.y),
            z = s0.z + frac * (s1.z - s0.z),
            vx = s0.vx + frac * (s1.vx - s0.vx),
            vy = s0.vy + frac * (s1.vy - s0.vy),
            vz = s0.vz + frac * (s1.vz - s0.vz),
            t = s0.t + frac * (s1.t - s0.t)
        )
    }

    fun findZeroAngle(zeroDistance: Distance): Double {
        val targetX = zeroDistance.meters
        var lo = 0.0
        var hi = 0.05

        repeat(100) {
            val mid = (lo + hi) / 2.0
            val drop = simulateDropAt(targetX, mid)
            if (drop > 0.0) hi = mid else lo = mid
        }

        return (lo + hi) / 2.0
    }

    private fun simulateDropAt(targetX: Double, zeroAngleRad: Double): Double {
        var state = State(
            x = 0.0, y = -sightHeight, z = 0.0,
            vx = v0 * cos(zeroAngleRad),
            vy = v0 * sin(zeroAngleRad),
            vz = 0.0, t = 0.0
        )

        val dt = 0.0025
        while (state.x < targetX && state.vx > 0.0) {
            val remaining = targetX - state.x
            val currentDt = if (remaining < state.vx * dt) remaining / state.vx else dt
            state = rk4StepZero(state, currentDt)
        }

        return state.y
    }

    private fun rk4StepZero(state: State, dt: Double): State {
        val vMag = sqrt(state.vx * state.vx + state.vy * state.vy + state.vz * state.vz)
        if (vMag < 1.0) return state.copy(t = state.t + dt)

        val mach = vMag / zeroSpeedOfSound
        val bc = DragTables.effectiveBc(input.bulletBC, vMag)
        val km = zeroDensityRatio * DragTables.standardCd(input.dragModel, mach) / (bc * DragTables.DRAG_CONSTANT)

        fun derivs(s: State): Derivatives {
            val v = sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz)
            val dragScale = km * v
            return Derivatives(
                dx = s.vx, dy = s.vy, dz = s.vz,
                dvx = -dragScale * s.vx,
                dvy = -dragScale * s.vy - G,
                dvz = -dragScale * s.vz
            )
        }

        val k1 = derivs(state)
        val k2 = derivs(advanceState(state, k1, dt / 2.0))
        val k3 = derivs(advanceState(state, k2, dt / 2.0))
        val k4 = derivs(advanceState(state, k3, dt))

        return State(
            x = state.x + dt / 6.0 * (k1.dx + 2 * k2.dx + 2 * k3.dx + k4.dx),
            y = state.y + dt / 6.0 * (k1.dy + 2 * k2.dy + 2 * k3.dy + k4.dy),
            z = state.z + dt / 6.0 * (k1.dz + 2 * k2.dz + 2 * k3.dz + k4.dz),
            vx = state.vx + dt / 6.0 * (k1.dvx + 2 * k2.dvx + 2 * k3.dvx + k4.dvx),
            vy = state.vy + dt / 6.0 * (k1.dvy + 2 * k2.dvy + 2 * k3.dvy + k4.dvy),
            vz = state.vz + dt / 6.0 * (k1.dvz + 2 * k2.dvz + 2 * k3.dvz + k4.dvz),
            t = state.t + dt
        )
    }

    private fun rk4Step(
        state: State,
        dt: Double,
        windZones: List<WindZone>,
        useGusts: Boolean
    ): State {
        val crosswind = WindDecomposition.crosswindAt(windZones, state.x, useGusts)
        val headwind = WindDecomposition.headwindAt(windZones, state.x, useGusts)

        val vxAir = state.vx + headwind
        val vyAir = state.vy
        val vzAir = state.vz + crosswind
        val vRelMag = sqrt(vxAir * vxAir + vyAir * vyAir + vzAir * vzAir)

        if (vRelMag < 1.0) return state.copy(t = state.t + dt)

        val (densityRatio, speedOfSound) = densityAndMachAtAltitude(state.y)
        val mach = vRelMag / speedOfSound
        val bc = DragTables.effectiveBc(input.bulletBC, vRelMag)
        val km = densityRatio * DragTables.standardCd(input.dragModel, mach) / (bc * DragTables.DRAG_CONSTANT)

        val k1 = derivativesFixed(state, crosswind, headwind, km)
        val s2 = advanceState(state, k1, dt / 2.0)
        val k2 = derivativesFixed(s2, crosswind, headwind, km)
        val s3 = advanceState(state, k2, dt / 2.0)
        val k3 = derivativesFixed(s3, crosswind, headwind, km)
        val s4 = advanceState(state, k3, dt)
        val k4 = derivativesFixed(s4, crosswind, headwind, km)

        return State(
            x = state.x + dt / 6.0 * (k1.dx + 2 * k2.dx + 2 * k3.dx + k4.dx),
            y = state.y + dt / 6.0 * (k1.dy + 2 * k2.dy + 2 * k3.dy + k4.dy),
            z = state.z + dt / 6.0 * (k1.dz + 2 * k2.dz + 2 * k3.dz + k4.dz),
            vx = state.vx + dt / 6.0 * (k1.dvx + 2 * k2.dvx + 2 * k3.dvx + k4.dvx),
            vy = state.vy + dt / 6.0 * (k1.dvy + 2 * k2.dvy + 2 * k3.dvy + k4.dvy),
            vz = state.vz + dt / 6.0 * (k1.dvz + 2 * k2.dvz + 2 * k3.dvz + k4.dvz),
            t = state.t + dt
        )
    }

    private fun advanceState(state: State, d: Derivatives, dt: Double): State {
        return State(
            x = state.x + d.dx * dt,
            y = state.y + d.dy * dt,
            z = state.z + d.dz * dt,
            vx = state.vx + d.dvx * dt,
            vy = state.vy + d.dvy * dt,
            vz = state.vz + d.dvz * dt,
            t = state.t + dt
        )
    }

    private fun derivativesFixed(
        state: State,
        crosswind: Double,
        headwind: Double,
        km: Double
    ): Derivatives {
        val vxAir = state.vx + headwind
        val vyAir = state.vy
        val vzAir = state.vz + crosswind

        val vMag = sqrt(vxAir * vxAir + vyAir * vyAir + vzAir * vzAir)

        val dragScale = km * vMag
        val ax = -dragScale * vxAir
        val ay = -dragScale * vyAir - G
        val az = -dragScale * vzAir

        val (aCoriolisX, aCoriolisY, aCoriolisZ) = Corrections.coriolisAcceleration(
            state.vx, state.vy, state.vz, latRad, azRad
        )

        return Derivatives(
            dx = state.vx, dy = state.vy, dz = state.vz,
            dvx = ax + aCoriolisX, dvy = ay + aCoriolisY, dvz = az + aCoriolisZ
        )
    }

    private fun stateToPoint(state: State): TrajectoryPoint {
        val vMag = sqrt(state.vx * state.vx + state.vy * state.vy + state.vz * state.vz)
        val (_, speedOfSound) = densityAndMachAtAltitude(state.y)
        val mach = vMag / speedOfSound

        val spinDriftM = Corrections.spinDrift(sg, state.t, input.twistRate.direction)

        val totalWindage = state.z + spinDriftM
        val totalDrop = state.y

        val bulletMass = input.bulletWeight.kilograms
        val energyJoules = 0.5 * bulletMass * vMag * vMag

        return TrajectoryPoint(
            distance = Distance(state.x),
            drop = Distance(totalDrop),
            windage = Distance(totalWindage),
            velocity = Speed(vMag),
            energy = Energy(energyJoules),
            timeOfFlight = state.t.seconds,
            mach = mach
        )
    }

    companion object {
        private const val G = 9.80665
        private const val RHO_STANDARD = 1.2250
        private const val M_TO_FT = 3.2808398950131
        private const val LAPSE_RATE_K_PER_FT = -0.0019812
        private const val PRESSURE_EXPONENT = 5.255876
        private const val LOWEST_TEMP_C = -57.22  // ~-71°F
        private const val GAMMA_AIR = 1.4
        private const val R_DRY = 287.058
    }
}
