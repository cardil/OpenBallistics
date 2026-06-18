package org.openballistics.engine

import kotlinx.serialization.Serializable

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
