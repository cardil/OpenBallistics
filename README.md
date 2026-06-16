# OpenBallistics

Open-source ballistic calculator for long-range shooting (PRS, F-Class, hunting).

## Overview

OpenBallistics is a system consisting of:
- **Server** -- a mobile application (Android, iOS in the future) that manages
  profiles, performs calculations, and integrates with sensors
- **Terminal** -- a smartwatch application (Garmin ConnectIQ) that displays
  data and allows full parameter control from the wrist

## Features

- Point-mass 3DOF ballistic solver (RK4, G1/G7)
- Multi-zone wind model (sustained + gusts)
- ICAO atmospheric corrections, spin drift, Coriolis, aerodynamic jump
- Truing (V0 and BC correction from observed impacts)
- Rifle, ammunition and scope profiles with bullet/scope databases
- Shot history with series timer, parameter logging and group analysis
- Range card and trajectory charts
- BLE integration with wind sensors (Calypso, WeatherFlow)
- Smartwatch terminal with full parameter control (Garmin ConnectIQ; WearOS, Apple Watch planned)
- CLI tool for scripting and power users

## Tech Stack

- **Core**: Kotlin Multiplatform (shared ballistic engine)
- **Android**: Jetpack Compose
- **Garmin**: ConnectIQ SDK, Monkey C

## License

Apache 2.0 -- see [LICENSE](LICENSE).
