# OpenBallistics: Technical Specification

**Version**: 1.1 DRAFT
**Date**: 2026-06-15
**Project License**: Apache 2.0

---

## 1. Vision and Goals

### 1.1 Problem

Existing ballistic calculators (Applied Ballistics, Strelok, Shooter) are
closed, expensive, and incomplete. There's no solution that combines:
- an open, verifiable computation engine
- integration with wind sensors (Calypso, WeatherFlow)
- a Garmin watch terminal as an active shooter cockpit
- a multi-zone wind model

### 1.2 Solution

OpenBallistics is a system consisting of:
- **Server** — a mobile application (Android, iOS in the future) that manages
  profiles, performs calculations, and integrates with sensors
- **Terminal** — a smartwatch application that displays data and allows
  full parameter control from the wrist. Also acts as a **sensor**
  (barometer, thermometer, GPS, compass). MVP: Garmin ConnectIQ. Future:
  WearOS, Apple Watch, other platforms.

### 1.3 Target User

A long-range shooter (PRS, F-Class, mountain hunting). Uses the app in two modes:
1. **Preparation** — home/range: profile configuration, truing,
   recording velocity vs. temperature
2. **Competition/hunting** — field: quick correction readout from the watch or
   phone, changing wind/distance without breaking shooting position

---

## 2. Architecture

### 2.1 Tech Stack

| Component | Technology |
|-----------|------------|
| Corelib (shared) | Kotlin Multiplatform (commonMain) |
| Android app | Kotlin + Jetpack Compose |
| iOS app (future) | Swift + SwiftUI (consumes KMP framework) |
| Garmin terminal | ConnectIQ SDK, Monkey C |
| BLE Android | Nordic Kotlin BLE Library |
| BLE iOS | Native iOS APIs via expect/actual |
| Build system | Gradle (root) |
| Local database | Room (Android) / SQLDelight (KMP shared) |
| Maps | OpenStreetMap + Open Elevation (SRTM) |

### 2.2 Monorepo Structure

```
OpenBallistics/
├── core/                          # KMP corelib
│   ├── src/commonMain/kotlin/     #   Ballistic engine, data models
│   ├── src/commonTest/kotlin/     #   Tests (ported from py-ballisticcalc)
│   ├── src/androidMain/kotlin/    #   Android BLE, sensors
│   ├── src/iosMain/kotlin/        #   iOS BLE, sensors
│   └── build.gradle.kts
├── cli/                           # CLI tool (JVM, consumes core)
│   ├── src/main/kotlin/
│   └── build.gradle.kts
├── android/                       # Android app
│   ├── src/main/kotlin/
│   ├── src/main/res/
│   └── build.gradle.kts
├── ios/                           # iOS app (Xcode project)
│   └── OpenBallistics/
├── garmin/                        # ConnectIQ terminal (multi-device)
│   ├── source/
│   ├── resources/
│   ├── resources-round-*/         # Per-resolution layouts
│   └── monkey.jungle
├── database/                      # Static data
│   ├── bullets/                   #   Bullets (BC, dimensions)
│   ├── ammunition/                #   Factory loads
│   ├── powders/                   #   Powders (names)
│   ├── scopes/                    #   Riflescopes (models, parameters)
│   ├── reticles/                  #   Reticles (parametric SVG)
│   ├── targets/                   #   Targets (dimensions, graphics)
│   └── schema/                    #   JSON Schema definitions
├── docs/
├── build.gradle.kts
├── settings.gradle.kts
└── LICENSE                        # Apache 2.0
```

### 2.3 Data Flow

```
Sensors (Calypso/WeatherFlow)
       │ BLE
       ▼
┌─────────────────┐       BLE/JSON       ┌──────────────────┐
│  Android/iOS    │◄────────────────────►│ Smartwatch        │
│  (Server)       │                      │ (Terminal+Sensor) │
│                 │                      │                   │
│ • Ballistic eng.│                      │ • Display/Input   │
│ • Profile mgmt  │                      │ • Barometer       │
│ • Sensor fusion │                      │ • Thermometer     │
│ • Shot log      │                      │ • GPS / Compass   │
│ • Maps          │                      │ • Humidity*       │
└─────────────────┘                      └──────────────────┘
       │                         * depending on model
       ▼
  Internet (weather, maps, elevation)
```

---

## 3. Ballistic Engine (core/)

### 3.1 Approach

Clean-room reimplementation of ballistic algorithms based on published
physical models (Litz, McCoy, ICAO standard atmosphere). Reference
implementation: `py-ballisticcalc` (o-murphy, LGPL-3.0). No source code
is copied — only mathematical formulas and physical models, which are not
copyrightable. The resulting Kotlin code is an original work under Apache 2.0.

**Testing strategy:**
1. Python fixture generator (`fixtures/generate.py`) uses py-ballisticcalc
   as a permanent dev dependency to compute reference trajectories
2. Fixtures generated **on-the-fly** during each `./gradlew test` run —
   not committed to repo; each run uses a different random seed
3. Combinatorial parameter space (~130k combinations) across 4 calibers
   (.223 Rem to 800m, .308 Win to 1200m, 6.5 Creedmoor to 1200m,
   .338 Lapua Mag to 2500m), wind (4 directions × 2 speeds), atmosphere,
   slope, cant, Coriolis, twist, V0, and drag model variations
4. 1000 random combinations sampled per run; 5 random distance checkpoints
   per fixture (5000 trajectory comparisons total, <5s generation time)
5. No zero/trivial parameter values — every fixture has non-trivial
   conditions to ensure each parameter is actually exercised
6. Kotlin parameterized tests load generated JSON and compare against
   our engine within tolerances (≤0.5 cm drop/windage, ≤1 m/s velocity)
7. On failure: seed printed to stdout → `FIXTURE_SEED=<N>` reproduces it
8. Over time, random seeds statistically cover the entire parameter space
9. Generator stays permanently as a regression safety net
10. Aerodynamic jump tested separately (py-ballisticcalc does not implement it)

### 3.2 Physical Model

**Point-mass 3DOF** with extensions:
- ODE solver: Runge-Kutta 4 (RK4) with adaptive step
- Drag models: G1, G7, (future: custom drag curves)
- Segmented BC (velocity-dependent)

