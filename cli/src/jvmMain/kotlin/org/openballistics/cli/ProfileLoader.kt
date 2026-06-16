package org.openballistics.cli

import org.openballistics.engine.Atmosphere
import org.openballistics.model.*
import org.openballistics.units.*
import java.io.File

object ProfileLoader {

    val storeDir: File by lazy {
        val configDir = System.getenv("XDG_CONFIG_HOME")
            ?: "${System.getProperty("user.home")}/.config"
        File(configDir, "open-ballistics")
    }

    fun initStore() {
        val profilesDir = File(storeDir, "profiles")
        val ammoDir = File(storeDir, "ammunition")
        profilesDir.mkdirs()
        ammoDir.mkdirs()

        val configFile = File(storeDir, "config.toml")
        if (!configFile.exists()) {
            configFile.writeText(DEFAULT_CONFIG)
        }

        val sampleProfile = File(profilesDir, "sako-trg-308.toml")
        if (!sampleProfile.exists()) {
            sampleProfile.writeText(SAMPLE_PROFILE)
        }

        val sampleAmmo = File(ammoDir, "ggg-308-175.toml")
        if (!sampleAmmo.exists()) {
            sampleAmmo.writeText(SAMPLE_AMMO)
        }
    }

    fun listProfiles(): List<String> {
        val profilesDir = File(storeDir, "profiles")
        if (!profilesDir.exists()) return emptyList()
        return profilesDir.listFiles()
            ?.filter { it.extension == "toml" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    fun loadAndBuild(
        profileName: String,
        ammoName: String,
        distance: Double,
        windSpec: String?,
        tempC: Double?,
        pressureHpa: Double?
    ): BallisticInput {
        val profile = loadProfile(profileName)
        val ammo = loadAmmo(ammoName)
        val windZones = parseWind(windSpec, distance)
        val atmo = AtmosphericData(
            temperature = Temperature(tempC ?: 15.0),
            pressure = Pressure(pressureHpa ?: 1013.25),
            humidity = Percentage.fromPercent(50.0),
            altitude = Distance.ZERO
        )

        val dragModel = when (ammo["drag_model"]) {
            "G1" -> DragModel.G1
            else -> DragModel.G7
        }
        val twistDir = when (profile["twist_direction"]) {
            "LH" -> TwistDirection.LEFT_HAND
            else -> TwistDirection.RIGHT_HAND
        }
        val twistUnit = when (profile["twist_unit"]) {
            "mm" -> TwistRateUnit.MILLIMETERS
            else -> TwistRateUnit.INCHES
        }

        val bc = ammo.double("bc", 0.260)
        val v0 = ammo.double("velocity_mps", 780.0)

        val zeroAtmo = AtmosphericData(
            temperature = Temperature(profile.double("zero_atmosphere.temperature_c", 15.0)),
            pressure = Pressure(profile.double("zero_atmosphere.pressure_hpa", 1013.25)),
            humidity = Percentage.fromPercent(profile.double("zero_atmosphere.humidity_pct", 0.0)),
            altitude = Distance(profile.double("zero_atmosphere.altitude_m", 0.0))
        )

        return BallisticInput(
            zeroDistance = Distance(profile.double("zero_distance_m", 100.0)),
            zeroAtmosphere = zeroAtmo,
            sightHeight = Distance.fromMillimeters(profile.double("sight_height_mm", 90.0)),
            twistRate = TwistRate(profile.double("twist_rate", 11.0), twistUnit, twistDir),
            barrelLength = Distance.fromMillimeters(profile.double("barrel_length_mm", 660.0)),
            dragModel = dragModel,
            bulletBC = BallisticCoefficient.Single(bc, dragModel),
            bulletWeight = Mass.fromGrains(ammo.double("bullet_weight_grains", 175.0)),
            bulletDiameter = Distance.fromMillimeters(ammo.double("bullet_diameter_mm", 7.82)),
            bulletLength = Distance.fromMillimeters(ammo.double("bullet_length_mm", 31.5)),
            muzzleVelocity = Speed(v0),
            targetDistance = Distance(distance),
            slope = Angle.fromDegrees(0.0),
            cant = Angle.fromDegrees(0.0),
            windZones = windZones,
            atmosphere = atmo,
            latitude = Angle.fromDegrees(52.0),
            azimuth = Angle.fromDegrees(0.0)
        )
    }

    private fun loadProfile(name: String): TomlData {
        val file = File(storeDir, "profiles/$name.toml")
        if (!file.exists()) {
            System.err.println("Profile not found: $file — using defaults")
            return TomlData(emptyMap())
        }
        return parseToml(file.readText())
    }

    private fun loadAmmo(name: String): TomlData {
        val file = File(storeDir, "ammunition/$name.toml")
        if (!file.exists()) {
            System.err.println("Ammunition not found: $file — using defaults")
            return TomlData(emptyMap())
        }
        return parseToml(file.readText())
    }

    private class TomlData(private val values: Map<String, String>) {
        operator fun get(key: String): String? = values[key]
        fun double(key: String, default: Double): Double =
            values[key]?.toDoubleOrNull() ?: default
    }

    private fun parseToml(content: String): TomlData {
        val values = mutableMapOf<String, String>()
        var section = ""
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            if (trimmed.startsWith("[") && !trimmed.startsWith("[[")) {
                section = trimmed.removePrefix("[").removeSuffix("]").trim()
                continue
            }
            if (trimmed.startsWith("[[")) {
                section = trimmed.removePrefix("[[").removeSuffix("]]").trim()
                continue
            }
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx < 0) continue
            val key = trimmed.substring(0, eqIdx).trim()
            val rawVal = trimmed.substring(eqIdx + 1).trim()
                .removeSurrounding("\"")
            val fullKey = if (section.isEmpty()) key else "$section.$key"
            values[fullKey] = rawVal
        }
        return TomlData(values)
    }

