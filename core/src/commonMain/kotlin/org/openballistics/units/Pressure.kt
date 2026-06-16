package org.openballistics.units

private const val HPA_PER_MMHG = 1.33322387415
private const val HPA_PER_INHG = 33.8638815789

data class Pressure(val hectopascals: Double) {

    val mmHg: Double get() = hectopascals / HPA_PER_MMHG
    val inHg: Double get() = hectopascals / HPA_PER_INHG

    companion object {
        fun fromMmHg(mmHg: Double): Pressure = Pressure(mmHg * HPA_PER_MMHG)
        fun fromInHg(inHg: Double): Pressure = Pressure(inHg * HPA_PER_INHG)
    }
}
