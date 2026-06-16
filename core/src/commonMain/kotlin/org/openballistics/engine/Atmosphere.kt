package org.openballistics.engine

import org.openballistics.model.AtmosphericData
import org.openballistics.units.Distance
import org.openballistics.units.Percentage
import org.openballistics.units.Pressure
import org.openballistics.units.Temperature
import kotlin.math.exp
import kotlin.math.pow

/**
 * ICAO Standard Atmosphere model.
 *
 * Computes air density from temperature, pressure, humidity, and altitude.
 * Reference: ICAO Doc 7488/3, McCoy "Modern Exterior Ballistics".
 */
object Atmosphere {

    /** Sea-level standard temperature (K). */
    private const val T0 = 288.15

    /** Sea-level standard pressure (Pa). */
    private const val P0 = 101325.0

    /** Temperature lapse rate (K/m) in troposphere. */
    private const val LAPSE_RATE = 0.0065

    /** Gravitational acceleration (m/s²). */
    private const val G = 9.80665

    /** Molar mass of dry air (kg/mol). */
    private const val M_AIR = 0.0289644

    /** Universal gas constant (J/(mol·K)). */
    private const val R = 8.31447

    /** Specific gas constant for dry air (J/(kg·K)). */
    private const val R_DRY = 287.058

    /** Specific gas constant for water vapour (J/(kg·K)). */
    private const val R_VAPOUR = 461.495

    /** Standard air density at sea level (kg/m³). */
    private const val RHO_STANDARD = 1.2250

    /** Speed of sound constant a0² = γ·R_dry·T0 at 15 °C (m²/s²). */
    private const val GAMMA_AIR = 1.4

    /**
     * Standard pressure at a given altitude using the barometric formula.
     * Troposphere only (valid to ~11 km).
     */
    fun standardPressure(altitude: Distance): Pressure {
        val h = altitude.meters
        val p = P0 * (1.0 - LAPSE_RATE * h / T0).pow(G * M_AIR / (R * LAPSE_RATE))
        return Pressure(p / 100.0) // Pa → hPa
    }

    /**
     * Standard temperature at a given altitude.
     */
    fun standardTemperature(altitude: Distance): Temperature {
        val h = altitude.meters
        val tKelvin = T0 - LAPSE_RATE * h
        return Temperature(tKelvin - 273.15)
    }

    /**
     * Compute air density (kg/m³) from atmospheric conditions using CIPM-2007.
     */
    fun airDensity(data: AtmosphericData): Double {
        val rMolar = 8.314472
        val mA = 28.96546e-3
        val mV = 18.01528e-3

        val tKelvin = data.temperature.celsius + 273.15
        val p = data.pressure.hectopascals * 100.0
        val rhFrac = data.humidity.fraction.coerceIn(0.0, 1.0)

        val a0 = 1.2378847e-5
        val a1 = -1.9121316e-2
        val a2 = 33.93711047
        val a3 = -6.3431645e3
        val pSv = exp(a0 * tKelvin * tKelvin + a1 * tKelvin + a2 + a3 / tKelvin)

        val tCelsius = data.temperature.celsius
        val f = 1.00062 + 3.14e-8 * p + 5.6e-7 * tCelsius * tCelsius

        val pV = rhFrac * f * pSv
        val xV = pV / p

        val tL = tKelvin - 273.15
        val pOverT = p / tKelvin
        val z = 1.0 - pOverT * (1.58123e-6 + (-2.9331e-8) * tL + 1.1043e-10 * tL * tL +
                (5.707e-6 + (-2.051e-8) * tL) * xV + (1.9898e-4 + (-2.376e-6) * tL) * xV * xV) +
                pOverT * pOverT * (1.83e-11 + (-0.765e-8) * xV * xV)

        return (p * mA) / (z * rMolar * tKelvin) * (1.0 - xV * (1.0 - mV / mA))
    }

    /**
     * Density ratio: current air density / standard air density.
     * Used to correct the ballistic coefficient.
     */
    fun densityRatio(current: AtmosphericData, zero: AtmosphericData): Double {
        val rhoCurrent = airDensity(current)
        val rhoZero = airDensity(zero)
        return rhoCurrent / rhoZero
    }

    /**
     * Density ratio relative to ICAO standard sea-level conditions.
     */
    fun densityRatioStandard(data: AtmosphericData): Double {
        return airDensity(data) / RHO_STANDARD
    }

    /**
     * Speed of sound (m/s) at a given temperature.
     * c = sqrt(γ · R_dry · T)
     */
    fun speedOfSound(temperature: Temperature): Double {
        val tKelvin = temperature.celsius + 273.15
        return kotlin.math.sqrt(GAMMA_AIR * R_DRY * tKelvin)
    }

    /**
     * Mach number for a given velocity and temperature.
     */
    fun machNumber(velocity: Double, temperature: Temperature): Double {
        return velocity / speedOfSound(temperature)
    }

    /**
     * ICAO standard atmosphere at sea level.
     */
    val STANDARD = AtmosphericData(
        temperature = Temperature(15.0),
        pressure = Pressure(1013.25),
        humidity = Percentage(0.0),
        altitude = Distance.ZERO
    )
}
