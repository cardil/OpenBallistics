package org.openballistics.units

enum class DistanceUnit(val suffix: String) {
    METERS("m"),
    YARDS("yd")
}

enum class SpeedUnit(val suffix: String) {
    METERS_PER_SECOND("m/s"),
    MPH("mph"),
    KNOTS("kts"),
    KMH("km/h")
}

enum class CorrectionUnit(val suffix: String) {
    MRAD("mrad"),
    MOA("MOA")
}

enum class DropUnit(val suffix: String) {
    CENTIMETERS("cm"),
    INCHES("in")
}

enum class TemperatureUnit(val suffix: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F")
}

enum class PressureUnit(val suffix: String) {
    HPA("hPa"),
    MMHG("mmHg"),
    INHG("inHg")
}

enum class WeightUnit(val suffix: String) {
    GRAINS("gr"),
    GRAMS("g")
}

enum class LengthUnit(val suffix: String) {
    MILLIMETERS("mm"),
    INCHES("in")
}

enum class SightHeightUnit(val suffix: String) {
    MILLIMETERS("mm"),
    CENTIMETERS("cm"),
    INCHES("in")
}

data class UnitConfiguration(
    val distance: DistanceUnit = DistanceUnit.METERS,
    val windSpeed: SpeedUnit = SpeedUnit.METERS_PER_SECOND,
    val muzzleSpeed: SpeedUnit = SpeedUnit.METERS_PER_SECOND,
    val correction: CorrectionUnit = CorrectionUnit.MRAD,
    val drop: DropUnit = DropUnit.CENTIMETERS,
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val pressure: PressureUnit = PressureUnit.HPA,
    val weight: WeightUnit = WeightUnit.GRAINS,
    val barrelLength: LengthUnit = LengthUnit.MILLIMETERS,
    val sightHeight: SightHeightUnit = SightHeightUnit.MILLIMETERS,
    val twistRate: org.openballistics.model.TwistRateUnit = org.openballistics.model.TwistRateUnit.INCHES
)

fun Distance.format(unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METERS -> "%.1f %s".format(meters, unit.suffix)
    DistanceUnit.YARDS -> "%.1f %s".format(yards, unit.suffix)
}

fun Speed.format(unit: SpeedUnit): String = when (unit) {
    SpeedUnit.METERS_PER_SECOND -> "%.1f %s".format(metersPerSecond, unit.suffix)
    SpeedUnit.MPH -> "%.1f %s".format(mph, unit.suffix)
    SpeedUnit.KNOTS -> "%.1f %s".format(knots, unit.suffix)
    SpeedUnit.KMH -> "%.1f %s".format(kmh, unit.suffix)
}

fun Temperature.format(unit: TemperatureUnit): String = when (unit) {
    TemperatureUnit.CELSIUS -> "%.1f %s".format(celsius, unit.suffix)
    TemperatureUnit.FAHRENHEIT -> "%.1f %s".format(fahrenheit, unit.suffix)
}

fun Pressure.format(unit: PressureUnit): String = when (unit) {
    PressureUnit.HPA -> "%.1f %s".format(hectopascals, unit.suffix)
    PressureUnit.MMHG -> "%.1f %s".format(mmHg, unit.suffix)
    PressureUnit.INHG -> "%.2f %s".format(inHg, unit.suffix)
}

fun Mass.format(unit: WeightUnit): String = when (unit) {
    WeightUnit.GRAINS -> "%.1f %s".format(grains, unit.suffix)
    WeightUnit.GRAMS -> "%.2f %s".format(grams, unit.suffix)
}

fun AngularValue.format(unit: CorrectionUnit): String = when (unit) {
    CorrectionUnit.MRAD -> "%.2f %s".format(milliradians, unit.suffix)
    CorrectionUnit.MOA -> "%.2f %s".format(moa, unit.suffix)
}

fun Distance.formatDrop(unit: DropUnit): String = when (unit) {
    DropUnit.CENTIMETERS -> "%.1f %s".format(centimeters, unit.suffix)
    DropUnit.INCHES -> "%.1f %s".format(inches, unit.suffix)
}

fun Distance.formatLength(unit: LengthUnit): String = when (unit) {
    LengthUnit.MILLIMETERS -> "%.1f %s".format(millimeters, unit.suffix)
    LengthUnit.INCHES -> "%.2f %s".format(inches, unit.suffix)
}

fun Distance.formatSightHeight(unit: SightHeightUnit): String = when (unit) {
    SightHeightUnit.MILLIMETERS -> "%.1f %s".format(millimeters, unit.suffix)
    SightHeightUnit.CENTIMETERS -> "%.1f %s".format(centimeters, unit.suffix)
    SightHeightUnit.INCHES -> "%.2f %s".format(inches, unit.suffix)
}

fun parseDistance(value: String, unit: DistanceUnit): Distance {
    val numeric = value.trim().removeSuffix(unit.suffix).trim().toDouble()
    return when (unit) {
        DistanceUnit.METERS -> Distance(numeric)
        DistanceUnit.YARDS -> Distance.fromYards(numeric)
    }
}

fun parseSpeed(value: String, unit: SpeedUnit): Speed {
    val numeric = value.trim().removeSuffix(unit.suffix).trim().toDouble()
    return when (unit) {
        SpeedUnit.METERS_PER_SECOND -> Speed(numeric)
        SpeedUnit.MPH -> Speed.fromMph(numeric)
        SpeedUnit.KNOTS -> Speed.fromKnots(numeric)
        SpeedUnit.KMH -> Speed.fromKmh(numeric)
    }
}

fun parseTemperature(value: String, unit: TemperatureUnit): Temperature {
    val numeric = value.trim().removeSuffix(unit.suffix).trim().toDouble()
    return when (unit) {
        TemperatureUnit.CELSIUS -> Temperature(numeric)
        TemperatureUnit.FAHRENHEIT -> Temperature.fromFahrenheit(numeric)
    }
}

fun parsePressure(value: String, unit: PressureUnit): Pressure {
    val numeric = value.trim().removeSuffix(unit.suffix).trim().toDouble()
    return when (unit) {
        PressureUnit.HPA -> Pressure(numeric)
        PressureUnit.MMHG -> Pressure.fromMmHg(numeric)
        PressureUnit.INHG -> Pressure.fromInHg(numeric)
    }
}
