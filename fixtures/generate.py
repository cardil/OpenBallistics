#!/usr/bin/env python3
import json
import os
import random
import sys
from pathlib import Path

from py_ballisticcalc import (
    Ammo,
    Angular,
    Atmo,
    Calculator,
    Distance,
    DragModel,
    Pressure,
    Shot,
    TableG1,
    TableG7,
    Temperature,
    Velocity,
    Weapon,
    Weight,
    Wind,
)
from py_ballisticcalc.shot import ShotProps

OUTPUT_PATH = (
    Path(__file__).parent.parent
    / "core/src/commonTest/resources/fixtures.json"
)

CALIBERS = [
    {
        "name": ".223 Rem",
        "bc_g1": 0.393,
        "bc_g7": 0.209,
        "weight_grains": 77.0,
        "diameter_mm": 5.56,
        "length_mm": 22.1,
        "v0_options": [770.0, 810.0],
        "max_range_m": 800,
        "twist_options": [8.0, 7.0],
    },
    {
        "name": ".308 Win",
        "bc_g1": 0.505,
        "bc_g7": 0.260,
        "weight_grains": 175.0,
        "diameter_mm": 7.82,
        "length_mm": 31.5,
        "v0_options": [760.0, 800.0],
        "max_range_m": 1200,
        "twist_options": [10.0, 11.25],
    },
    {
        "name": "6.5 Creedmoor",
        "bc_g1": 0.610,
        "bc_g7": 0.314,
        "weight_grains": 140.0,
        "diameter_mm": 6.71,
        "length_mm": 35.2,
        "v0_options": [830.0, 870.0],
        "max_range_m": 1200,
        "twist_options": [8.0, 7.5],
    },
    {
        "name": ".338 Lapua Mag",
        "bc_g1": 0.768,
        "bc_g7": 0.381,
        "weight_grains": 300.0,
        "diameter_mm": 8.59,
        "length_mm": 43.2,
        "v0_options": [800.0, 840.0],
        "max_range_m": 2500,
        "twist_options": [9.35, 10.0],
    },
]

WIND_CLOCKS = [2, 4, 7, 10]
WIND_SPEEDS = [0.5, 3.0]
TEMPERATURES = [-5.0, 30.0]
PRESSURES = [950.0, 1030.0]
HUMIDITIES = [25.0, 78.0]
ALTITUDES = [200.0, 1400.0]
SLOPES = [-8.0, 12.0]
CANTS = [2.0, 7.0]
LATITUDES = [15.0, 52.0]
AZIMUTHS = [90.0, 270.0]
DRAG_MODELS = ["G7", "G1"]


def clock_to_degrees(clock: int) -> float:
    # Spec §3.3: 12=headwind, 6=tailwind.  py-ballisticcalc: 0°=tailwind, 180°=headwind.
    # Formula: py_degrees = ((clock - 6) * 30) % 360
    # Verification: 12h→180° ✓  3h→270° ✓  6h→0° ✓  9h→90° ✓
    return float(((clock - 6) * 30) % 360)


def _build_drag_model(caliber: dict, drag_model_str: str) -> DragModel:
    bc = caliber["bc_g7"] if drag_model_str == "G7" else caliber["bc_g1"]
    drag_table = TableG7 if drag_model_str == "G7" else TableG1
    return DragModel(
        bc=bc,
        drag_table=drag_table,
        weight=Weight.Grain(caliber["weight_grains"]),
        diameter=Distance.Millimeter(caliber["diameter_mm"]),
        length=Distance.Millimeter(caliber["length_mm"]),
    )


