package org.openballistics

import org.openballistics.model.TwistRateUnit
import org.openballistics.units.CorrectionUnit
import org.openballistics.units.DistanceUnit
import org.openballistics.units.DropUnit
import org.openballistics.units.LengthUnit
import org.openballistics.units.PressureUnit
import org.openballistics.units.SightHeightUnit
import org.openballistics.units.SpeedUnit
import org.openballistics.units.TemperatureUnit
import org.openballistics.units.UnitConfiguration
import org.openballistics.units.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitConfigurationTest {

    @Test
    fun defaultValues() {
        val config = UnitConfiguration()
        assertEquals(DistanceUnit.METERS, config.distance)
        assertEquals(SpeedUnit.METERS_PER_SECOND, config.windSpeed)
        assertEquals(SpeedUnit.METERS_PER_SECOND, config.muzzleSpeed)
        assertEquals(CorrectionUnit.MRAD, config.correction)
        assertEquals(DropUnit.CENTIMETERS, config.drop)
        assertEquals(TemperatureUnit.CELSIUS, config.temperature)
        assertEquals(PressureUnit.HPA, config.pressure)
        assertEquals(WeightUnit.GRAINS, config.weight)
        assertEquals(LengthUnit.MILLIMETERS, config.barrelLength)
        assertEquals(SightHeightUnit.MILLIMETERS, config.sightHeight)
        assertEquals(TwistRateUnit.INCHES, config.twistRate)
    }

    @Test
    fun customValues() {
        val config = UnitConfiguration(
            distance = DistanceUnit.YARDS,
            windSpeed = SpeedUnit.MPH,
            muzzleSpeed = SpeedUnit.MPH,
            correction = CorrectionUnit.MOA,
            drop = DropUnit.INCHES,
            temperature = TemperatureUnit.FAHRENHEIT,
            pressure = PressureUnit.INHG,
            weight = WeightUnit.GRAINS,
            barrelLength = LengthUnit.INCHES,
            sightHeight = SightHeightUnit.INCHES,
            twistRate = TwistRateUnit.INCHES
        )
        assertEquals(DistanceUnit.YARDS, config.distance)
        assertEquals(SpeedUnit.MPH, config.windSpeed)
        assertEquals(CorrectionUnit.MOA, config.correction)
        assertEquals(DropUnit.INCHES, config.drop)
        assertEquals(TemperatureUnit.FAHRENHEIT, config.temperature)
        assertEquals(PressureUnit.INHG, config.pressure)
        assertEquals(LengthUnit.INCHES, config.barrelLength)
        assertEquals(SightHeightUnit.INCHES, config.sightHeight)
    }
}
