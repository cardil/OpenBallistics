package org.openballistics.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import org.openballistics.engine.BallisticEngine
import org.openballistics.units.Distance

class SolveCommand : CliktCommand(name = "solve") {
    private val profile by option("--profile", help = "Profile name").required()
    private val ammo by option("--ammo", help = "Ammunition name").required()
    private val distance by option("--distance", help = "Target distance (m)").double().required()
    private val wind by option("--wind", help = "Wind: clock:sustained:gusts (e.g. 5:3.2:4.8)")
    private val temp by option("--temp", help = "Temperature (°C)").double()
    private val pressure by option("--pressure", help = "Pressure (hPa)").double()

    override fun run() {
        val input = ProfileLoader.loadAndBuild(profile, ammo, distance, wind, temp, pressure)
        val solution = BallisticEngine.solve(input)
        echo("Elevation:  %.2f mrad".format(solution.elevation.milliradians))
        echo("Windage:    %.2f mrad".format(solution.windage.milliradians))
        echo("Drop:       %.1f cm".format(solution.drop.centimeters))
        echo("Drift:      %.1f cm".format(solution.drift.centimeters))
        echo("Velocity:   %.1f m/s".format(solution.velocity.metersPerSecond))
        echo("Energy:     %.0f J".format(solution.energy.joules))
        echo("TOF:        %.3f s".format(solution.timeOfFlight.inWholeMilliseconds / 1000.0))
    }
}

class RangeCardCommand : CliktCommand(name = "range-card") {
    private val profile by option("--profile", help = "Profile name").required()
    private val ammo by option("--ammo", help = "Ammunition name").required()
    private val max by option("--max", help = "Max distance (m)").double().default(1000.0)
    private val step by option("--step", help = "Step (m)").int().default(25)

    override fun run() {
        val input = ProfileLoader.loadAndBuild(profile, ammo, max, null, null, null)
        val solution = BallisticEngine.solve(input, Distance(step.toDouble()))
        echo("%-8s %-10s %-10s %-10s %-10s %-10s %-10s".format(
            "Dist", "Elev", "Wind", "Drop", "Drift", "Vel", "TOF"
        ))
        echo("-".repeat(70))
        for (entry in solution.rangeTable) {
            echo("%-8.0f %-10.2f %-10.2f %-10.1f %-10.1f %-10.1f %-10.3f".format(
                entry.distance.meters,
                entry.elevation.milliradians,
                entry.windage.milliradians,
                entry.drop.centimeters,
                entry.drift.centimeters,
                entry.velocity.metersPerSecond,
                entry.timeOfFlight.inWholeMilliseconds / 1000.0
            ))
        }
    }
}

class ProfilesCommand : CliktCommand(name = "profiles") {
    override fun run() {
        val profiles = ProfileLoader.listProfiles()
        if (profiles.isEmpty()) {
            echo("No profiles found. Run 'openballistics init' to create templates.")
        } else {
            profiles.forEach { echo(it) }
        }
    }
}

class InitCommand : CliktCommand(name = "init") {
    override fun run() {
        ProfileLoader.initStore()
        echo("Initialized profile store at ${ProfileLoader.storeDir}")
    }
}