    private fun parseWind(windSpec: String?, maxDistance: Double): List<WindZone> {
        if (windSpec == null) {
            return listOf(
                WindZone(
                    rangeStart = Distance.ZERO,
                    rangeEnd = Distance(maxDistance),
                    direction = ClockDirection(12),
                    sustained = Speed(0.5),
                    gusts = Speed(0.5),
                    source = DataSource.MANUAL
                )
            )
        }
        val parts = windSpec.split(":")
        val clock = parts[0].toInt()
        val sustained = parts[1].toDouble()
        val gusts = if (parts.size > 2) parts[2].toDouble() else sustained
        return listOf(
            WindZone(
                rangeStart = Distance.ZERO,
                rangeEnd = Distance(maxDistance),
                direction = ClockDirection(clock),
                sustained = Speed(sustained),
                gusts = Speed(gusts),
                source = DataSource.MANUAL
            )
        )
    }

    private val DEFAULT_CONFIG = """
        |# OpenBallistics CLI configuration
        |
        |[units]
        |distance = "m"
        |wind_speed = "m/s"
        |muzzle_speed = "m/s"
        |correction = "mrad"
        |temperature = "C"
        |pressure = "hPa"
    """.trimMargin()

    private val SAMPLE_PROFILE = """
        |# Sako TRG 42 .308 Win
        |name = "Sako TRG 42 .308"
        |zero_distance_m = 100.0
        |sight_height_mm = 90.0
        |twist_rate = 11.0
        |twist_unit = "inches"
        |twist_direction = "RH"
        |barrel_length_mm = 660.0
        |
        |[zero_atmosphere]
        |temperature_c = 15.0
        |pressure_hpa = 1013.25
        |humidity_pct = 0.0
        |altitude_m = 0.0
        |
        |[scope]
        |model = "Delta Stryker HD 5-50x56"
        |click_value_mrad = 0.1
        |max_elevation_mrad = 40.0
        |max_windage_mrad = 20.0
    """.trimMargin()

    private val SAMPLE_AMMO = """
        |# GGG .308 Win 175gn HPBT
        |name = "GGG .308W 175gn HPBT"
        |source = "factory"
        |drag_model = "G7"
        |bc = 0.260
        |bullet_weight_grains = 175.0
        |bullet_diameter_mm = 7.82
        |bullet_length_mm = 31.5
        |
        |[[velocity_table]]
        |temperature_c = 20.0
        |velocity_mps = 780.0
        |is_estimate = true
    """.trimMargin()
}
