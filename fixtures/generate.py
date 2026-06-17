#!/usr/bin/env python3
import json
import os
import random
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
        "v0_min": 750.0, "v0_max": 830.0,
        "max_range_m": 800,
        "twist_min": 7, "twist_max": 9,
    },
    {
        "name": ".308 Win",
        "bc_g1": 0.505,
        "bc_g7": 0.260,
        "weight_grains": 175.0,
        "diameter_mm": 7.82,
        "length_mm": 31.5,
        "v0_min": 740.0, "v0_max": 820.0,
        "max_range_m": 1200,
        "twist_min": 10, "twist_max": 12,
    },
    {
        "name": "6.5 Creedmoor",
        "bc_g1": 0.610,
        "bc_g7": 0.314,
        "weight_grains": 140.0,
        "diameter_mm": 6.71,
        "length_mm": 35.2,
        "v0_min": 810.0, "v0_max": 890.0,
        "max_range_m": 1200,
        "twist_min": 7, "twist_max": 9,
    },
    {
        "name": ".338 Lapua Mag",
        "bc_g1": 0.768,
        "bc_g7": 0.381,
        "weight_grains": 300.0,
        "diameter_mm": 8.59,
        "length_mm": 43.2,
        "v0_min": 780.0, "v0_max": 860.0,
        "max_range_m": 2500,
        "twist_min": 9, "twist_max": 11,
    },
]


def clock_to_degrees(clock: int) -> float:
    return float(((clock - 6) * 30) % 360)


def random_params(rng: random.Random, caliber: dict) -> dict:
    sustained = round(rng.uniform(0.2, 4.0), 1)
    gusts = round(sustained * rng.uniform(1.0, 2.0), 1)
    return {
        "wind_clock": rng.randint(1, 12),
        "wind_speed": sustained,
        "wind_gusts": gusts,
        "temperature": round(rng.uniform(-15, 40), 1),
        "pressure": round(rng.uniform(920, 1060), 1),
        "humidity": round(rng.uniform(5, 95), 1),
        "altitude": round(rng.uniform(0, 2500)),
        "slope": round(rng.uniform(-20, 20), 1),
        "cant": round(rng.uniform(0.5, 10), 1),
        "latitude": round(rng.uniform(-60, 70), 1),
        "azimuth": round(rng.uniform(0, 359), 1),
        "twist": float(rng.randint(caliber["twist_min"], caliber["twist_max"])),
        "v0": round(rng.uniform(caliber["v0_min"], caliber["v0_max"]), 1),
        "drag_model": rng.choice(["G7", "G1"]),
    }


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


def _extract_checkpoints(
    result, rng: random.Random, num_checkpoints: int, step_m: int, min_distance: int = 200
) -> list[dict]:
    traj_by_m: dict[int, object] = {
        round(p.distance >> Distance.Meter): p for p in result.trajectory
    }
    candidates = [d for d in traj_by_m if d >= min_distance and d % step_m == 0]
    if not candidates:
        return []
    chosen = sorted(rng.sample(candidates, min(num_checkpoints, len(candidates))))
    checkpoints = []
    for d in chosen:
        pt = traj_by_m[d]
        checkpoints.append({
            "distance_m": float(d),
            "drop_cm": round(float(pt.height >> Distance.Centimeter), 6),
            "windage_cm": round(float(pt.windage >> Distance.Centimeter), 6),
            "velocity_mps": round(float(pt.velocity >> Velocity.MPS), 6),
            "time_s": round(float(pt.time), 6),
        })
    return checkpoints


def run_fixture(caliber: dict, params: dict, rng: random.Random) -> dict:
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

    current_atmo = Atmo(
        altitude=Distance.Meter(params["altitude"]),
        pressure=Pressure.hPa(params["pressure"]),
        temperature=Temperature.Celsius(params["temperature"]),
        humidity=params["humidity"],
    )
    wind_dir = Angular.Degree(clock_to_degrees(params["wind_clock"]))

    step_m = 50
    num_checkpoints = int(os.environ.get("FIXTURE_CHECKPOINTS", "3"))
    num_gusts_checkpoints = max(1, num_checkpoints // 2)
    max_range = Distance.Meter(caliber["max_range_m"])

    sustained_wind = Wind(velocity=Velocity.MPS(params["wind_speed"]), direction_from=wind_dir)
    sustained_shot = Shot(
        weapon=weapon, ammo=ammo, atmo=current_atmo,
        winds=[sustained_wind],
        look_angle=Angular.Degree(params["slope"]),
        cant_angle=Angular.Degree(params["cant"]),
        latitude=params["latitude"], azimuth=params["azimuth"],
    )
    props = ShotProps.from_shot(sustained_shot)
    stability_coefficient = float(props.stability_coefficient)

    sustained_result = calc.fire(
        sustained_shot, trajectory_range=max_range,
        trajectory_step=Distance.Meter(step_m), raise_range_error=False,
    )
    checkpoints = _extract_checkpoints(sustained_result, rng, num_checkpoints, step_m)

    gusts_wind = Wind(velocity=Velocity.MPS(params["wind_gusts"]), direction_from=wind_dir)
    gusts_shot = Shot(
        weapon=weapon, ammo=ammo, atmo=current_atmo,
        winds=[gusts_wind],
        look_angle=Angular.Degree(params["slope"]),
        cant_angle=Angular.Degree(params["cant"]),
        latitude=params["latitude"], azimuth=params["azimuth"],
    )
    gusts_result = calc.fire(
        gusts_shot, trajectory_range=max_range,
        trajectory_step=Distance.Meter(step_m), raise_range_error=False,
    )
    gusts_checkpoints = _extract_checkpoints(gusts_result, rng, num_gusts_checkpoints, step_m)

    return {
        "checkpoints": checkpoints,
        "gusts_checkpoints": gusts_checkpoints,
        "zero_angle_rad": round(zero_angle_rad, 6),
        "stability_coefficient": round(stability_coefficient, 6),
    }


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
        "wind_gusts_mps": params["wind_gusts"],
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
        params = random_params(rng, caliber)
        result = run_fixture(caliber, params, rng)
        fixtures.append({
            "id": fixture_id,
            "caliber": caliber["name"],
            "inputs": build_inputs(caliber, params),
            "zero_angle_rad": result["zero_angle_rad"],
            "stability_coefficient": result["stability_coefficient"],
            "checkpoints": result["checkpoints"],
            "gusts_checkpoints": result["gusts_checkpoints"],
        })

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8") as fh:
        json.dump({"seed": seed, "fixtures": fixtures}, fh, indent=2)
        fh.write("\n")

    total_sustained = sum(len(f["checkpoints"]) for f in fixtures)
    total_gusts = sum(len(f["gusts_checkpoints"]) for f in fixtures)
    print(f"Generated {len(fixtures)} fixtures ({total_sustained} sustained + {total_gusts} gusts checkpoints) → {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