def run_trajectory(caliber: dict, params: dict, rng: random.Random) -> tuple[list[dict], float, float]:
    dm = _build_drag_model(caliber, params["drag_model"])
    ammo = Ammo(dm=dm, mv=Velocity.MPS(params["v0"]))
    weapon = Weapon(
        sight_height=Distance.Millimeter(90.0),
        twist=Distance.Inch(params["twist"]),
    )

    zero_shot = Shot(weapon=weapon, ammo=ammo, atmo=Atmo.icao())
    calc = Calculator()
    calc.set_weapon_zero(zero_shot, Distance.Meter(100))

    zero_angle_rad = float(weapon.zero_elevation >> Angular.Radian)
    props = ShotProps.from_shot(zero_shot)
    stability_coefficient = float(props.stability_coefficient)

    current_atmo = Atmo(
        altitude=Distance.Meter(params["altitude"]),
        pressure=Pressure.hPa(params["pressure"]),
        temperature=Temperature.Celsius(params["temperature"]),
        humidity=params["humidity"],
    )
    wind = Wind(
        velocity=Velocity.MPS(params["wind_speed"]),
        direction_from=Angular.Degree(clock_to_degrees(params["wind_clock"])),
    )
    shot = Shot(
        weapon=weapon,
        ammo=ammo,
        atmo=current_atmo,
        winds=[wind],
        look_angle=Angular.Degree(params["slope"]),
        cant_angle=Angular.Degree(params["cant"]),
        latitude=params["latitude"],
        azimuth=params["azimuth"],
    )

    step_m = int(os.environ.get("FIXTURE_STEP", "25"))
    num_checkpoints = int(os.environ.get("FIXTURE_CHECKPOINTS", "3"))

    result = calc.fire(
        shot,
        trajectory_range=Distance.Meter(caliber["max_range_m"]),
        trajectory_step=Distance.Meter(step_m),
    )

    traj_by_m: dict[int, object] = {
        round(p.distance >> Distance.Meter): p for p in result.trajectory
    }
    candidates = [d for d in traj_by_m if d >= 50]
    chosen = sorted(rng.sample(candidates, min(num_checkpoints, len(candidates))))

    checkpoints = []
    for d in chosen:
        pt = traj_by_m[d]
        drop_cm = float(pt.height >> Distance.Centimeter)
        windage_cm = float(pt.windage >> Distance.Centimeter)
        velocity_mps = float(pt.velocity >> Velocity.MPS)
        time_s = float(pt.time)
        checkpoints.append({
            "distance_m": float(d),
            "drop_cm": drop_cm,
            "windage_cm": windage_cm,
            "velocity_mps": velocity_mps,
            "time_s": time_s,
        })
    return checkpoints, zero_angle_rad, stability_coefficient


def build_inputs(caliber: dict, params: dict) -> dict:
    drag_model = params["drag_model"]
    bc = caliber["bc_g7"] if drag_model == "G7" else caliber["bc_g1"]
    return {
        "bc": bc,
        "drag_model": drag_model,
        "bullet_weight_grains": caliber["weight_grains"],
        "bullet_diameter_mm": caliber["diameter_mm"],
        "bullet_length_mm": caliber["length_mm"],
        "muzzle_velocity_mps": params["v0"],
        "zero_distance_m": 100.0,
        "sight_height_mm": 90.0,
        "twist_inches": params["twist"],
        "twist_direction": "RH",
        "temperature_c": params["temperature"],
        "pressure_hpa": params["pressure"],
        "humidity_pct": params["humidity"],
        "altitude_m": params["altitude"],
        "wind_clock": params["wind_clock"],
        "wind_speed_mps": params["wind_speed"],
        "slope_deg": params["slope"],
        "cant_deg": params["cant"],
        "latitude_deg": params["latitude"],
        "azimuth_deg": params["azimuth"],
    }


TARGET_COUNT = int(os.environ.get("FIXTURE_COUNT", "100"))


def main() -> None:
    seed_env = os.environ.get("FIXTURE_SEED")
    seed = int(seed_env) if seed_env is not None else random.randint(0, 2**31 - 1)
    print(f"FIXTURE_SEED={seed}")

    rng = random.Random(seed)
    fixtures: list[dict] = []

    for fixture_id in range(TARGET_COUNT):
        caliber = rng.choice(CALIBERS)
        params = {
            "wind_clock": rng.choice(WIND_CLOCKS),
            "wind_speed": rng.choice(WIND_SPEEDS),
            "temperature": rng.choice(TEMPERATURES),
            "pressure": rng.choice(PRESSURES),
            "humidity": rng.choice(HUMIDITIES),
            "altitude": rng.choice(ALTITUDES),
            "slope": rng.choice(SLOPES),
            "cant": rng.choice(CANTS),
            "latitude": rng.choice(LATITUDES),
            "azimuth": rng.choice(AZIMUTHS),
            "twist": rng.choice(caliber["twist_options"]),
            "v0": rng.choice(caliber["v0_options"]),
            "drag_model": rng.choice(DRAG_MODELS),
        }

        checkpoints, zero_angle_rad, stability_coefficient = run_trajectory(caliber, params, rng)
        fixtures.append(
            {
                "id": fixture_id,
                "caliber": caliber["name"],
                "inputs": build_inputs(caliber, params),
                "zero_angle_rad": zero_angle_rad,
                "stability_coefficient": stability_coefficient,
                "checkpoints": checkpoints,
            }
        )

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8") as fh:
        json.dump({"seed": seed, "fixtures": fixtures}, fh, indent=2)
        fh.write("\n")

    print(f"Generated {len(fixtures)} fixtures → {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