**Corrections:**
- ICAO atmospheric model (temperature, pressure, humidity, altitude)
- Spin drift (Litz formulas)
- Coriolis effect (dependent on latitude and azimuth)
- Aerodynamic jump (Litz formula — depends on crosswind and gyroscopic stability factor Sg). Dynamic stability factor (Sd) omitted in MVP (requires additional bullet data). Stability factor computed internally by the engine (Miller's formula) from twistRate, bulletDiameter, bulletLength, bulletWeight, muzzleVelocity, and air density.
- Terrain slope (Rifleman's Rule: horizontal range = slant range × cos(slope))
- Cant (weapon tilt)

### 3.3 Multi-Zone Wind Model

Distance to target divided into N zones (default 1). Each zone:

```
WindZone {
  rangeStart: Distance       // zone start (m)
  rangeEnd: Distance         // zone end (m)
  direction: ClockDirection  // 1-12, see definition below
  sustained: Speed           // sustained wind (m/s, mph, knots)
  gusts: Speed               // gusts (same direction — simplification, see note)
  source: DataSource         // manual | internet | sensor | sensor_stream
}
```

```
DataSource = manual              // MVP
           | internet            // v1.1 (weather from GPS)
           | sensor              // v1.1 (one-time reading from BLE sensor)
           | sensor_stream       // v1.1 (continuous stream from BLE sensor)
```

```
ClockDirection = Int  // 1-12 (see §3.3 convention definition)
```

```
TwistRate {
  rate: Double        // N in "1:N" (e.g. 11.0 for 1:11")
  unit: in | mm       // per Settings
  direction: RH | LH  // right-hand / left-hand
}
```

```
ReticleRef = String   // ID from database/reticles/ (e.g. "dls-1")
```

**Clock direction convention:**
```
      12 (headwind)
       |
 9 ----+---- 3
(left) |  (right)
       6 (tailwind)
```
Shooter's perspective. 12 o'clock = wind from target (headwind). 3 o'clock = full-value
crosswind from the right. 6 o'clock = tailwind. 9 o'clock = full-value crosswind from the left.
Decomposition: crosswind = speed × sin((clock - 12) × 30°),
headwind = speed × cos((clock - 12) × 30°).
Sign convention: crosswind > 0 = wind from shooter's right (3 o'clock), crosswind < 0 = wind from left (9 o'clock).
The engine decomposes WindZone into crosswind and headwind. Aerodynamic jump depends only on the crosswind component (headwind/tailwind does not cause AJ).

**Gusts — simplification:** gusts have the same direction as sustained.
In reality gusts may come from a different direction, but for MVP purposes
this accuracy is sufficient. Consequence: with crosswind,
`elevationGusts` differs from `elevation` minimally. With
headwind/tailwind the difference can be significant (headwind increases drop,
tailwind reduces it). `windageGusts` differs significantly (higher speed
= greater lateral deflection).

**Calculations:** the engine computes two corrections per zone — for sustained and gusts.
The final result is two values: nominal correction (sustained) and maximum
correction (gusts).

### 3.4 Engine Input

```
BallisticInput {
  // Weapon
  zeroDistance: Distance
  zeroAtmosphere: AtmosphericData // conditions at zeroing (required)
  sightHeight: Distance          // mm, bore axis → scope axis
  twistRate: TwistRate           // 1:N inches, direction (RH/LH)
  barrelLength: Distance         // mm (optional, for V0 estimation)

  // Ammunition
  dragModel: G1 | G7             // drag model (default G7)
  bulletBC: BallisticCoefficient  // BC value for selected dragModel, segmented
  bulletWeight: Mass             // grains
  bulletDiameter: Distance       // mm / cal
  bulletLength: Distance         // mm
  muzzleVelocity: Speed          // m/s — value already interpolated by the application layer from velocityTable based on current temperature

  // Target
  targetDistance: Distance       // line-of-sight distance (slant range). Engine applies Rifleman's Rule internally (horizontal range = slant range × cos(slope)).
  slope: Angle                   // degrees, + = target higher
  cant: Angle                    // degrees, weapon tilt

  // Wind
  windZones: List<WindZone>

  // Atmosphere (current conditions)
  atmosphere: AtmosphericData

  // Coriolis
  latitude: Angle
  azimuth: Angle                 // shot direction (0-360°)
}

AtmosphericData {
  temperature: Temperature       // °C — air temperature, also used for
                                 // V0 interpolation (we assume powder = air,
                                 // separation for future)
  pressure: Pressure             // hPa — barometric pressure (priority
                                 // for calculations; altitude used only for
                                 // Coriolis/GPS when barometer unavailable)
  humidity: Percentage
  altitude: Distance             // m above sea level
}
```

**Note on air density (density ratio):** The engine computes air density for
zeroing conditions and current conditions. The density ratio corrects the
aerodynamic drag coefficient — higher pressure/lower temperature = denser air
= more drag = greater drop.

**Note on temperature:** in MVP we assume powder temperature = air temperature.
In the future, separation is possible (powder in vest pocket,
chamber heating during rapid fire).

**Altitude vs pressure priority:** the engine uses `pressure` directly for
air density correction. `altitude` is used for Coriolis and as a fallback
to estimate standard pressure (ICAO) when no barometer is available.

**Clicks are not part of the engine** — the engine returns a raw angular value
(`AngularValue`). Conversion to clicks (accounting for `actualClickValue`
and `clickDisplayMode`) is performed by the UI/presentation layer.

### 3.5 Engine Output

```
BallisticSolution {
  // Corrections (sustained wind) — raw angular values from engine
  elevation: AngularValue        // mrad
  windage: AngularValue          // mrad

  // Corrections (gusts)
  elevationGusts: AngularValue
  windageGusts: AngularValue

  // Additional
  drop: Distance                 // drop (m internally, UI converts to cm/in)
  drift: Distance                // lateral deflection (m internally, UI converts to cm/in)
  velocity: Speed                // velocity at target
  energy: Energy                 // energy at target
  timeOfFlight: Duration         // time of flight

  // Distance table (range card)
  rangeTable: List<RangeEntry>   // every N meters from 0 to max
}
```

```
RangeEntry {
  distance: Distance
  elevation: AngularValue
  windage: AngularValue
  drop: Distance
  drift: Distance
  velocity: Speed
  energy: Energy
  timeOfFlight: Duration
}
```

The engine returns raw `AngularValue` (always in mrad internally).
The UI layer converts to display format:

```
DisplayCorrection {
  angular: Double                // angular value (mrad or MOA, per Settings)
  clicks: Int                    // rounded to whole clicks
  drop: Distance                 // equivalent in cm/inches
}
```

UI logic (see §4.6.2 — clickDisplayMode):
- clicks = round(engineValue / actualClickValue)
- precise mode: angular = clicks × actualClickValue
- nominal mode: angular = clicks × clickValue
- when `actualClickValue` is null → fallback to nominal (precise unavailable)

### 3.6 Truing

Profile correction based on actual impacts:

```
TruingInput {
  observedDistance: Distance      // distance at which shots were fired
  observedElevation: AngularValue // correction actually dialed (mrad/MOA)
  // system compares with calculated and corrects V0 or BC
}
```

Methods:
1. **V0 truing** — muzzle velocity correction (recommended for <500m)
2. **BC truing** — ballistic coefficient correction (>500m)

Truing is accessible from Equipment → selected ammunition profile → Truing.
The wizard guides the user: enter distance → enter the correction actually dialed
→ system compares with calculated → proposes V0 or BC correction.

---

## 4. Android Application — Screens and UX

### 4.1 Navigation

```
Bottom Nav:
├── [Shoot]         — main screen, correction calculator
├── [History]       — shot log, shooting sessions
├── [Equipment]     — weapons, ammunition, scopes, targets, truing
└── [Settings]      — units, display preferences
```

### 4.2 Main Screen — Shoot

**Layout:**
```
┌──────────────────────────────────┐
│  [Sako TRG .308 / GGG 175gn ▼]   │  ← single select: weapon + ammunition
├──────────────────────────────────┤
│                                  │
│     ↑ 3.5 mrad  (14 clicks)      │  ← elevation correction
│     ← 1.2 mrad  (5 clicks)       │  ← windage correction
│                                  │
│    drop: -127 cm                 │
│    gusts: ↑3.8 / ←1.5 mrad       │  ← correction for gusts
│                                  │
│    V0: 780 m/s (18°C) ⚠          │  ← V0 for ammunition, ⚠ if estimate
│    SF: 1.65 🟢                   │  ← stability factor (color per threshold)
│                    [🔭 Scope]    │  ← scope view (v1.1, hidden in MVP)
├──────────────────────────────────┤
│ Distance:  [  850 m  ] [+][-][🪄]  ← 🪄 → map (v1.2)
│ Slope:     [  +3.2°  ]      [🪄]  ← 🪄 → slope from camera (v1.2)
│ Cant:      [   0.0°  ]      [🪄]  ← 🪄 → phone inclinometer (v1.2)
├──────────────────────────────────┤
│ Wind Zone 1 [sensor ●]           │
│   5:00  sustained: 3.2 m/s       │
│         gusts:     4.8 m/s       │
│ [+ Add zone]                     │
├──────────────────────────────────┤
│ Atmosphere [🪄 ●]                │
│  18°C  1013 hPa  45%  320m       │
├──────────────────────────────────┤
│ Coriolis [🪄]                    │
│  Lat: 52.2°N  Az: 270°           │
├──────────────────────────────────┤
│ [Range Card] [Charts]            │
├──────────────────────────────────┤
│ 🏁 II Runda PPLRS, Zielonka      │  ← active session (tap → edit)
│ Seria 3: oceniana  ⏱ 9:32 [⏸]   │  ← timer + pause/start
└──────────────────────────────────┘
```

Tapping the session section opens the series editor (session name,
series type, time limit, notes, end session). The main screen shows
only the essentials: session name, current series info, and the
timer with a single Start/Pause/Resume button.

After a series completes (Stop or timer reaches 0), the series name
and type are cleared after 10 minutes of inactivity — the next series
starts fresh. Session name and range persist until explicitly ended.

When no session is active, the bottom section shows:
```
├──────────────────────────────────┤
│ [🏁 Nowa sesja]                  │  ← starts a new session
│ [Range Card] [Charts]            │
└──────────────────────────────────┘
```

**Profile/ammunition** — one dropdown "Weapon / Ammunition". Ammunition is
assigned to a specific weapon (e.g. GGG 175gn defined only for
Sako TRG, not for AR-10). Select weapon → then ammunition within it.

**V0** — displayed with ammunition, interpolated from the temp→V0 table.
⚠ icon when value is a manufacturer estimate (see 4.6.3).

**🪄 buttons** (magic wand icon, like Photoshop) — lead to helpers:
- Distance 🪄 → map screen with pins (v1.2)
- Slope 🪄 → phone camera with crosshair + level (v1.2)
- Cant 🪄 → inclinometer from phone accelerometer
- Coriolis 🪄 → latitude from GPS, azimuth from phone compass

**Interactions:**
- Distance: +/- buttons, manual entry, or 🪄 (map)
- Wind: per zone — manual, from internet, from BLE sensor, stream from sensor
- Atmosphere: manual, from internet (GPS → weather), from sensor, from watch
- Coriolis: manual entry (latitude + azimuth), or 🪄 (GPS + compass)
- Range Card: correction table every N meters (configurable step)
- Charts: drop, velocity, energy vs. distance

### 4.3 Scope View (v1.1)

Rendered view through the scope. The view always centers on the **aiming point** —
the point the shooter should aim at after dialing the calculated correction.
With automatically selected correction, the aiming point is near the center of
the target (bullseye). With manual turret adjustment, the point shifts accordingly.

**View elements:**
- Reticle (parametric SVG, FFP or SFP)
- Target to scale (from target database, scaled to distance and magnification)
- Simplified horizon
- Virtual turrets (elevation + windage):
  - Default set to calculated correction
  - Interactive — swipe/scroll moves the reticle relative to the target
- Zoom control (scope magnification, e.g. 5x-50x)
- FFP support (reticle scales with zoom) and SFP (reticle fixed)

**Aiming point without wind** (toggle on/off):
Displays an additional marker showing the aiming point
ignoring windage — the shooter sees how much wind shifts the
aiming point from the "no wind" position.

### 4.4 Map Screen (v1.2)

- OpenStreetMap with two pins (shooter + target)
- Calculates: distance, azimuth, slope (from SRTM elevation data)
- Results feed the calculator automatically
- Phone GPS as default shooter position

### 4.5 History Screen

History is **contextual per profile** — filtered by default to the
currently selected weapon+ammunition. The filter can be cleared to see
all sessions (e.g. all starts at a given competition with different equipment).

#### 4.5.1 History Navigation

Entering History shows a breakdown:
```
┌───────────────────────────────────┐
│ 🔍 Search / Filter                │
│ [Weapon: Sako TRG .308 / GGG ▼] ✕ │  ← auto-filter per profile (clearable)
├───────────────────────────────────┤
│ 📋 Competitions                   │
│   Puchar Polski 2025 (3 sessions) │
│   LREC 2025 (2 sessions)          │
│   Mistrzostwa Polski Orzysz (1)   │
├───────────────────────────────────┤
│ 🎯 Ranges                         │
│   Zielonka (12 sessions)          │
│   Orzysz (4 sessions)             │
│   Raszyn (7 sessions)             │
└───────────────────────────────────┘
```

**Filtering and search:**
- By range, competition, string type (scored/sighters/training),
  distance, temperature, date
- Autocomplete from previously entered values

Selecting a competition or range → list of sessions grouped by date.

#### 4.5.2 Shooting Session (ShootingSession)

A session represents a multi-day event (competition) or a range visit.
The shooter creates a session once and adds shot strings to it over time.
Sessions are **live objects** — they are created on the Shoot screen,
not saved after the fact.

**Session lifecycle:**
1. "Nowa sesja" on Shoot screen → fill: competition, range, location
2. Session is now **active** — shown on Shoot screen with "Następna seria"
3. Each "Następna seria" adds a `ShotString` with timer
4. Session can stay active across days (multi-day competition)
5. "Zakończ sesję" closes it — moves to History as read-only

Only one session can be active at a time.

```
ShootingSession {
  // Description (structured fields, autocomplete from previous entries)
  range: String                  // shooting range: "Zielonka", "Orzysz"
  competition: String?           // competition: "Puchar Polski 2025", null = training
  description: String?           // additional description
  createdAt: DateTime            // when session was created
  closedAt: DateTime?            // when session was closed (null = active)
  location: GpsCoordinates?      // suggested from GPS
  notes: String?

  // Snapshot of calculator parameters (deep copy, not a reference —
  // later truing/profile edits do not change historical data)
  profile: RifleProfile          // deep copy; profile.ammunition contains
                                 // ONLY the ammunition used in the session
  // Shot strings
  strings: List<ShotString>
}
```

**Computed from strings (not stored separately):**
- `WindRange` (min/max sustained/gusts, directions across all strings)
- `CorrectionRange` (elevation/windage min/max across all strings)
- `duration` (sum of actualDuration across all strings)

#### 4.5.2.1 "Następna seria" Flow

From the Shoot screen (active session):
1. Tap "Następna seria" → pre-filled form:
   - Distance: current calculator distance (editable)
   - Type: scored / sighters / training (autocomplete from session)
   - Time limit: picker (common presets: 3, 5, 10, 15 min + custom)
2. Confirm → string created with status `in_progress`, timer section
   appears on Shoot screen
3. **▶ Start** → timer counts down, `startedAt` recorded
4. **⏸ Pause** → timer stops (cease fire, animal on range).
   `pauseCount` incremented. Calculator remains functional.
5. **▶ Resume** → timer continues
6. **⏹ Stop** → string status → `completed`, post-series screen
   (optional: mark target, enter applied corrections, notes)
7. Timer reaches 0 → vibration/sound alert, string auto-completed

The timer does not block the calculator — the shooter can change wind,
distance, etc. while the timer runs. **All parameter changes during a
running series are recorded in `parameterLog`** (black box). This includes
distance, wind (direction, speed, gusts), atmosphere, slope, cant — every
change with a timestamp.

**Quick correction filter:** if two entries changing the same field appear
within a short interval (e.g. distance 592→850 in 3 seconds), the first
is marked as a correction (likely a typo). The threshold is configurable in Settings (default: 5 seconds).
On PRS/tactical matches the threshold should be lowered or disabled (0)
because distance changes can be rapid and intentional. The filter only
affects analysis views — the raw log always retains all snapshots.

**Change sources:**
- `manual` — user changed a parameter (distance, wind, etc.).
  Typically one field per entry.
- `sensor` — BLE sensor pushed new data (Kestrel, Calypso, WeatherFlow,
  Garmin barometer). One entry with all changed fields from the push.

**Post-series analysis** (computed from parameterLog):
- Distance breakdown: which distances were engaged, time spent at each
- Wind heat map: direction distribution (e.g. 70% from 2, 10% from 1...),
  speed range (min–max sustained/gusts)
- Condition timeline: how atmosphere/wind changed over the series
- Correction history: what the engine recommended at each moment

#### 4.5.3 Session View

Entering a session shows:

**Header:** range, competition, dates, total duration (computed)

**Session summary (computed from strings):**
- Weapon + ammunition, V0, SF
- Wind range (min–max sustained/gusts, directions)
- Correction range (elevation min–max, windage min–max)
- Conditions spread across strings (atmosphere, slope, Coriolis)

**Shot string table:**
```
┌─────┬───────────────┬──────────┬───────┬───────┐
│  #  │ Distance      │ Type     │ Timer │Target │
├─────┼───────────────┼──────────┼───────┼───────┤
│  1  │  300m         │ sighters │  3:00 │  📷  │
│  2  │  300m         │ scored   │ 10:00 │  📷  │
│  3  │  600m         │ sighters │  3:00 │  🎯  │
│  4  │  600m         │ scored   │ 10:00 │  📷  │
│  5  │  300,600,800m │ scored   │ 20:00 │  📷  │  ← multi-distance
└─────┴───────────────┴──────────┴───────┴───────┘
```

String type (not session type) — because a single session can have both sighters and scored strings.
Timer column shows the time limit for the string.

#### 4.5.4 Shot String (ShotString)

```
ShotString {
  index: Int
  startDistance: Distance          // distance at series start (from calculator)
  // Additional distances are captured automatically in parameterLog
  // when the shooter changes distance during the series.
  // Computed: distances() → unique distances from parameterLog
  // Examples: single-distance (300m), multi-distance F-Class (300,600,800m),
  // tactical PRS (unknown beforehand, entered live from rangefinder)
  stringType: scored | sighters | training
  status: in_progress | paused | completed
  shotCount: Int
  notes: String?

  // Timer
  timeLimit: Duration             // series time limit (e.g. 10 min)
  startedAt: DateTime             // when ▶ Start was pressed
  actualDuration: Duration        // actual elapsed time (excluding pauses)
  pauseCount: Int                 // how many times paused (0 = uninterrupted)

  // Initial state — snapshot of all parameters at series start.
  // This is the baseline for parameterLog (log records only changes,
  // full state at any point = initial state + log replay).
  atmosphere: AtmosphericData
  slope: Angle?
  cant: Angle?
  latitude: Angle?
  azimuth: Angle?
  windZones: List<WindZone>

  // Black box — records only changed parameters during a running
  // series. Sensor batch updates produce one entry with multiple
  // fields. Manual changes produce one entry with one field.
  // Full state at time T = initial fields above + replay log up to T.
  parameterLog: List<ParameterChange>

  // Target — one of two:
  targetMode: VirtualTarget | PhotoTarget  // sealed class — implementation: VirtualTarget or PhotoTarget
  aimPoint: Point                // aiming point (default: target center); mm from target center; for PhotoTarget center = calibration point or image center

  // Velocities (optional)
  velocities: List<Speed>?       // per shot, from Xero C1 or manual entry
}

ParameterChange {
  timestamp: DateTime
  source: manual | sensor          // what triggered the change
  values: Map<String, String>      // only changed fields → new value
  // Key examples: "distance", "windZone1.sustained", "windZone1.clock",
  //   "atmosphere.temp", "atmosphere.pressure", "slope"
  // Sensor batch (e.g. Kestrel push) → one entry, multiple keys:
  //   {"atmosphere.temp": "18", "atmosphere.pressure": "1013",
  //    "windZone1.sustained": "3.5", "windZone1.clock": "4"}
  // Manual change → one entry, one key:
  //   {"distance": "850"}
  // Delta computed by comparing with previous entry for same key.
}


```

```
VirtualTarget {
  targetId: String               // ref to target from database (database/targets/)
  shotMarkers: List<ShotMarker>  // shot holes marked by user
}

PhotoTarget {
  imageUri: String               // path to target photo
  calibrationPoint1: PixelPoint  // calibration point 1 (px)
  calibrationPoint2: PixelPoint  // calibration point 2 (px)
  calibrationDistance: Distance  // known distance between points (e.g. 20cm)
  shotMarkers: List<ShotMarker>  // shot holes marked by user
}

ShotMarker {
  position: Point                // position on target (mm from target center)
  isFlyer: Boolean               // marked as flyer (auto or manual)
}

Point {
  x: Double                      // mm, relative to target center (right = +)
  y: Double                      // mm, relative to target center (up = +)
}

PixelPoint {
  x: Int                         // px, coordinates on the photo
  y: Int
}
```

**VirtualTarget** — target from the app database (e.g. IPSC, PRS plate):
- User clicks on the target drawing to mark impacts
- Target to scale, shot holes scaled to caliber

**PhotoTarget** — photo of a target with calibrated scale:
1. User takes a photo
2. Marks 2 known points and the distance between them (e.g. 20cm)
   → system knows the px/mm scale
3. Marks shot holes (by clicking on the photo)
4. Shot holes scaled to caliber

**Aiming point (aimPoint)** — in both modes the user specifies the aiming
point for the string. Default: target center. But a target may have
multiple aiming points (e.g. multi-bull target, PRS with several plates) —
in each string the shooter engages a different point. Group analysis
(MPI, correctionError) is calculated relative to the selected aimPoint, not
the target center.

Both modes support multiple strings on one target (grouping practice —
string 1 on one group, string 2 on another, etc.).

#### 4.5.5 Group Analysis

Calculated automatically after marking shot holes (in both modes):

```
GroupAnalysis {
  groupSize: Distance            // mm, MOA, mrad
  meanPointOfImpact: Point       // mean point of impact (MPI)
  correctionError: AngularValue  // MPI vs aiming point
  extremeSpread: Distance        // ES (max spread)
  standardDeviation: Distance    // SD
  flyers: List<ShotMarker>       // automatic flyer detection
  circularErrorProbable: Distance? // CEP (optional)
}
```

#### 4.5.6 Velocities

Optional list of velocities per shot in a string:
- Manual entry
- Import from Xero C1 (future)
- Calculates: mean V0, SD, ES of velocities

**Session export:** JSON, CSV.

### 4.6 Profile Screen

#### 4.6.1 Rifle Profile

```
RifleProfile {
  id: String                     // unique identifier
  name: String                   // "Sako TRG 42 .308"
  zeroDistance: Distance          // 100m
  zeroAtmosphere: AtmosphericData // conditions at zeroing (required;
                                  // 🪄 button fetches current conditions)
  sightHeight: Distance          // mm (manual or from photo measurement)
  twistRate: TwistRate           // 1:11", RH
  barrelLength: Distance?         // 660mm (optional; required for photo sight height)
  scope: ScopeProfile
  ammunition: List<AmmunitionProfile>  // multiple ammunition types
}
```

#### 4.6.2 Scope Profile

```
ScopeProfile {
  model: String                  // "Delta Stryker HD 5-50x56"
  reticle: ReticleRef            // ref to reticle from database
  focalPlane: FFP | SFP
  zoomRange: Range<Double>       // 5.0 .. 50.0
  clickValue: AngularValue       // 0.1 mrad / 0.25 MOA (nominal)
  actualClickValue: AngularValue? // actual click value (e.g. 0.098 mrad)
                                  // if null → use nominal
  reticleTrueMagnification: Double? // reticle magnification (SFP only)
                                  // e.g. 40x for Delta Stryker HD 5-50x56
                                  // = magnification at which reticle subtensions
                                  // correspond to angular values
                                  // null for FFP (reticle always to scale)
                                  // required for SFP
                                  // Validation: when focalPlane=SFP field required; when focalPlane=FFP must be null. Missing value with SFP → warning in UI, fallback to max zoom.
  maxElevation: AngularValue     // adjustment range
  maxWindage: AngularValue
  tubeDiameter: Distance         // 34mm
}
```

Scope database with autocomplete — user starts typing the model,
system suggests from the database and fills in parameters.

**Click compensation:** scopes often have inaccurate clicks (e.g.
nominal 0.1 mrad = actual 0.098 mrad). The `actualClickValue` field
lets the user enter the measured click value.

Correction calculation logic (two modes in Settings):

**"Precise" mode:**
1. Engine calculates the physical correction (e.g. 1.37 mrad)
2. Divides by `actualClickValue` → rounds to whole clicks
   (e.g. 1.37 / 0.098 = 13.98 → **14 clicks**)
3. Displayed angular correction = clicks × actualClickValue
   (14 × 0.098 = **1.372 mrad**)
4. Shooter knows exactly how many real mrad they're dialing

**"Nominal" mode:**
1. Engine calculates the physical correction (e.g. 1.37 mrad)
2. Divides by `actualClickValue` → rounds to whole clicks
   (e.g. 1.37 / 0.098 = 13.98 → **14 clicks**)
3. Displayed angular correction = clicks × nominal clickValue
   (14 × 0.1 = **1.4 mrad**)
4. Simpler, consistent with markings on the scope turret

In both modes the number of clicks is identical — only the
displayed angular value differs (actual vs. nominal).

The user can measure the click value at the range (tall target test)
or based on data from truing sessions.

#### 4.6.3 Ammunition Profile

```
AmmunitionProfile {
  id: String                     // unique identifier
  name: String                   // "GGG .308W 175gn HPBT"
  source: Factory | Handload     // informational — does not enforce field validation;
                                 // handload fields optional regardless of source

  // Bullet
  bullet: BulletProfile          // from database or custom

  // Velocity — ALWAYS a table, even for a single measurement point
  velocityTable: List<VelocityEntry>  // min. 1 entry: temp → V0
  // Engine interpolates V0 from current temperature

  // Handload data (if handload)
  brass: String?                 // "Lapua .308 Win"
  primer: String?                // "CCI BR-2"
  powder: String?                // "RS N52"
  charge: Mass?                  // 42.45 gn
  oal: Distance?                 // overall length
  cbto: Distance?                // cartridge base to ogive
}

VelocityEntry {
  temperature: Temperature       // °C
  velocity: Speed                // m/s
  isEstimate: Boolean            // true = from box/table, false = measured
}
```

**V0 logic:**
- The `velocityTable` is the sole source of V0 (no separate field)
- For factory ammunition: 1 entry with V0 from the box at 20°C, `isEstimate=true`
- ⚠ icon when `isEstimate=true` with info: "Manufacturer estimate. Measure V0 with a chronograph for your rifle."
- User adds chronograph measurements (various temperatures) → `isEstimate=false`
- Engine interpolates V0 linearly between the nearest table points

```
BulletProfile {
  name: String                   // "Sierra MatchKing 175gn"
  bcG1: Double?                  // G1: 0.505
  bcG7: Double?                  // G7: 0.260 (preferred by default)
  bcG7Segmented: List<BcSegment>? // optional: velocity-dependent BC
  // BcSegment { velocity: Speed (fps), bc: Double }
  // BcSegment velocity breakpoints always in fps (industry convention), not converted per user settings.
  preferredDragModel: G1 | G7    // default G7
  weight: Mass                   // 175 gn
  diameter: Distance             // 7.82mm / .308"
  length: Distance               // 31.5mm
}
```

The database stores both G1 and G7 when available. Default G7
(better for modern boat-tail bullets). User can switch.

**Stability Factor (SF)** — displayed with ammunition in the context of
the selected weapon (SF depends on weapon twist rate + bullet parameters).
Calculated by the engine (Miller's formula) from twistRate, bulletDiameter,
bulletLength, bulletWeight, muzzleVelocity (V0), and air density.
Changing V0 (e.g. different temperature → different interpolation from velocityTable)
affects SF — lower V0 = lower SF.

Displayed with a color indicator:
- 🟢 **SF ≥ 1.5** — stable (optimal range 1.5–3.0)
- 🟡 **1.0 ≤ SF < 1.5** — marginal stability (may work, but
  accuracy drops, especially in transonic conditions)
- 🔴 **SF < 1.0** — insufficient stabilization (bullet will not be
  stable, clear warning)

Displayed in two places:
- **V0 table (velocityTable)** — SF calculated per row, because each
  temperature gives a different V0 → different SF. Shooter immediately sees at
  what temperature the bullet loses stability:
  ```
  Temp   V0        SF
  -10°C  745 m/s   🟡 1.32
    5°C  762 m/s   🟢 1.48
   20°C  780 m/s   🟢 1.65
   35°C  798 m/s   🟢 1.82
  ```
- **Shoot screen** — SF for current conditions (current temp → V0 →
  SF). Updates when atmosphere changes.

#### 4.6.4 Sight Height from Photo (v1.1)

1. User takes a side-profile photo of the weapon
2. Marks the bore axis — 2 points: muzzle and chamber
   → system knows barrel length from weapon profile → calculates px/mm scale
3. Marks the scope tube — rectangle (side view = rectangle)
   → system knows tube diameter from scope profile
4. System calculates sight height = distance from tube center to bore center

If barrelLength is not set, Photo Sight Height is unavailable — UI displays a message asking to complete the profile.

### 4.7 Slope from Camera (v1.2)

1. User points the phone camera toward the target
2. App displays a crosshair on the camera preview
   → user aligns the crosshair with the target
3. Accelerometer + gyroscope measure the tilt angle in real time
4. Displays the slope value on screen
5. User confirms → value goes to the calculator

### 4.8 Settings

```
Settings {
  units: UnitConfiguration {
    distance: m | yd
    windSpeed: m/s | mph | knots | km/h
    muzzleSpeed: m/s | fps
    correction: mrad | MOA
    drop: cm | inches
    temperature: °C | °F
    pressure: hPa | mmHg | inHg
    weight: gn | g

    // Equipment units
    barrelLength: mm | in
    sightHeight: mm | cm | in
    twistRate: in | mm           // 1:N inches or 1:N mm
  }

  display: DisplaySettings {
    correctionFormat: angular | clicks | both
    correctionStyle: arrows | udlr
      // arrows: ↑ 3.5 mrad  ← 1.2 mrad
      // udlr:   U 3.5 mrad  L 1.2 mrad
    clickDisplayMode: precise | nominal  // precise = actual click value,
                                         // nominal = nominal (from turret)
    rangeCardStep: Distance       // 25m / 50m / 100m
    calculationMode: auto | manual
    quickCorrectionThreshold: Duration  // 5s default; parameter changes
                                        // within this interval are marked
                                        // as corrections (typos) in analysis;
                                        // 0 = disabled (PRS/tactical)
  }
}
```

Wind direction always in clock system (1-12), not configurable.

Each unit configured separately (mixed system). Separate settings
for wind speed and muzzle speed (users may use different units for each).

### 4.9 Devices (subpage in Settings)

List of paired BLE devices with status and configuration.

```
┌──────────────────────────────────┐
│ Devices                          │
├──────────────────────────────────┤
│ 🟢 Tactix 8          connected   │
│    Role: Terminal + Sensor       │
│    Feeds: pressure, temp, GPS    │
│                                  │
│ 🟢 Calypso AB Mini   connected   │
│    Role: Wind sensor             │
│    Feeds: Wind → Zone 1          │
│                                  │
│ ⚪ WeatherFlow Tempest  saved    │
│    Role: Weather station         │
│    Feeds: Wind → Zone 1,         │
│           atmosphere             │
├──────────────────────────────────┤
│ [+ Pair new device]              │
└──────────────────────────────────┘
```

**Device entry:**
```
PairedDevice {
  id: String                       // BLE MAC or internal ID
  name: String                     // user-facing name (editable)
  type: GarminWatch | WindSensor | WeatherStation
  status: connected | disconnected | saved
  feeds: List<SensorFeed>          // what data this device provides
}

SensorFeed {
  dataType: wind | pressure | temperature | humidity | altitude | gps
  target: String?                  // e.g. "windZone1" for wind sensors
}
```

**No automatic fallback.** Each data feed is bound to exactly one device.
If the device disconnects, the feed shows last known value with a ⚠ stale
indicator — it does NOT fall back to another source. The user must
explicitly reconfigure the feed to use a different device or switch to
manual entry. This prevents silent source changes during shooting.

**Interactions:**
- Tap device → detail view (rename, configure feeds, forget)
- Device status also visible contextually on Shoot screen
  (e.g. wind zone shows "sensor ●" indicator, atmosphere shows "auto ●")
- Pairing a wind sensor from Shoot screen (tap "sensor" on a wind zone)
  navigates here with the zone pre-selected as feed target

---

## 5. Garmin Terminal

### 5.1 Application Type

**Device App** — full access to sensors, BLE, persistent state.

### 5.2 Target Devices

**Tier 1** (primary, full features):
Tactix 7/8, Fenix 7X, Fenix 8 47mm+, Enduro 3

**Tier 2** (extended, smaller screens):
Fenix 7, Fenix 8 43mm, Enduro 2, Tactix Delta

**Tier 3** (optional, frozen):
Instinct 2 (92KB — simplified UI)

### 5.3 Multi-Device Support

- Jungle files (`monkey.jungle`) map devices to per-resolution resources
- Runtime detection: resolution, touch/buttons, screen type (MIP/AMOLED)
- Build-time exclusions per device tier
- 7 unique resolutions: 176x176 → 454x454

### 5.4 Main Screen (Always-On)

MIP display — always-on via `onPartialUpdate()` (30ms limit).
Main screen shows **minimum information** in large font:

```
        ╭─────────────╮
       ╱    12         ╲
     ╱   ➘               ╲        ← wind direction arrow on the
    │ 9    ↑ 3.5 mrad   3 │         round screen perimeter,
    │      ← 1.2 mrad     │         at the clock position of
    │                      │         the active zone's wind
    │      gusts:          │
    │  ↑3.5  ←1.8 mrad    │
     ╲                   ╱
      ╲  ⚡Z2 3.2/4.2  ╱          ← active zone + sustained/gusts
        ╰─────⏱ 9:32──╯           ← series timer (when active)
              6
```

The round display uses the screen perimeter as a clock face. The wind
direction arrow sits on the perimeter at the clock position matching
the active zone's wind direction (e.g. arrow at 5 o'clock pointing
inward = wind from 5 o'clock). This mirrors the clock picker used in
wind zone editing (see §5.5.1).

**Wind display:** the main screen shows wind data for the **active zone**
(see Quick Wind Control below). The ballistic correction (elevation,
windage) always accounts for **all zones** — the solver integrates the
full trajectory through every zone. The active zone is only which zone
is displayed and controlled from the main screen.

Buttons/touch → navigate to details and editing (see 5.5).

Garmin displays mrad on the main screen. Clicks available in the Details menu. Format configuration in watch settings (future).

### 5.5 Watch Interactions

**Buttons (5 buttons):**
- **UP/DOWN**: scroll (default) or quick wind change (when Quick Wind ON)
- **ENTER**: action menu
- **ENTER long press**: shortcut to active wind zone editor (skip menu)
- **MENU** (long press): settings, profiles
- **BACK**: back
- **LIGHT**: backlight (system, not interceptable by ConnectIQ)

**Touch (Tactix 7/8, Fenix 8):**
- Tap wind section on main screen → active wind zone editor directly
- Swipe up/down → scroll (same as UP/DOWN buttons)

**Quick Wind Control** (opt-in, OFF by default):

When enabled in watch settings, UP/DOWN on the main screen changes the
sustained wind speed of the active zone by ±0.5 m/s. When disabled,
UP/DOWN behaves as standard Garmin scroll.

Active zone selection:
- **auto** (default): first zone with source = manual (skips sensor-fed
  zones). Typical setup: zone 1 near (sensor) + zone 2 far (manual) →
  active = zone 2.
- **manual override**: user picks a specific zone in watch settings.
- Single-zone setup: that zone is always active.

When Quick Wind is ON, the main screen shows `⚡Z2` (or `⚡Z1` etc.)
to indicate which zone UP/DOWN controls. When OFF, the zone number is
shown without ⚡.

First launch prompt: "Enable quick wind control? UP/DOWN will change
wind speed on the main screen." User can skip — configurable later
in settings.

**Watch display settings** are independent from phone settings. The watch
has its own correction unit and style — a shooter may prefer clicks on the
watch (quick turret adjustment) and mrad on the phone (detailed analysis).
Defaults are synced from phone on first pairing, then independent.

**Menu (ENTER):**
```
├── Wind                                ← first item for fast access
│   ├── Zone 1: direction + sustained + gusts
│   ├── Zone 2: ...
│   └── [+ Add zone]
├── Serie ▶ Start / ⏸ Pause / ⏹ Stop   ← series timer control
├── Distance (manual entry, picker)
├── Atmosphere (auto from barometer / manual)
├── Coriolis
│   ├── Latitude (manual / 🪄 GPS)
│   └── Azimuth (manual / 🪄 compass)
├── Slope (manual)
├── Cant (manual)
├── Profile (weapon + ammunition)
├── Details (V0, velocity at target, TOF, drop, clicks)
├── Settings
│   ├── Quick Wind Control: OFF / ON
│   ├── Active Zone: auto / Zone 1 / Zone 2 / ...
│   ├── Correction Unit: mrad / MOA / clicks
│   └── Correction Style: arrows / U·D·L·R
└── Sync with phone
```

Wind is the first menu item — on button-only watches the fast path to
the active zone is: Enter → Enter (Wind) → Enter (active zone) = 3 presses.
Long press Enter is even faster — goes directly to the active zone editor.

UP/DOWN for wind is opt-in because it overrides standard Garmin scroll
behavior. Wind is the parameter that changes most often during a
competition — hence the shortcut. Distance changes less often — going
into the menu is acceptable.

#### 5.5.1 Wind Direction Clock Picker

Editing a wind zone's direction uses a circular clock picker:

```
        ╭─────────────╮
       ╱    12         ╲
     ╱                   ╲
    │ 9      ➘        3   │     ← arrow at current direction
    │                      │       pointing inward (= wind FROM)
     ╲                   ╱
      ╲     6          ╱
        ╰─────────────╯
```

- The round screen displays clock positions 1-12 on the perimeter
- An arrow at the current position points inward (wind blows FROM
  that direction toward the shooter)
- **UP/DOWN**: rotate arrow to next/previous clock position (cycles
  1→2→...→12→1)
- **Touch**: tap a clock position directly, or swipe up/down to rotate
- **ENTER**: confirm direction, return to zone editor
- Wind zone editor order: direction (first) → sustained → gusts

#### 5.5.2 Series Timer on Watch

When a series is active (started from phone or watch menu), the main
screen shows a countdown timer `⏱ 9:32` at the bottom of the display.

**Timer states on watch:**
- No active series → timer hidden, menu shows "Serie ▶ Start"
- Running → timer counts down, menu shows "Serie ⏸ Pause" and "Serie ⏹ Stop"
- Paused → timer blinks/dimmed, menu shows "Serie ▶ Resume" and "Serie ⏹ Stop"
- Timer reaches 0 → vibration alert, timer shows `0:00`, auto-completed

The timer is synced via BLE — starting/pausing/stopping from either
phone or watch is reflected on both devices.

Series control is the second menu item (after Wind): Enter → Down →
Enter = 3 presses.

### 5.6 BLE Protocol (Watch ↔ Phone)

JSON over Toybox.Communications. Payloads <1KB.
The phone applies the configured `clickDisplayMode` (precise/nominal) before
sending clicks to the watch.
Values in BLE payload always in mrad (regardless of user settings).
Conversion to MOA on the displaying side.

```json
// Watch → Phone: request solution
// The `solve` command overrides only: distance, windZones, slope, cant.
// Everything else (profile, atmosphere, Coriolis) comes from the phone's current state
// (last-known values). The watch does not send the full computation context.
{
  "cmd": "solve",
  "distance": 850,
  "windZones": [{"clock": 5, "sustained": 3.2, "gusts": 4.8}],
  "slope": 3.2,
  "cant": 0
}

// Phone → Watch: solution
{
  "cmd": "solution",
  "elev": 3.5, "wind": 1.2,
  "elevGusts": 3.8, "windGusts": 1.5,
  "elevClicks": 14, "windClicks": 5,
  "drop": -127, "vel": 498, "tof": 1.42
}

// Phone → Watch: push update (e.g. wind sensor) — modifies existing zone by index
{
  "cmd": "windUpdate",
  "zone": 1, "sustained": 3.5, "gusts": 5.1, "clock": 5
}

// Watch → Phone: add new wind zone
{"cmd": "zoneAdd", "rangeStart": 0, "rangeEnd": 400, "clock": 3, "sustained": 2.5, "gusts": 3.0}
// `windUpdate` modifies existing zones by index; `zoneAdd` creates a new zone.

// Zones identified by index (0-based, from shooter to target). zoneAdd appends to the end.
// zoneDelete (future) removes by index. Reindexing after deletion is the phone's responsibility —
// the watch requests the full zone list after the operation.

// Watch → Phone: profile sync
{"cmd": "getProfiles"}
{"cmd": "setProfile", "id": "sako-trg-308"}
{"cmd": "setAmmo", "id": "ggg-175gn"}

// Watch → Phone: barometer from Garmin (with humidity and altitude)
{"cmd": "atmosphere", "pressure": 1013, "temp": 18, "humidity": 45, "altitude": 320}

// Series timer control (bidirectional — either side can control)
// Watch → Phone or Phone → Watch:
{"cmd": "seriesStart", "timeLimit": 600}     // start timer (600s = 10 min)
{"cmd": "seriesPause"}
{"cmd": "seriesResume"}
{"cmd": "seriesStop"}

// Phone → Watch: timer sync (sent periodically and on state change)
{"cmd": "seriesSync", "remaining": 572, "state": "running"}
// state: running | paused | completed
// remaining: seconds left

// Error handling
// Phone → Watch: error
{"cmd": "error", "code": "NO_PROFILE", "msg": "No profile loaded"}
{"cmd": "error", "code": "SOLVE_FAILED", "msg": "Calculation error"}

// Error codes: NO_PROFILE, NO_AMMO, SOLVE_FAILED, NO_SESSION, UNKNOWN_CMD
// On BLE connection loss the watch displays last known data
// with a ⚠ "BLE disconnected" icon. Automatic reconnect.
```

### 5.7 Garmin Offline Mode (v1.2)

Simplified solver in Monkey C:
- Basic point-mass with G1/G7 (no Coriolis, no spin drift)
- Profile and drag table loaded from phone and cached
- Fallback when phone unavailable

---

## 6. Databases

### 6.1 Bullets (database/bullets/)

Sources: GRT community (CC0), Berger, Sierra, LoadDevelopment.com

```json
{
  "id": "sierra-matchking-308-175",
  "manufacturer": "Sierra",
  "name": "MatchKing HPBT",
  "caliber": ".308",
  "weight_gn": 175,
  "diameter_mm": 7.82,
  "length_mm": 31.5,
  "bc_g1": 0.505,
  "bc_g7": 0.260,
  "bc_g7_segmented": [
    {"velocity_fps": 2800, "bc": 0.264},
    {"velocity_fps": 2400, "bc": 0.260},
    {"velocity_fps": 1800, "bc": 0.255}
  ]
}
```

### 6.2 Scopes (database/scopes/)

Database of scope models with parameters. Angular values in the database always in mrad —
the app converts to MOA per user settings:

```json
{
  "id": "delta-stryker-5-50x56-dls1",
  "manufacturer": "Delta Optical",
  "model": "Stryker HD 5-50x56",
  "reticle_id": "dls-1",
  "focal_plane": "SFP",
  "zoom_min": 5.0,
  "zoom_max": 50.0,
  "click_value_mrad": 0.1,
  "reticle_true_magnification": 40.0,
  "max_elevation_mrad": 40.0,
  "max_windage_mrad": 20.0,
  "tube_diameter_mm": 34
}
```

The `actual_click_value_mrad` field is intentionally absent — that's user data, not manufacturer data.

### 6.3 Reticles (database/reticles/)

Parametric SVG generated at runtime. We store subtension values:

```json
{
  "id": "dls-1",
  "name": "DLS-1",
  "manufacturer": "Delta Optical",
  "type": "mrad",
  "svg_template": "reticles/dls-1.svg",
  "subtensions": {
    "center_dot_mrad": 0.1,
    "hashmark_spacing_mrad": 1.0,
    "hashmark_length_mrad": 0.5,
    "stadia_lines": [2, 4, 6, 8, 10]
  }
}
```

**Reticle sources:**
- Manufacturer PDF/image specifications (Horus, Nightforce, Vortex, Delta)
- Manufacturers publish reticles as raster (webp/png/jpg), rarely SVG
- Simple reticles (like DLS-1: crosshair + ticks every 0.5/1.0 mrad) described
  parametrically (JSON → SVG generated at runtime)
- Complex reticles (like Tremor3: hundreds of elements) — manual vectorization
  from manufacturer image or tracing from subtension documentation

**Reticle approach — vector, smoothly scalable:**

For the scope view to work correctly (smooth zoom, target and horizon under
the reticle), every reticle must be vector. Manufacturer images (webp/png) are
marketing material — not suitable for smooth scaling.
Strelok (3000+ reticles), AB, Chairgun — all manually recreate
reticles as vectors. There's no shortcut.

**Reticle creation methods (best to worst):**
1. **Parametric (JSON → SVG at runtime)** — for reticles with regular
   geometry (crosshairs, ticks, stadia). We describe subtension values in JSON,
   generate SVG programmatically. Covers ~80% of simpler reticles (Mil-Dot,
   DLS-1, MOA crosshair, basic tree reticles).
2. **Manual vectorization (SVG)** — for complex reticles (LRD-1P, Tremor3,
   H59). Recreated from: manufacturer image + subtension documentation
   from the manual. Labor-intensive (~1-2h per reticle), but
   one-time. Community contributions.
3. **Raster with multiply blend (fallback)** — import manufacturer image.
   Rendered with `BlendMode.Multiply` — white background disappears (white × anything
   = no change), black lines pass through, colored elements (e.g. red
   illumination crosshair) overlay correctly. Doesn't scale smoothly,
   but works when no vector is available.

**Strategy at launch:** 10-20 most popular reticles (parametric
+ manual SVG). We publish JSON schema and SVG template — community
adds new reticles via PR.

Users can upload their own reticle (SVG or raster).

### 6.4 Targets (database/targets/)

Standard competition targets + upload your own:

```json
{
  "id": "prs-ipsc-alpha",
  "name": "IPSC Classic",
  "width_cm": 46,
  "height_cm": 76,
  "image": "targets/ipsc-classic.svg",
  "zones": [
    {"name": "Alpha", "width_cm": 15, "height_cm": 28},
    {"name": "Charlie", "width_cm": 30, "height_cm": 40}
  ]
}
```

At launch (MVP): targets from Polish LR competitions — LREC, Mistrzostwa Polski
Orzysz, Puchar Polski Zielonka, F-Class.pl.
Users can add their own (upload image + real dimensions).

### 6.5 Powders and Loads (database/powders/, ammunition/)

Powder names (informational) + approximate V0 from load tables:

Sources: Hodgdon RLDC, Vihtavuori, Reload Swiss, GRT community (CC0).

V0 from tables is just a starting point — the user should measure with a chronograph.

```json
{
  "id": "ggg-308-175-hpbt",
  "manufacturer": "GGG",
  "name": ".308W 175gn HPBT",
  "bullet_id": "sierra-matchking-308-175",
  "v0_fps": 2600,
  "v0_barrel_length_in": 26,
  "note": "Manufacturer spec, 26\" barrel"
}
```

Import to `AmmunitionProfile`: `v0_fps` → `VelocityEntry(temperature=20°C, velocity=v0_fps×0.3048, isEstimate=true)`. fps→m/s conversion is automatic.

---

## 7. Integrations

### 7.1 Wind Sensors BLE

**Calypso AB Mini:**
- BLE GATT, custom characteristics
- Data: wind speed, direction (meteorological degrees 0-360°)
- Ref: signalk-calypso-ultrasonic (open-source)
- Conversion to clock: `clock = round(((windDegrees - shooterAzimuth + 180) mod 360) / 30); if clock == 0 then clock = 12`
  (requires shooter azimuth — sourced from: Coriolis azimuth field on Shoot
  screen if set, otherwise phone compass, otherwise Garmin compass via BLE,
  otherwise manual entry. Same value used for both Coriolis and wind conversion.)

**WeatherFlow Tempest:**
- BLE GATT + UDP broadcast + REST API
- Data: wind, temperature, pressure, humidity
- Official API available

### 7.2 Smartwatch as Sensor

The watch is not just a terminal — it's also a source of sensor data.
Depending on the model, it sends to the phone:

| Sensor | Data | Use |
|--------|------|-----|
| Barometer | pressure (hPa) | Atmospheric correction |
| Thermometer | temperature (°C) | Atmo correction + V0 interpolation |
| GPS | lat/lon/alt | Coriolis, azimuth, weather, altitude |
| Compass | heading (°) | Shot azimuth (Coriolis) |
| Altimeter | altitude (m) | Atmospheric correction |

Data sent cyclically or on-demand via BLE protocol (see 5.6).
Each data feed is explicitly bound to one source device by the user
(see §4.9). No automatic fallback — if the source disconnects, data is
frozen with a ⚠ stale indicator until reconnection or manual reconfiguration.

### 7.3 Weather from Internet

Based on GPS (phone or Garmin):
- Temperature, pressure, humidity from public APIs (OpenWeather, MET.no)
- Fallback when no sensors available

### 7.4 Map + Elevation

- OpenStreetMap (map rendering)
- Open Elevation API / SRTM data (Digital Elevation Model)
- Calculates: 3D distance, azimuth, slope
- Phone GPS as shooter position

---

## 8. Implementation Phases

### MVP (Phase 1) — Working Calculator

**Goal:** complete ballistic calculator without "fancy" features.

**Core engine:**
- [ ] Point-mass solver (RK4, G1/G7)
- [ ] ICAO atmospheric model
- [ ] Multi-zone wind (sustained + gusts → two corrections)
- [ ] Spin drift, Coriolis, aerodynamic jump
- [ ] Slope correction
- [ ] Cant correction (manual entry)
- [ ] Truing (V0 and BC)
- [ ] Range card generation

**Android app:**
- [ ] Main screen — calculator (corrections, distance, wind, atmosphere)
- [ ] Corrections in: mrad/MOA, clicks, cm/inches (configured per-parameter)
- [ ] Rifle profiles (CRUD): zero distance, sight height (manual), twist rate
- [ ] Ammunition profiles: bullet (BC G1/G7, weight, length), V0
- [ ] Multiple ammunition types per rifle profile, quick switching
- [ ] Velocity vs. temperature profiles (V0 vs temp table, manual)
- [ ] Bullet database with autocomplete (from database/bullets/)
- [ ] Manual wind entry (clock system, sustained + gusts)
- [ ] Manual atmosphere entry (temp, pressure, humidity, alt.)
- [ ] Unit configuration (mixed system, per-parameter)
- [ ] Range card (correction table every N meters)
- [ ] Charts: drop, velocity, energy vs. distance
- [ ] Custom handloads (brass, primer, powder + charge weight, bullet)

**Garmin terminal:**
- [ ] Device App, Tier 1 devices (Tier 2 after stabilization)
- [ ] Main screen always-on (elevation, windage, clicks, distance, wind)
- [ ] Wind change (UP/DOWN = sustained +/- 0.5 m/s; menu: direction + gusts)
- [ ] Distance change (via menu, picker)
- [ ] Rifle / ammunition profile change
- [ ] Atmosphere from Garmin barometer
- [ ] BLE JSON protocol with phone

**Database:**
- [ ] Bullet database from GRT community (CC0) + public manufacturer data
- [ ] Scope database with autocomplete (several dozen popular models)
- [ ] Target database from Polish LR competitions (LREC, MP Orzysz, PP Zielonka, F-Class.pl)
- [ ] Upload custom targets (image + dimensions)

**Shot log (History):**
- [ ] Structured history: range, competition, string type (scored/sighters/training)
- [ ] Breakdown by Competitions / Ranges, session list per date
- [ ] Filtering: distance, range, competition, string type, temperature
- [ ] Autocomplete from previously entered values (ranges, competitions)
- [ ] Auto-filter per current weapon+ammunition profile (clearable)
- [ ] Save current session (parameter snapshot + optional change history)
- [ ] Session view: parameters, wind/correction ranges, atmosphere
- [ ] Shot string table per session (distance, type, corrections)
- [ ] VirtualTarget — target from database + manual shot hole marking
- [ ] PhotoTarget — target photo, scale calibration (2 points + distance)
- [ ] Multiple strings on one target (grouping practice)
- [ ] Group analysis (group size, MPI, ES, SD, flyer detection)
- [ ] Shot holes scaled to caliber
- [ ] Velocity recording per shot in string (manual, future Xero C1)
- [ ] Notes, location (GPS), JSON/CSV export

---

### v1.1 — Scope View + Sensors

**Scope view:**
- [ ] Reticle rendering (parametric SVG)
- [ ] FFP and SFP support (scaling with zoom)
- [ ] Reticle database (popular: Mil-Dot, Tremor3, MIL-XT, EBR-7C, DLS-1, ...)
- [ ] Upload custom reticle
- [ ] Target to scale (from target database, scaled to distance + zoom)
- [ ] Centering on aiming point (with wind)
- [ ] "No wind" marker (toggle) — shows wind offset
- [ ] Simplified horizon
- [ ] Virtual turrets (auto-set + manual adjustment)
- [ ] Zoom control

**BLE sensor integration:**
- [ ] Calypso AB Mini (wind — auto-feed zone 1)
- [ ] WeatherFlow Tempest (wind + atmosphere)
- [ ] Sensor stream (real-time update)

**Sight height from photo:**
- [ ] Side-profile photo of weapon
- [ ] Marking bore axis (2 points) + scope tube (rectangle)
- [ ] Sight height calculation (tube center → bore center)

**Weather from internet:**
- [ ] Auto-fetch conditions based on GPS
- [ ] Fallback when no sensors available

**Cloud Sync:**
- [ ] Google Drive sync (opt-in)
- [ ] Sync profiles, history, settings, custom targets/reticles
- [ ] Target photos: JPEG 80%, max 2048×2048
- [ ] Conflict resolution: last-write-wins

---

### v1.2 — Map + Advanced

**Map:**
- [ ] OpenStreetMap with shooter + target pins
- [ ] 3D distance, azimuth, slope calculation (SRTM elevation)
- [ ] Auto-feed calculator (distance, slope, Coriolis azimuth)

**Slope from camera:**
- [ ] Phone camera + accelerometer/gyroscope as level
- [ ] Real-time angle measurement to target

**Garmin offline solver:**
- [ ] Simplified point-mass solver in Monkey C
- [ ] Profile and drag table cache from phone
- [ ] Fallback when phone unavailable

**Garmin Tier 3:**
- [ ] Instinct 2 support (frozen, simplified UI)

**iOS app:**
- [ ] SwiftUI app consuming KMP core framework
- [ ] Feature parity with Android

---

## 9. Non-Functional Requirements

### 9.1 Performance
- Single solution calculation: <50ms on mid-range Android (2022+)
- Range card (0-2000m every 25m): <200ms
- Garmin onPartialUpdate: <30ms (SDK requirement)

### 9.2 Offline
- Calculator works fully offline (after profiles are loaded)
- BLE sensors don't require internet
- Map/weather from internet = nice to have, not a blocker

### 9.3 Accuracy
- Validation vs. py-ballisticcalc: 1000 random fixtures generated on-the-fly
  per test run (different seed each time, reproducible via `FIXTURE_SEED`)
- 4 calibers: .223 Rem (to 800m), .308 Win (to 1200m),
  6.5 Creedmoor (to 1200m), .338 Lapua Mag (to 2500m)
- Combinatorial sampling across wind (4 dirs × 2 speeds), atmosphere,
  slope, cant, Coriolis, twist, V0, drag model — no trivial/zero values
- 5 random distance checkpoints per fixture (5000 comparisons per run)
- Initial tolerances: ≤0.5 cm drop/windage, ≤1 m/s velocity, ≤0.005 s TOF
- Validation vs. Sierra/Berger tables (public)
- Target: <0.5% / <0.2 MOA vs. reference calculators at 1000m

### 9.4 Privacy
- No telemetry, no user accounts
- All data local on device by default
- Profile export/import as files (TOML — human-editable, shared with CLI)
- Cloud sync (v1.1) — opt-in, see §9.8

### 9.5 Calculation Mode
- **Auto** (default): every parameter change immediately recalculates the correction
- **Manual**: user changes multiple parameters, then clicks "Calculate"
- Switchable in Settings

### 9.6 Localization (i18n)
- English as base language
- i18n-ready structure from day 1 (string resources, not hardcoded)
- Community translations (Polish and others) via contribution

### 9.7 Zeroing
- `zeroDistance` field in rifle profile (e.g. 100m)
- System assumes the rifle is zeroed at that distance
- No zeroing wizard

### 9.8 Cloud Sync (v1.1)

Optional (opt-in) data sync between devices (phone/tablet).
User works with the same profiles and history on multiple devices.

**Mechanism:** Google Drive (MVP sync), future options (iCloud, WebDAV).

**Synchronized data:**
- Rifle profiles (with ammunition, scopes)
- Shooting session history
- User settings
- Custom targets, reticles

**Target photos — size optimization:**
- Conversion to JPEG 80% quality, max 2048×2048 px
- Original photos local only (not synchronized)
- Estimate: profile + history without photos = a few MB; target photos =
  potentially hundreds of MB → hence compression

**Conflict resolution:** last-write-wins per entity (profile, session).
Per-field merge in the future.

**Privacy:** data in user's Google Drive (app-scoped folder),
not on our servers. No OpenBallistics accounts.

---

## 10. Out of Scope (post-MVP backlog)

- Import/export profiles from competitor formats (Strelok, AB)
- Xero C1 integration (velocity import)
- BLE rangefinder integration (Sig Kilo, Leica, Vectronix) — auto-feed
  distance, azimuth, slope to calculator
- Shot hole recognition from photo (CV/AI)
- WearOS / Apple Watch terminal
- Internal ballistics calculator (pressure, burn rate — inspired by GRT)
- Cloud sync via iCloud, WebDAV
