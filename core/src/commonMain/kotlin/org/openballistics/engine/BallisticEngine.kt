package org.openballistics.engine

import org.openballistics.model.BallisticInput
import org.openballistics.model.BallisticSolution
import org.openballistics.model.RangeEntry
import org.openballistics.units.AngularValue
import org.openballistics.units.Distance
import org.openballistics.units.Energy
import kotlin.math.atan2
import kotlin.math.tan

object BallisticEngine {

    fun solve(input: BallisticInput, rangeStep: Distance = Distance(25.0)): BallisticSolution {
        val solver = TrajectorySolver(input)

        val zeroAngle = solver.findZeroAngle(input.zeroDistance)

        val targetRange = input.targetDistance
        val maxRange = if (targetRange.meters > 0) targetRange else Distance(1000.0)

        val sustained = solver.computeTrajectory(maxRange, rangeStep, zeroAngle)
        val gusts = solver.computeTrajectoryGusts(maxRange, rangeStep, zeroAngle)

        val slopeRad = input.slope.radians
        val slopeTan = tan(slopeRad)
        val targetPoint = sustained.lastOrNull()
        val gustsPoint = gusts.lastOrNull()

        val distM = targetPoint?.distance?.meters ?: 1.0
        val dropM = (targetPoint?.drop?.meters ?: 0.0) - distM * slopeTan
        val windageM = targetPoint?.windage?.meters ?: 0.0

        val gustsDistM = gustsPoint?.distance?.meters ?: 1.0
        val gustsDropM = (gustsPoint?.drop?.meters ?: 0.0) - gustsDistM * slopeTan
        val gustsWindageM = gustsPoint?.windage?.meters ?: 0.0

        val crosswindMps = WindDecomposition.crosswindAt(input.windZones, distM, false)
        val sg = StabilityFactor.compute(
            input.twistRate, input.bulletDiameter, input.bulletLength,
            input.bulletWeight, input.muzzleVelocity, input.atmosphere
        )
        val ajRad = Corrections.aerodynamicJump(
            crosswindMps, sg, input.muzzleVelocity.metersPerSecond, input.twistRate.direction
        )

        val elevation = dropToAngular(dropM, distM) + AngularValue.fromRadians(ajRad)
        val windage = dropToAngular(windageM, distM)
        val elevationGusts = dropToAngular(gustsDropM, distM) + AngularValue.fromRadians(ajRad)
        val windageGusts = dropToAngular(gustsWindageM, distM)

        val rangeTable = sustained.map { pt ->
            val d = pt.distance.meters
            val sightLineDrop = pt.drop.meters - d * slopeTan
            RangeEntry(
                distance = pt.distance,
                elevation = if (d > 0) dropToAngular(sightLineDrop, d) else AngularValue.ZERO,
                windage = if (d > 0) dropToAngular(pt.windage.meters, d) else AngularValue.ZERO,
                drop = Distance(sightLineDrop),
                drift = pt.windage,
                velocity = pt.velocity,
                energy = pt.energy,
                timeOfFlight = pt.timeOfFlight
            )
        }

        return BallisticSolution(
            elevation = -elevation,
            windage = -windage,
            elevationGusts = -elevationGusts,
            windageGusts = -windageGusts,
            drop = targetPoint?.drop ?: Distance.ZERO,
            drift = targetPoint?.windage ?: Distance.ZERO,
            velocity = targetPoint?.velocity ?: input.muzzleVelocity,
            energy = targetPoint?.energy ?: org.openballistics.units.Energy.ZERO,
            timeOfFlight = targetPoint?.timeOfFlight ?: kotlin.time.Duration.ZERO,
            rangeTable = rangeTable
        )
    }

    private fun dropToAngular(dropMeters: Double, distanceMeters: Double): AngularValue {
        if (distanceMeters == 0.0) return AngularValue.ZERO
        val radians = atan2(dropMeters, distanceMeters)
        return AngularValue.fromRadians(radians)
    }


}
